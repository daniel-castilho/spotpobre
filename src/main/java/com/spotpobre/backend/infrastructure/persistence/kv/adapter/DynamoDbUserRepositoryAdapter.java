package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UserPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbUserRepositoryAdapter implements UserRepository {

    private final DynamoDbUserRepository dynamoDbUserRepository;
    private final UserPersistenceMapper mapper;

    @Override
    public Optional<User> findById(final UserId id) {
        return dynamoDbUserRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByProfileEmail(final String email) {
        return dynamoDbUserRepository.findByProfileEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public void save(final User user) {
        final UserDocument document = mapper.toDocument(user);
        dynamoDbUserRepository.save(document);
    }
}
