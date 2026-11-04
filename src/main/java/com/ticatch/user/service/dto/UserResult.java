package com.ticatch.user.service.dto;

import com.ticatch.user.domain.User;
import com.ticatch.user.domain.UserRole;

public record UserResult(Long id, String email, String name, UserRole role) {

    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
