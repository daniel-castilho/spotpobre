package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import org.springframework.data.domain.Pageable;

public interface GetPlaylistsByOwnerUseCase {
    DynamoDbPage<Playlist> getPlaylistsByOwner(final UserId ownerId, final Pageable pageable, final String exclusiveStartKey);
}
