package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DynamoDbConfig {

    @Value("${aws.dynamodb.endpoint}")
    private String dynamoDbEndpoint;
    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(awsRegion))
                .endpointOverride(URI.create(dynamoDbEndpoint))
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
        // Esta linha está correta porque PlaylistDocument é um @DynamoDbBean
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
                .addAttribute(UserProfileDocument.class, a -> a.name("profile")
                        .getter(UserDocument::getProfile)
                        .setter(UserDocument::setProfile)
                        .attributeConverter(new UserProfileDocumentConverter())) // Correto, pois é um POJO customizado
                .addAttribute(String.class, a -> a.name("profile.email") // Define GSI on profile.email directly
                        .getter(userDocument -> userDocument.getProfile() != null ? userDocument.getProfile().getEmail() : null)
                        .setter((userDocument, email) -> {
                            if (userDocument.getProfile() != null) userDocument.getProfile().setEmail(email);
                        })
                        .tags(StaticAttributeTags.secondaryPartitionKey("email-index"))) // GSI tag moved here
                .addAttribute(String.class, a -> a.name("password")
                        .getter(UserDocument::getPassword)
                        .setter(UserDocument::setPassword))

                // Esta é a correção da primeira etapa (de Set.class para EnhancedType.setOf)
                .addAttribute(EnhancedType.setOf(String.class), a -> a.name("roles")
                        .getter(UserDocument::getRoles)
                        .setter(UserDocument::setRoles))

                // --- ESTA É A NOVA CORREÇÃO ---
                // Precisamos dizer ao SDK como mapear o PlaylistDocument aninhado,
                // tratando-o como um documento e passando seu schema.
                .addAttribute(EnhancedType.listOf(
                        EnhancedType.documentOf(PlaylistDocument.class, TableSchema.fromBean(PlaylistDocument.class))
                ), a -> a.name("playlists")
                        .getter(UserDocument::getPlaylists)
                        .setter(UserDocument::setPlaylists))

                .build();
    }

    @Bean
    public DynamoDbTable<UserDocument> userTable(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        return enhancedClient.table("Users", userTableSchema);
    }

    @Bean
    public DynamoDbIndex<UserDocument> userEmailIndex(final DynamoDbEnhancedClient enhancedClient, final TableSchema<UserDocument> userTableSchema) {
        // The GSI is defined within the userTableSchema, so we just reference it by name.
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

    // Custom AttributeConverter for UserProfileDocument
    private static class UserProfileDocumentConverter implements AttributeConverter<UserProfileDocument> {

        @Override
        public AttributeValue transformFrom(UserProfileDocument input) {
            if (input == null) {
                return AttributeValue.builder().nul(true).build();
            }
            Map<String, AttributeValue> map = new HashMap<>();
            map.put("name", AttributeValue.builder().s(input.getName()).build());
            map.put("email", AttributeValue.builder().s(input.getEmail()).build()); // Removed GSI tag from here
            map.put("country", AttributeValue.builder().s(input.getCountry()).build());
            return AttributeValue.builder().m(map).build();
        }

        @Override
        public UserProfileDocument transformTo(AttributeValue input) {
            if (input.nul() != null && input.nul()) {
                return null;
            }
            Map<String, AttributeValue> map = input.m();
            UserProfileDocument profile = new UserProfileDocument();
            profile.setName(map.get("name").s());
            profile.setEmail(map.get("email").s());
            profile.setCountry(map.get("country").s());
            return profile;
        }

        @Override
        public EnhancedType<UserProfileDocument> type() {
            return EnhancedType.of(UserProfileDocument.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.M;
        }
    }
}