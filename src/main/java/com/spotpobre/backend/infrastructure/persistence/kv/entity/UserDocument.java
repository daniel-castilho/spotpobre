package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@DynamoDbBean
public class UserDocument {
    private String id;
    private UserProfileDocument profile;
    private String password;
    private Set<String> roles;
    private Instant emailVerifiedAt;
    private Instant passwordChangedAt;

    public UserDocument() {
    }

    private UserDocument(Builder builder) {
        this.id = builder.id;
        this.profile = builder.profile;
        this.password = builder.password;
        this.roles = builder.roles;
        this.emailVerifiedAt = builder.emailVerifiedAt;
        this.passwordChangedAt = builder.passwordChangedAt;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserProfileDocument getProfile() {
        return profile;
    }

    public void setProfile(UserProfileDocument profile) {
        this.profile = profile;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /** Null (or absent on legacy rows) means the e-mail is not verified. */
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDocument that = (UserDocument) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(profile, that.profile) &&
               Objects.equals(password, that.password) &&
               Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, profile, password, roles);
    }

    @Override
    public String toString() {
        return "UserDocument{" +
               "id='" + id + '\'' +
               ", profile=" + profile +
               ", password='" + (password != null ? "[PROTECTED]" : "null") + '\'' +
               ", roles=" + roles +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private UserProfileDocument profile;
        private String password;
        private Set<String> roles;
        private Instant emailVerifiedAt;
        private Instant passwordChangedAt;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder profile(UserProfileDocument profile) {
            this.profile = profile;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder passwordChangedAt(Instant passwordChangedAt) {
            this.passwordChangedAt = passwordChangedAt;
            return this;
        }

        public Builder emailVerifiedAt(Instant emailVerifiedAt) {
            this.emailVerifiedAt = emailVerifiedAt;
            return this;
        }

        public UserDocument build() {
            return new UserDocument(this);
        }
    }
}
