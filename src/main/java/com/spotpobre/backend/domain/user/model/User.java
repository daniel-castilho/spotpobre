package com.spotpobre.backend.domain.user.model;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class User {

    private UserId id;
    private UserProfile profile;
    private String password; // Can be null for OAuth2 users
    private Set<Role> roles;
    private List<Playlist> playlists;

    public static User createWithLocalPassword(final UserProfile profile, final String password) {
        if (profile == null) {
            throw new IllegalArgumentException("User profile cannot be null.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank for local registration.");
        }
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User(UserId.generate(), profile, password, defaultRoles, new ArrayList<>());
    }

    public static User createFromExternalProvider(final UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("User profile cannot be null.");
        }
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User(UserId.generate(), profile, null, defaultRoles, new ArrayList<>());
    }

    public Playlist createPlaylist(final String name) {
        if (playlists.size() >= 10) {
            throw new IllegalStateException("User cannot have more than 10 playlists.");
        }
        final Playlist playlist = Playlist.create(name, this.id);
        this.playlists.add(playlist);
        return playlist;
    }

    public void updateProfile(final UserProfile newProfile) {
        this.profile = newProfile;
    }

    public void grantRole(final Role role) {
        this.roles.add(role);
    }

    public void revokeRole(final Role role) {
        this.roles.remove(role);
    }
}
