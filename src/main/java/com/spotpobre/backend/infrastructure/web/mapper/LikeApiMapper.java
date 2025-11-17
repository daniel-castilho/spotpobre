package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.web.dto.request.ToggleLikeRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.LikeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LikeApiMapper {

    @Mapping(target = "userId", source = "ownerId")
    ToggleLikeUseCase.ToggleLikeCommand toCommand(ToggleLikeRequest request, UserId ownerId);

    LikeResponse toResponse(ToggleLikeUseCase.LikeResult result);
}
