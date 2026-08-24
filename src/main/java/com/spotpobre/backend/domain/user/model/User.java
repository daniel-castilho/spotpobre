package com.spotpobre.backend.domain.user.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class User {

    public static final int MAX_PLAYLISTS_PER_USER = 10;

    private UserId id;
    private UserProfile profile;
    private String password; // Can be null for OAuth2 users
    private Set<Role> roles;
    private Instant emailVerifiedAt; // Null (or absent on legacy rows) = not verified
    // Revocation baseline: JWTs issued BEFORE this instant are rejected on the authenticated
    // path (password-reset invalidation, spec S22 / defect #13). Null = never changed.
    private Instant passwordChangedAt;

    private User(final Builder builder) {
        this.id = builder.id;
        this.profile = builder.profile;
        this.password = builder.password;
        this.roles = builder.roles;
        this.emailVerifiedAt = builder.emailVerifiedAt;
        this.passwordChangedAt = builder.passwordChangedAt;
    }

    public static User createWithLocalPassword(final UserProfile profile, final String password) {
        return createWithLocalPassword(UserId.generate(), profile, password);
    }

    /**
     * Creates a local-registration user under a preassigned stable identifier, so a durable
     * idempotency claim can reserve the ID before the write and retries recover the same user.
     * New local accounts start with {@code emailVerifiedAt == null}.
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

    /** Null means the address is not verified (legacy rows without the attribute included). */
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void updateProfile(final UserProfile newProfile) {
        this.profile = newProfile;
    }

    /**
     * Replaces the local password with an already-hashed value (callers encode through the
     * {@code PasswordHasher} port before reaching the aggregate).
     */
    public void changePassword(final String encodedPassword, final java.time.Instant at) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Encoded password cannot be blank");
        }
        this.password = encodedPassword;
        this.passwordChangedAt = at;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    /**
     * Marks the e-mail as verified at the given instant. First verification wins: calling it
     * again on an already-verified account keeps the original timestamp.
     */
    public void markEmailVerified(final Instant verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalArgumentException("verifiedAt cannot be null");
        }
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = verifiedAt;
        }
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
                + ", emailVerifiedAt=" + (emailVerifiedAt != null ? emailVerifiedAt : "null")
                + '}';
    }

    public static final class Builder {
        private UserId id;
        private UserProfile profile;
        private String password;
        private Set<Role> roles;
        private Instant emailVerifiedAt;
        private Instant passwordChangedAt;

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

        public Builder passwordChangedAt(final Instant passwordChangedAt) {
            this.passwordChangedAt = passwordChangedAt;
            return this;
        }

        public Builder emailVerifiedAt(final Instant emailVerifiedAt) {
            this.emailVerifiedAt = emailVerifiedAt;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
