package com.spotpobre.backend;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractIntegrationTest {

    private static final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:3.2");
    private static final String BUCKET_NAME = "spotpobre-songs";

    // Single container shared by every *IT class for the whole JVM. It is started manually (not via
    // the @Container lifecycle) so it stays alive while Spring's cached ApplicationContext is reused
    // across test classes; otherwise Testcontainers would stop it after the first class and the
    // cached context would keep pointing at a dead port.
    private static final LocalStackContainer localstack = startLocalStack();

    // Dedicated pinned Redis for the whole JVM (same lifecycle rationale as LocalStack):
    // keeps the rate-limit authority and any Redis-backed behaviour deterministic and
    // isolated from whatever happens to listen on localhost:6379 (e.g. a dev compose stack).
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    static {
        redis.start();
    }

    /** Static accessors so non-subclassing ITs (e.g. RateLimitFlowIT) reuse the singleton. */
    public static String localstackEndpoint() {
        return localstack.getEndpoint().toString();
    }

    public static String localstackRegion() {
        return localstack.getRegion();
    }

    private static LocalStackContainer startLocalStack() {
        // Testcontainers 2.x: withServices takes service-name strings (the
        // Service enum was removed) and every LocalStack service shares the
        // single container endpoint, so getEndpoint() replaces
        // getEndpointOverride(service).
        LocalStackContainer container = new LocalStackContainer(localstackImage)
                .withServices("s3", "dynamodb", "ses");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // AWS Endpoints
        registry.add("aws.s3.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("aws.dynamodb.endpoint", () -> localstack.getEndpoint().toString());
        // SES shares the LocalStack edge; the adapter must hit the mapped port, not 4566.
        registry.add("email.sesEndpoint", () -> localstack.getEndpoint().toString());

        // Rate-limit authority + Redis-backed behaviour point at the dedicated container so
        // suites stay deterministic; the in-memory cache keeps @Cacheable lookups simple.
        // Capacities are generous: every class in the JVM shares one identity (127.0.0.1),
        // so the defaults would exhaust mid-suite and fail unrelated tests. The authority
        // itself stays real (atomic Lua buckets); dedicated RateLimit*ITs assert ceilings.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("rate-limit.key-secret", () -> "flow-it-rate-limit-secret");
        registry.add("rate-limit.register-ip-capacity", () -> "200");
        registry.add("rate-limit.register-email-capacity", () -> "50");
        registry.add("rate-limit.authenticate-ip-capacity", () -> "500");
        registry.add("rate-limit.authenticate-email-capacity", () -> "200");

        // AWS Credentials for LocalStack — used by DynamoDbConfig and S3Config which build
        // clients with StaticCredentialsProvider from AwsProperties.credentials().
        registry.add("aws.credentials.access-key", localstack::getAccessKey);
        registry.add("aws.credentials.secret-key", localstack::getSecretKey);

        // AWS Region
        registry.add("aws.region", localstack::getRegion);

        // No Redis in the Testcontainers stack: fall back to an in-memory cache for the
        // @Cacheable lookups (userCache) so auth flows do not depend on an external Redis.
        registry.add("spring.cache.type", () -> "simple");
    }

    /**
     * Mirrors the LocalStack setup block from README.md: creates the S3 bucket and every
     * DynamoDB table (with GSIs) the application expects, against the throwaway container.
     */
    @BeforeAll
    public static void provisionLocalStack() {
        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials())
                .region(Region.of(localstack.getRegion()))
                .build();

        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials())
                .region(Region.of(localstack.getRegion()))
                .build();

        createBucketIfMissing(s3);
        createTableIfMissing(dynamoDb, "Users", "id", null,
                gsi("email-index", "profile.email"));
        createTableIfMissing(dynamoDb, "UserEmails", "email", null);
        createTableIfMissing(dynamoDb, "Playlists", "id", null,
                gsi("ownerId-index", "ownerId"));
        createTableIfMissing(dynamoDb, "Songs", "id", null,
                gsi("title-search-index", "searchPartition", "searchTitle"),
                gsi("albumId-index", "albumId"));
        createTableIfMissing(dynamoDb, "Artists", "id", null,
                gsi("name-search-index", "searchPartition", "searchName"));
        createTableIfMissing(dynamoDb, "ArtistAccounts", "artistId", "userId");
        createTableIfMissing(dynamoDb, "Albums", "id", null,
                gsi("artistId-index", "artistId"));
        createTableIfMissing(dynamoDb, "Likes", "userId", "entityCompositeKey",
                gsi("entityId-index", "entityCompositeKey", "userId"));
        createTableIfMissing(dynamoDb, "IdempotencyRecords", "scopeKey", null);
        enableTimeToLive(dynamoDb, "IdempotencyRecords");
        createTableIfMissing(dynamoDb, "AccountTokens", "tokenHash", null,
                gsi("userId-index", "userId"));
        enableTimeToLive(dynamoDb, "AccountTokens");
        createSongUploadsTableIfMissing(dynamoDb);
    }

    /**
     * SongUploads uses a NUMERIC GSI range key (expiresAtEpochSeconds), so it cannot go
     * through the all-strings {@link #createTableIfMissing} helper.
     */
    private static void createSongUploadsTableIfMissing(DynamoDbClient dynamoDb) {
        if (dynamoDb.listTables().tableNames().contains("SongUploads")) {
            return;
        }
        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName("SongUploads")
                .keySchema(KeySchemaElement.builder().attributeName("songId").keyType(KeyType.HASH).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("songId").attributeType("S").build(),
                        AttributeDefinition.builder().attributeName("state").attributeType("S").build(),
                        AttributeDefinition.builder().attributeName("expiresAtEpochSeconds").attributeType("N").build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("state-expiry-index")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("state").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("expiresAtEpochSeconds").keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .billingMode("PAY_PER_REQUEST")
                .build());
        enableTimeToLive(dynamoDb, "SongUploads");
    }

    private static void enableTimeToLive(DynamoDbClient dynamoDb, String tableName) {
        dynamoDb.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                .tableName(tableName)
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .enabled(true)
                        .attributeName("expiresAtEpochSeconds")
                        .build())
                .build());
    }

    private static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
    }

    private static void createBucketIfMissing(S3Client s3) {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (Exception e) {
            // Bucket already exists (409 BucketAlreadyOwnedByYou) — fine.
        }
    }

    private static void createTableIfMissing(DynamoDbClient dynamoDb, String tableName, String partitionKey,
                                             String sortKey, GlobalSecondaryIndex... indexes) {
        boolean exists = dynamoDb.listTables().tableNames().contains(tableName);
        if (exists) {
            return;
        }

        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(KeySchemaElement.builder().attributeName(partitionKey).keyType(KeyType.HASH).build());
        if (sortKey != null) {
            keySchema.add(KeySchemaElement.builder().attributeName(sortKey).keyType(KeyType.RANGE).build());
        }

        Set<String> attributeNames = new LinkedHashSet<>();
        attributeNames.add(partitionKey);
        if (sortKey != null) {
            attributeNames.add(sortKey);
        }
        for (GlobalSecondaryIndex index : indexes) {
            for (KeySchemaElement key : index.keySchema()) {
                attributeNames.add(key.attributeName());
            }
        }

        List<AttributeDefinition> attributeDefinitions = attributeNames.stream()
                .map(name -> AttributeDefinition.builder().attributeName(name).attributeType("S").build())
                .toList();

        CreateTableRequest.Builder request = CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(keySchema)
                .attributeDefinitions(attributeDefinitions)
                .billingMode("PAY_PER_REQUEST");
        if (indexes.length > 0) {
            request.globalSecondaryIndexes(indexes);
        }

        dynamoDb.createTable(request.build());
    }

    private static GlobalSecondaryIndex gsi(String indexName, String hashKey) {
        return gsi(indexName, hashKey, null);
    }

    private static GlobalSecondaryIndex gsi(String indexName, String hashKey, String rangeKey) {
        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build());
        if (rangeKey != null) {
            keySchema.add(KeySchemaElement.builder().attributeName(rangeKey).keyType(KeyType.RANGE).build());
        }
        return GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .keySchema(keySchema)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}