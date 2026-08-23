package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AccountTokenDocument;import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistAccountDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.IdempotencyRecordDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.LikeDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserEmailDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class DynamoDbConfig {

    private final AwsProperties awsProperties;
    private final AwsCredentialsProviderResolver credentialsProviderResolver;

    public DynamoDbConfig(AwsProperties awsProperties, AwsCredentialsProviderResolver credentialsProviderResolver) {
        this.awsProperties = awsProperties;
        this.credentialsProviderResolver = credentialsProviderResolver;
    }

    private static final TableSchema<UserProfileDocument> USER_PROFILE_TABLE_SCHEMA =
            TableSchema.builder(UserProfileDocument.class)
                    .newItemSupplier(UserProfileDocument::new)
                    .addAttribute(String.class, a -> a.name("name").getter(UserProfileDocument::getName).setter(UserProfileDocument::setName))
                    .addAttribute(String.class, a -> a.name("email").getter(UserProfileDocument::getEmail).setter(UserProfileDocument::setEmail))
                    .addAttribute(String.class, a -> a.name("country").getter(UserProfileDocument::getCountry).setter(UserProfileDocument::setCountry))
                    .build();

    @Bean
    @DependsOn("prodConfigValidator")
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(credentialsProviderResolver.resolve())
                .region(Region.of(awsProperties.region()))
                .endpointOverride(URI.create(awsProperties.dynamodb().endpoint()))
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(final DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public TableSchema<SongDocument> songTableSchema() {
        return TableSchema.builder(SongDocument.class)
                .newItemSupplier(SongDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(SongDocument::getId).setter(SongDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("title").getter(SongDocument::getTitle).setter(SongDocument::setTitle))
                .addAttribute(String.class, a -> a.name("searchTitle").getter(SongDocument::getSearchTitle).setter(SongDocument::setSearchTitle).tags(StaticAttributeTags.secondarySortKey("title-search-index")))
                .addAttribute(String.class, a -> a.name("searchPartition").getter(doc -> "SONG").setter((doc, val) -> {}).tags(StaticAttributeTags.secondaryPartitionKey("title-search-index")))
                .addAttribute(String.class, a -> a.name("albumId").getter(song -> song.getAlbumId() != null ? song.getAlbumId().toString() : null).setter((song, albumId) -> song.setAlbumId(albumId != null ? UUID.fromString(albumId) : null)).tags(StaticAttributeTags.secondaryPartitionKey("albumId-index")))
                .addAttribute(String.class, a -> a.name("storageId").getter(SongDocument::getStorageId).setter(SongDocument::setStorageId))
                .build();
    }

    @Bean
    public DynamoDbTable<SongDocument> songTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<SongDocument> songTableSchema) {
        return enhancedClient.table("Songs", songTableSchema);
    }

    @Bean
    public TableSchema<AlbumDocument> albumTableSchema() {
        return TableSchema.builder(AlbumDocument.class)
                .newItemSupplier(AlbumDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(AlbumDocument::getId).setter(AlbumDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("name").getter(AlbumDocument::getName).setter(AlbumDocument::setName))
                .addAttribute(String.class, a -> a.name("artistId").getter(album -> album.getArtistId() != null ? album.getArtistId().toString() : null).setter((album, artistId) -> album.setArtistId(artistId != null ? UUID.fromString(artistId) : null)).tags(StaticAttributeTags.secondaryPartitionKey("artistId-index")))
                .addAttribute(String.class, a -> a.name("coverArtUrl").getter(AlbumDocument::getCoverArtUrl).setter(AlbumDocument::setCoverArtUrl))
                .build();
    }

    @Bean
    public DynamoDbTable<AlbumDocument> albumTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<AlbumDocument> albumTableSchema) {
        return enhancedClient.table("Albums", albumTableSchema);
    }

    @Bean
    public TableSchema<PlaylistDocument> playlistTableSchema(final TableSchema<SongDocument> songTableSchema) {
        return TableSchema.builder(PlaylistDocument.class)
                .newItemSupplier(PlaylistDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(PlaylistDocument::getId).setter(PlaylistDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("name").getter(PlaylistDocument::getName).setter(PlaylistDocument::setName))
                .addAttribute(String.class, a -> a.name("ownerId").getter(playlist -> playlist.getOwnerId() != null ? playlist.getOwnerId().toString() : null).setter((playlist, ownerId) -> playlist.setOwnerId(ownerId != null ? UUID.fromString(ownerId) : null)).tags(StaticAttributeTags.secondaryPartitionKey("ownerId-index")))
                .addAttribute(EnhancedType.listOf(EnhancedType.documentOf(SongDocument.class, songTableSchema)), a -> a.name("songs").getter(PlaylistDocument::getSongs).setter(PlaylistDocument::setSongs))
                .addAttribute(Long.class, a -> a.name("version").getter(PlaylistDocument::getVersion).setter(PlaylistDocument::setVersion))
                .build();
    }

    @Bean
    public DynamoDbTable<PlaylistDocument> playlistTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<PlaylistDocument> playlistTableSchema) {
        return enhancedClient.table("Playlists", playlistTableSchema);
    }

    @Bean
    public TableSchema<ArtistDocument> artistTableSchema() {
        return TableSchema.builder(ArtistDocument.class)
                .newItemSupplier(ArtistDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(ArtistDocument::getId).setter(ArtistDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("name").getter(ArtistDocument::getName).setter(ArtistDocument::setName))
                .addAttribute(String.class, a -> a.name("searchName").getter(ArtistDocument::getSearchName).setter(ArtistDocument::setSearchName).tags(StaticAttributeTags.secondarySortKey("name-search-index")))
                .addAttribute(String.class, a -> a.name("searchPartition").getter(doc -> "ARTIST").setter((doc, val) -> {}).tags(StaticAttributeTags.secondaryPartitionKey("name-search-index")))
                .build();
    }

    @Bean
    public DynamoDbTable<ArtistDocument> artistTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<ArtistDocument> artistTableSchema) {
        return enhancedClient.table("Artists", artistTableSchema);
    }

    @Bean
    public TableSchema<ArtistAccountDocument> artistAccountTableSchema() {
        return TableSchema.builder(ArtistAccountDocument.class)
                .newItemSupplier(ArtistAccountDocument::new)
                .addAttribute(String.class, a -> a.name("artistId").getter(ArtistAccountDocument::getArtistId).setter(ArtistAccountDocument::setArtistId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("userId").getter(ArtistAccountDocument::getUserId).setter(ArtistAccountDocument::setUserId).tags(StaticAttributeTags.primarySortKey()))
                .addAttribute(String.class, a -> a.name("permission").getter(ArtistAccountDocument::getPermission).setter(ArtistAccountDocument::setPermission))
                .addAttribute(Instant.class, a -> a.name("createdAt").getter(ArtistAccountDocument::getCreatedAt).setter(ArtistAccountDocument::setCreatedAt))
                .build();
    }

    @Bean
    public DynamoDbTable<ArtistAccountDocument> artistAccountTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<ArtistAccountDocument> artistAccountTableSchema) {
        return enhancedClient.table("ArtistAccounts", artistAccountTableSchema);
    }

    @Bean
    public TableSchema<IdempotencyRecordDocument> idempotencyRecordTableSchema() {
        return TableSchema.builder(IdempotencyRecordDocument.class)
                .newItemSupplier(IdempotencyRecordDocument::new)
                .addAttribute(String.class, a -> a.name("scopeKey").getter(IdempotencyRecordDocument::getScopeKey).setter(IdempotencyRecordDocument::setScopeKey).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("operationName").getter(IdempotencyRecordDocument::getOperationName).setter(IdempotencyRecordDocument::setOperationName))
                .addAttribute(String.class, a -> a.name("routeTemplate").getter(IdempotencyRecordDocument::getRouteTemplate).setter(IdempotencyRecordDocument::setRouteTemplate))
                .addAttribute(String.class, a -> a.name("actorScopeHash").getter(IdempotencyRecordDocument::getActorScopeHash).setter(IdempotencyRecordDocument::setActorScopeHash))
                .addAttribute(Integer.class, a -> a.name("hashVersion").getter(IdempotencyRecordDocument::getHashVersion).setter(IdempotencyRecordDocument::setHashVersion))
                .addAttribute(String.class, a -> a.name("requestHash").getter(IdempotencyRecordDocument::getRequestHash).setter(IdempotencyRecordDocument::setRequestHash))
                .addAttribute(String.class, a -> a.name("state").getter(IdempotencyRecordDocument::getState).setter(IdempotencyRecordDocument::setState))
                .addAttribute(String.class, a -> a.name("resourceType").getter(IdempotencyRecordDocument::getResourceType).setter(IdempotencyRecordDocument::setResourceType))
                .addAttribute(String.class, a -> a.name("resourceId").getter(IdempotencyRecordDocument::getResourceId).setter(IdempotencyRecordDocument::setResourceId))
                .addAttribute(String.class, a -> a.name("leaseTokenHash").getter(IdempotencyRecordDocument::getLeaseTokenHash).setter(IdempotencyRecordDocument::setLeaseTokenHash))
                .addAttribute(Instant.class, a -> a.name("leaseUntil").getter(IdempotencyRecordDocument::getLeaseUntil).setter(IdempotencyRecordDocument::setLeaseUntil))
                .addAttribute(String.class, a -> a.name("resultSnapshot").getter(IdempotencyRecordDocument::getResultSnapshot).setter(IdempotencyRecordDocument::setResultSnapshot))
                .addAttribute(Integer.class, a -> a.name("responseStatus").getter(IdempotencyRecordDocument::getResponseStatus).setter(IdempotencyRecordDocument::setResponseStatus))
                .addAttribute(String.class, a -> a.name("responseContentType").getter(IdempotencyRecordDocument::getResponseContentType).setter(IdempotencyRecordDocument::setResponseContentType))
                .addAttribute(String.class, a -> a.name("location").getter(IdempotencyRecordDocument::getLocation).setter(IdempotencyRecordDocument::setLocation))
                .addAttribute(Integer.class, a -> a.name("failureStatus").getter(IdempotencyRecordDocument::getFailureStatus).setter(IdempotencyRecordDocument::setFailureStatus))
                .addAttribute(String.class, a -> a.name("failureType").getter(IdempotencyRecordDocument::getFailureType).setter(IdempotencyRecordDocument::setFailureType))
                .addAttribute(String.class, a -> a.name("failureMessage").getter(IdempotencyRecordDocument::getFailureMessage).setter(IdempotencyRecordDocument::setFailureMessage))
                .addAttribute(Instant.class, a -> a.name("createdAt").getter(IdempotencyRecordDocument::getCreatedAt).setter(IdempotencyRecordDocument::setCreatedAt))
                .addAttribute(Instant.class, a -> a.name("updatedAt").getter(IdempotencyRecordDocument::getUpdatedAt).setter(IdempotencyRecordDocument::setUpdatedAt))
                .addAttribute(Instant.class, a -> a.name("completedAt").getter(IdempotencyRecordDocument::getCompletedAt).setter(IdempotencyRecordDocument::setCompletedAt))
                .addAttribute(Long.class, a -> a.name("expiresAtEpochSeconds").getter(IdempotencyRecordDocument::getExpiresAtEpochSeconds).setter(IdempotencyRecordDocument::setExpiresAtEpochSeconds))
                .build();
    }

    @Bean
    public DynamoDbTable<IdempotencyRecordDocument> idempotencyRecordTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<IdempotencyRecordDocument> idempotencyRecordTableSchema) {
        return enhancedClient.table("IdempotencyRecords", idempotencyRecordTableSchema);
    }

    @Bean
    public TableSchema<UserDocument> userTableSchema() {
        return TableSchema.builder(UserDocument.class)
                .newItemSupplier(UserDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(UserDocument::getId).setter(UserDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(EnhancedType.documentOf(UserProfileDocument.class, USER_PROFILE_TABLE_SCHEMA), a -> a.name("profile").getter(UserDocument::getProfile).setter(UserDocument::setProfile))
                .addAttribute(String.class, a -> a.name("password").getter(UserDocument::getPassword).setter(UserDocument::setPassword))
                .addAttribute(EnhancedType.setOf(String.class), a -> a.name("roles").getter(UserDocument::getRoles).setter(UserDocument::setRoles))
                .addAttribute(String.class, a -> a.name("profile.email").getter(userDoc -> userDoc.getProfile() != null ? userDoc.getProfile().getEmail() : null).setter((userDoc, email) -> { if (userDoc.getProfile() != null) { userDoc.getProfile().setEmail(email); } }).tags(StaticAttributeTags.secondaryPartitionKey("email-index")))
                .addAttribute(Instant.class, a -> a.name("emailVerifiedAt").getter(UserDocument::getEmailVerifiedAt).setter(UserDocument::setEmailVerifiedAt))
                .build();
    }

    @Bean
    public DynamoDbTable<UserDocument> userTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema);
    }

    @Bean
    public DynamoDbTable<UserEmailDocument> userEmailTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("UserEmails", TableSchema.fromBean(UserEmailDocument.class));
    }

    @Bean
    public DynamoDbIndex<UserDocument> userEmailIndex(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema).index("email-index");
    }

    @Bean
    public DynamoDbTable<LikeDocument> likesTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("Likes", TableSchema.fromBean(LikeDocument.class));
    }

    @Bean
    public TableSchema<AccountTokenDocument> accountTokenTableSchema() {
        return TableSchema.builder(AccountTokenDocument.class)
                .newItemSupplier(AccountTokenDocument::new)
                .addAttribute(String.class, a -> a.name("tokenHash").getter(AccountTokenDocument::getTokenHash).setter(AccountTokenDocument::setTokenHash).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("userId").getter(AccountTokenDocument::getUserId).setter(AccountTokenDocument::setUserId))
                .addAttribute(String.class, a -> a.name("purpose").getter(AccountTokenDocument::getPurpose).setter(AccountTokenDocument::setPurpose))
                .addAttribute(Long.class, a -> a.name("expiresAtEpochSeconds").getter(AccountTokenDocument::getExpiresAtEpochSeconds).setter(AccountTokenDocument::setExpiresAtEpochSeconds))
                .addAttribute(Long.class, a -> a.name("usedAtEpochSeconds").getter(AccountTokenDocument::getUsedAtEpochSeconds).setter(AccountTokenDocument::setUsedAtEpochSeconds))
                .addAttribute(Instant.class, a -> a.name("createdAt").getter(AccountTokenDocument::getCreatedAt).setter(AccountTokenDocument::setCreatedAt))
                .build();
    }

    @Bean
    public DynamoDbTable<AccountTokenDocument> accountTokenTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<AccountTokenDocument> accountTokenTableSchema) {
        return enhancedClient.table("AccountTokens", accountTokenTableSchema);
    }
}
