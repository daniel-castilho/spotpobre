package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface UserApiMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "userIdToUuid")
    @Mapping(source = "profile.name", target = "name")
    @Mapping(source = "profile.email", target = "email")
    @Mapping(source = "profile.country", target = "country")
    @Mapping(source = "roles", target = "roles")
    UserProfileResponse toResponse(final User user);
}
