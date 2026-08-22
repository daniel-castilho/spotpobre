package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.LikeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DynamoDbLikeRepositoryAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DynamoDbTable<LikeDocument> likesTable;

    @Test
    void createIfAbsentPersistsLikeAndPreservesOriginalOnRepeat() {
        UserId userId = UserId.generate();
        UUID songId = UUID.randomUUID();
        Instant originalLikedAt = Instant.parse("2024-05-01T10:15:30Z");

        assertTrue(likeRepository.createIfAbsent(new Like(userId, songId.toString(), EntityType.SONG, originalLikedAt)));

        // Repeated PUT with a newer likedAt must not overwrite the original record.
        Instant newerLikedAt = originalLikedAt.plusSeconds(3600);
        assertFalse(likeRepository.createIfAbsent(new Like(userId, songId.toString(), EntityType.SONG, newerLikedAt)));

        LikeDocument stored = likesTable.getItem(keyFor(userId, songId));
        assertEquals(originalLikedAt, stored.getLikedAt());

        assertTrue(likeRepository.deleteIfPresent(userId, songId.toString(), EntityType.SONG));
        assertFalse(likeRepository.deleteIfPresent(userId, songId.toString(), EntityType.SONG));
    }

    @Test
    void deleteIfPresentOnlyRemovesTargetEntityLike() {
        UserId userId = UserId.generate();
        UUID likedSongId = UUID.randomUUID();
        UUID otherSongId = UUID.randomUUID();

        assertTrue(likeRepository.createIfAbsent(new Like(userId, likedSongId.toString(), EntityType.SONG, Instant.now())));
        assertTrue(likeRepository.createIfAbsent(new Like(userId, otherSongId.toString(), EntityType.SONG, Instant.now())));

        assertTrue(likeRepository.deleteIfPresent(userId, likedSongId.toString(), EntityType.SONG));

        assertEquals(null, likesTable.getItem(keyFor(userId, likedSongId)));
        assertEquals(otherSongId.toString(),
                likesTable.getItem(keyFor(userId, otherSongId)).getEntityCompositeKey().split("#")[1]);
    }

    private static Key keyFor(UserId userId, UUID entityId) {
        return Key.builder()
                .partitionValue(userId.value().toString())
                .sortValue(EntityType.SONG.name() + "#" + entityId)
                .build();
    }
}
