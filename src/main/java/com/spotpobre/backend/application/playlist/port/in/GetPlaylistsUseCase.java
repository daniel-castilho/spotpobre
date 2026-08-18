package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;

public interface GetPlaylistsUseCase {
    PageResult<Playlist> getPlaylists(final PageRequest pageRequest, final String exclusiveStartKey);
}
