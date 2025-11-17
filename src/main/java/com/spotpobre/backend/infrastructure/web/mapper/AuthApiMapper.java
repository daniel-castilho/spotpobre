package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthApiMapper {
    RegisterUserUseCase.RegisterUserCommand toCommand(final RegisterRequest request);
    AuthenticateUserUseCase.AuthenticationCommand toCommand(final AuthenticationRequest request);
}
