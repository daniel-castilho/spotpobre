package com.spotpobre.backend.domain.user.model;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Add Setter

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter // Add Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor // Public no-argument constructor for frameworks
public class User {

    private UserId id;
    private UserProfile profile;
    private String password; // Can be null for OAuth2 users
    private Set<Role> roles;
    private List<Playlist> playlists;

    /**
     * Creates a new user with a local password (e.g., from a registration form).
     */
    public static User createWithLocalPassword(final UserProfile profile, final String password) {
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User(UserId.generate(), profile, password, defaultRoles, new ArrayList<Playlist>());
    }

    /**
     * Creates a new user from an external provider (e.g., OAuth2).
     * No local password is set.
     */
    public static User createFromExternalProvider(final UserProfile profile) {
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User(UserId.generate(), profile, null, defaultRoles, new ArrayList<Playlist>());
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
