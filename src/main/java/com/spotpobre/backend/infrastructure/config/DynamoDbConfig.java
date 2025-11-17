package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Configuration
public class DynamoDbConfig {

    private final AwsProperties awsProperties;

    public DynamoDbConfig(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    private static final TableSchema<UserProfileDocument> USER_PROFILE_TABLE_SCHEMA =
            TableSchema.builder(UserProfileDocument.class)
                    .newItemSupplier(UserProfileDocument::new)
                    .addAttribute(String.class, a -> a.name("name").getter(UserProfileDocument::getName).setter(UserProfileDocument::setName))
                    .addAttribute(String.class, a -> a.name("email").getter(UserProfileDocument::getEmail).setter(UserProfileDocument::setEmail))
                    .addAttribute(String.class, a -> a.name("country").getter(UserProfileDocument::getCountry).setter(UserProfileDocument::setCountry))
                    .build();

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
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
                .addAttribute(String.class, a -> a.name("title").getter(SongDocument::getTitle).setter(SongDocument::setTitle).tags(StaticAttributeTags.secondarySortKey("title-search-index")))
                .addAttribute(String.class, a -> a.name("searchPartition").getter(doc -> "SONG").setter((doc, val) -> {}).tags(StaticAttributeTags.secondaryPartitionKey("title-search-index")))
                .addAttribute(String.class, a -> a.name("artistId").getter(song -> song.getArtistId() != null ? song.getArtistId().toString() : null).setter((song, artistId) -> song.setArtistId(artistId != null ? java.util.UUID.fromString(artistId) : null)))
                .addAttribute(String.class, a -> a.name("storageId").getter(SongDocument::getStorageId).setter(SongDocument::setStorageId))
                .build();
    }

    @Bean
    public DynamoDbTable<SongDocument> songTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<SongDocument> songTableSchema) {
        return enhancedClient.table("Songs", songTableSchema);
    }

    @Bean
    public TableSchema<PlaylistDocument> playlistTableSchema(final TableSchema<SongDocument> songTableSchema) {
        return TableSchema.builder(PlaylistDocument.class)
                .newItemSupplier(PlaylistDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(PlaylistDocument::getId).setter(PlaylistDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("name").getter(PlaylistDocument::getName).setter(PlaylistDocument::setName))
                .addAttribute(String.class, a -> a.name("ownerId").getter(playlist -> playlist.getOwnerId() != null ? playlist.getOwnerId().toString() : null).setter((playlist, ownerId) -> playlist.setOwnerId(ownerId != null ? java.util.UUID.fromString(ownerId) : null)).tags(StaticAttributeTags.secondaryPartitionKey("ownerId-index")))
                .addAttribute(EnhancedType.listOf(EnhancedType.documentOf(SongDocument.class, songTableSchema)), a -> a.name("songs").getter(PlaylistDocument::getSongs).setter(PlaylistDocument::setSongs))
                .build();
    }

    @Bean
    public DynamoDbTable<PlaylistDocument> playlistTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<PlaylistDocument> playlistTableSchema) {
        return enhancedClient.table("Playlists", playlistTableSchema);
    }

    @Bean
    public TableSchema<ArtistDocument> artistTableSchema(final TableSchema<SongDocument> songTableSchema) {
        return TableSchema.builder(ArtistDocument.class)
                .newItemSupplier(ArtistDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(ArtistDocument::getId).setter(ArtistDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("name").getter(ArtistDocument::getName).setter(ArtistDocument::setName).tags(StaticAttributeTags.secondarySortKey("name-search-index")))
                .addAttribute(String.class, a -> a.name("searchPartition").getter(doc -> "ARTIST").setter((doc, val) -> {}).tags(StaticAttributeTags.secondaryPartitionKey("name-search-index")))
                .addAttribute(EnhancedType.listOf(EnhancedType.documentOf(SongDocument.class, songTableSchema)), a -> a.name("songs").getter(ArtistDocument::getSongs).setter(ArtistDocument::setSongs))
                .build();
    }

    @Bean
    public DynamoDbTable<ArtistDocument> artistTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<ArtistDocument> artistTableSchema) {
        return enhancedClient.table("Artists", artistTableSchema);
    }

    @Bean
    public TableSchema<UserDocument> userTableSchema(final TableSchema<PlaylistDocument> playlistTableSchema) {
        return TableSchema.builder(UserDocument.class)
                .newItemSupplier(UserDocument::new)
                .addAttribute(String.class, a -> a.name("id").getter(UserDocument::getId).setter(UserDocument::setId).tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(EnhancedType.documentOf(UserProfileDocument.class, USER_PROFILE_TABLE_SCHEMA), a -> a.name("profile").getter(UserDocument::getProfile).setter(UserDocument::setProfile))
                .addAttribute(String.class, a -> a.name("password").getter(UserDocument::getPassword).setter(UserDocument::setPassword))
                .addAttribute(EnhancedType.setOf(String.class), a -> a.name("roles").getter(UserDocument::getRoles).setter(UserDocument::setRoles))
                .addAttribute(EnhancedType.listOf(EnhancedType.documentOf(PlaylistDocument.class, playlistTableSchema)), a -> a.name("playlists").getter(UserDocument::getPlaylists).setter(UserDocument::setPlaylists))
                .addAttribute(String.class, a -> a.name("profile.email").getter(userDoc -> userDoc.getProfile() != null ? userDoc.getProfile().getEmail() : null).setter((userDoc, email) -> { if (userDoc.getProfile() != null) { userDoc.getProfile().setEmail(email); } }).tags(StaticAttributeTags.secondaryPartitionKey("email-index")))
                .build();
    }

    @Bean
    public DynamoDbTable<UserDocument> userTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema);
    }

    @Bean
    public DynamoDbIndex<UserDocument> userEmailIndex(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema).index("email-index");
    }

    @Bean
    public DynamoDbTable<LikeDocument> likesTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("Likes", TableSchema.fromBean(LikeDocument.class));
    }
}
