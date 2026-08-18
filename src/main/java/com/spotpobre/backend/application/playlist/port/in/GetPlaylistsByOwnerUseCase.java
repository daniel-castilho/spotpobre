package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;

public interface GetPlaylistsByOwnerUseCase {
    PageResult<Playlist> getPlaylistsByOwner(final UserId ownerId, final PageRequest pageRequest, final String exclusiveStartKey);
}
