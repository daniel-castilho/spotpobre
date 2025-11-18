package com.spotpobre.backend.domain.artist.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Artist {

    private ArtistId id;
    private String name;

    public static Artist create(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artist name cannot be blank.");
        }
        return new Artist(ArtistId.generate(), name);
    }
}
