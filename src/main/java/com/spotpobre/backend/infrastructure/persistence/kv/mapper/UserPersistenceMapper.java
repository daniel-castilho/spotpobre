package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserPersistenceMapper {

    private final PlaylistPersistenceMapper playlistPersistenceMapper;

    public UserPersistenceMapper(PlaylistPersistenceMapper playlistPersistenceMapper) {
        this.playlistPersistenceMapper = playlistPersistenceMapper;
    }

    public UserDocument toDocument(final User user) {
        if (user == null) {
            return null;
        }

        return UserDocument.builder()
                .id(user.getId() != null ? user.getId().value().toString() : null)
                .profile(toDocument(user.getProfile()))
                .password(user.getPassword())
                .roles(user.getRoles() != null ? 
                    user.getRoles().stream()
                        .map(Role::name)
                        .collect(Collectors.toSet()) : 
                    Collections.emptySet())
                .playlists(user.getPlaylists() != null ?
                        playlistPersistenceMapper.toDocumentList(user.getPlaylists()) :
                        Collections.emptyList())
                .build();
    }

    public User toDomain(final UserDocument document) {
        if (document == null) {
            return null;
        }

        return User.builder()
                .id(document.getId() != null ? UserId.from(document.getId()) : null)
                .profile(toDomain(document.getProfile()))
                .password(document.getPassword())
                .roles(document.getRoles() != null ? 
                    document.getRoles().stream()
                            .map(Role::valueOf)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class))) :
                    EnumSet.noneOf(Role.class))
                .playlists(document.getPlaylists() != null ?
                        playlistPersistenceMapper.toDomainList(document.getPlaylists()) :
                        Collections.emptyList())
                .build();
    }

    public UserProfileDocument toDocument(UserProfile userProfile) {
        if (userProfile == null) {
            return UserProfileDocument.builder().build();
        }

        return UserProfileDocument.builder()
                .name(userProfile.name())
                .email(userProfile.email())
                .country(userProfile.country())
                .build();
    }

    public UserProfile toDomain(UserProfileDocument userProfileDocument) {
        if (userProfileDocument == null) {
            return new UserProfile(null, null, null);
        }

        return new UserProfile(
            userProfileDocument.getName(),
            userProfileDocument.getEmail(),
            userProfileDocument.getCountry()
        );
    }

    public List<UserDocument> toDocumentList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        return users.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
    }

    public List<User> toDomainList(List<UserDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // Using the domain Role enum from com.spotpobre.backend.domain.user.model.Role
}
