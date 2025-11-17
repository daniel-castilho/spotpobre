package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
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

    // Define the schema for the nested UserProfileDocument
    private static final TableSchema<UserProfileDocument> USER_PROFILE_TABLE_SCHEMA =
            TableSchema.builder(UserProfileDocument.class)
                    .newItemSupplier(UserProfileDocument::new)
                    .addAttribute(String.class, a -> a.name("name")

                            .getter(UserProfileDocument::getName)
                            .setter(UserProfileDocument::setName))
                    .addAttribute(String.class, a -> a.name("email")
                            .getter(UserProfileDocument::getEmail)

                            .setter(UserProfileDocument::setEmail)) // <-- A TAG DO GSI FOI REMOVIDA DAQUI
                    .addAttribute(String.class, a -> a.name("country")
                            .getter(UserProfileDocument::getCountry)

                            .setter(UserProfileDocument::setCountry))
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
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<PlaylistDocument> playlistTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("Playlists", TableSchema.fromBean(PlaylistDocument.class));
    }

    // Define TableSchema for UserDocument programmatically to include GSI
    @Bean
    public TableSchema<UserDocument> userTableSchema() {
        return TableSchema.builder(UserDocument.class)
                .newItemSupplier(UserDocument::new)
                .addAttribute(String.class, a -> a.name("id")
                        .getter(UserDocument::getId)

                        .setter(UserDocument::setId)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(EnhancedType.documentOf(UserProfileDocument.class, USER_PROFILE_TABLE_SCHEMA),
                        a -> a.name("profile")

                                .getter(UserDocument::getProfile)
                                .setter(UserDocument::setProfile))
                .addAttribute(String.class, a -> a.name("password")
                        .getter(UserDocument::getPassword)

                        .setter(UserDocument::setPassword))
                .addAttribute(EnhancedType.setOf(String.class), a -> a.name("roles")
                        .getter(UserDocument::getRoles)
                        .setter(UserDocument::setRoles))
                .addAttribute(EnhancedType.listOf(

                        EnhancedType.documentOf(PlaylistDocument.class, TableSchema.fromBean(PlaylistDocument.class))
                ), a -> a.name("playlists")
                        .getter(UserDocument::getPlaylists)
                        .setter(UserDocument::setPlaylists))

                // --- CORREÇÃO: A TAG DO GSI FOI MOVIDA PARA CÁ ---
                // Definimos um atributo "virtual" para o GSI que aponta para o campo aninhado
                .addAttribute(String.class, a -> a.name("profile.email")
                        .getter(userDoc -> userDoc.getProfile()!= null? userDoc.getProfile().getEmail() : null)
                        .setter((userDoc, email) -> {
                            if (userDoc.getProfile()!= null) {
                                userDoc.getProfile().setEmail(email);
                            }
                        })
                        .tags(StaticAttributeTags.secondaryPartitionKey("email-index")))

                .build();
    }

    @Bean
    public DynamoDbTable<UserDocument> userTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema);
    }

    @Bean
    public DynamoDbIndex<UserDocument> userEmailIndex(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        // Agora esta chamada vai funcionar, pois o "email-index" está definido no userTableSchema
        return enhancedClient.table("Users", userTableSchema).index("email-index");
    }

    @Bean
    public DynamoDbTable<ArtistDocument> artistTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("Artists", TableSchema.fromBean(ArtistDocument.class));
    }

    @Bean
    public DynamoDbTable<SongDocument> songTable(final DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("Songs", TableSchema.fromBean(SongDocument.class));
    }
}