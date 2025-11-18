package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DynamoDbAlbumRepositoryImpl implements DynamoDbAlbumRepository {

    private final DynamoDbTable<AlbumDocument> albumTable;

    @Override
    public void save(AlbumDocument albumDocument) {
        albumTable.putItem(albumDocument);
    }

    @Override
    public Optional<AlbumDocument> findById(UUID albumId) {
        return Optional.ofNullable(albumTable.getItem(Key.builder().partitionValue(albumId.toString()).build()));
    }

    @Override
    public List<AlbumDocument> findByArtistId(UUID artistId) {
        DynamoDbIndex<AlbumDocument> index = albumTable.index("artistId-index");
        return index.query(QueryConditional.keyEqualTo(k -> k.partitionValue(artistId.toString())))
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}
