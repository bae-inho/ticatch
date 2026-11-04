package com.ticatch.user.service.dto;

public record SignUpCommand(String email, String password, String name) {
}
