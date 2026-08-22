package com.spotpobre.backend;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
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

    private static LocalStackContainer startLocalStack() {
        LocalStackContainer container = new LocalStackContainer(localstackImage)
                .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.DYNAMODB);
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // AWS Endpoints
        registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.dynamodb.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());

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
    static void provisionLocalStack() {
        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .credentialsProvider(credentials())
                .region(Region.of(localstack.getRegion()))
                .build();

        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
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