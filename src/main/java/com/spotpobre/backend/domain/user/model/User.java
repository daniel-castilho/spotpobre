package com.spotpobre.backend.domain.user.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class User {

    public static final int MAX_PLAYLISTS_PER_USER = 10;

    private UserId id;
    private UserProfile profile;
    private String password; // Can be null for OAuth2 users
    private Set<Role> roles;

    private User(final Builder builder) {
        this.id = builder.id;
        this.profile = builder.profile;
        this.password = builder.password;
        this.roles = builder.roles;
    }

    public static User createWithLocalPassword(final UserProfile profile, final String password) {
        return createWithLocalPassword(UserId.generate(), profile, password);
    }

    /**
     * Creates a local-registration user under a preassigned stable identifier, so a durable
     * idempotency claim can reserve the ID before the write and retries recover the same user.
     */
    public static User createWithLocalPassword(final UserId userId, final UserProfile profile,
                                               final String password) {
        if (userId == null) {
            throw new IllegalArgumentException("User id cannot be null.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("User profile cannot be null.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank for local registration.");
        }
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User.Builder()
                .id(userId)
                .profile(profile)
                .password(password)
                .roles(defaultRoles)
                .build();
    }

    public static User createFromExternalProvider(final UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("User profile cannot be null.");
        }
        final Set<Role> defaultRoles = EnumSet.of(Role.USER);
        return new User.Builder()
                .id(UserId.generate())
                .profile(profile)
                .password(null)
                .roles(defaultRoles)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UserId getId() {
        return id;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public String getPassword() {
        return password;
    }

    public Set<Role> getRoles() {
        return roles;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(profile, other.profile)
                && Objects.equals(password, other.password)
                && Objects.equals(roles, other.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, profile, password, roles);
    }

    @Override
    public String toString() {
        return "User{"
                + "id=" + id
                + ", profile=" + profile
                + ", password=" + (password != null ? "[PROTECTED]" : "null")
                + ", roles=" + roles
                + '}';
    }

    public static final class Builder {
        private UserId id;
        private UserProfile profile;
        private String password;
        private Set<Role> roles;

        private Builder() {
        }

        public Builder id(final UserId id) {
            this.id = id;
            return this;
        }

        public Builder profile(final UserProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        public Builder roles(final Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
