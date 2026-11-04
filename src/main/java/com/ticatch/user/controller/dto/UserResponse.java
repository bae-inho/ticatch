package com.ticatch.user.controller.dto;

import com.ticatch.user.domain.UserRole;
import com.ticatch.user.service.dto.UserResult;

public record UserResponse(Long userId, String email, String name, UserRole role) {

    public static UserResponse from(UserResult user) {
        return new UserResponse(user.id(), user.email(), user.name(), user.role());
    }
}
