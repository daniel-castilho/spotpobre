package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        String nextPageToken // Token for the next page, null if it's the last page
) {
}
