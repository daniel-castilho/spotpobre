package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey; // Keep import for now, will remove if not needed

import java.util.Objects;

// Removed @DynamoDbBean
public class UserProfileDocument {
    private String name;
    private String email;
    private String country;

    public UserProfileDocument() {
    }

    private UserProfileDocument(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.country = builder.country;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Removed @DynamoDbSecondaryPartitionKey from here
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserProfileDocument that = (UserProfileDocument) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(email, that.email) &&
               Objects.equals(country, that.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, country);
    }

    @Override
    public String toString() {
        return "UserProfileDocument{" +
               "name='" + name + '\'' +
               ", email='" + email + '\'' +
               ", country='" + country + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String email;
        private String country;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public UserProfileDocument build() {
            return new UserProfileDocument(this);
        }
    }
}
