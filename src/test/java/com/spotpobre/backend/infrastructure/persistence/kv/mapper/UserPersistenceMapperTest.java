package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPersistenceMapperTest {

    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    @Test
    void toDocument_null_mapsToNull() {
        assertNull(mapper.toDocument((User) null));
    }

    @Test
    void toDomain_null_mapsToNull() {
        assertNull(mapper.toDomain((UserDocument) null));
    }

    @Test
    void roundTrip_preservesEveryField_includingVerificationStamp() {
        var user = User.builder()
                .id(com.spotpobre.backend.domain.user.model.UserId.from(
                        "11111111-2222-3333-4444-555555555555"))
                .profile(new UserProfile("Ada", "ada@example.com", "BR"))
                .password("encoded-pw")
                .roles(EnumSet.of(Role.USER, Role.ARTIST))
                .emailVerifiedAt(Instant.parse("2026-08-23T10:00:00Z"))
                .build();

        UserDocument doc = mapper.toDocument(user);
        assertEquals(user.getId().value().toString(), doc.getId());
        assertTrue(doc.getRoles().contains("ARTIST"));
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), doc.getEmailVerifiedAt());

        User back = mapper.toDomain(doc);
        assertEquals(user.getProfile(), back.getProfile());
        assertEquals(user.getPassword(), back.getPassword());
        assertEquals(user.getEmailVerifiedAt(), back.getEmailVerifiedAt());
        assertEquals(user.getRoles(), back.getRoles());
    }

    @Test
    void roundTrip_legacyRowWithoutVerificationAttribute_staysUnverified() {
        UserDocument doc = UserDocument.builder()
                .id(java.util.UUID.randomUUID().toString())
                .profile(UserPersistenceMapperDummy.profile())
                .roles(java.util.Collections.emptySet())
                .build(); // emailVerifiedAt absent -> null

        User back = mapper.toDomain(doc);
        assertNull(back.getEmailVerifiedAt());
        assertNull(back.getPassword());

        UserDocument reSaved = mapper.toDocument(back);
        assertNull(reSaved.getEmailVerifiedAt(), "unverified must stay unverified (never silently marked)");
    }
}

/** Tiny helper so the dummy-profile construction stays readable. */
final class UserPersistenceMapperDummy {
    private UserPersistenceMapperDummy() {
    }

    static com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument profile() {
        return com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument.builder()
                .name("Legacy").email("legacy@example.com").country("BR")
                .build();
    }
}
