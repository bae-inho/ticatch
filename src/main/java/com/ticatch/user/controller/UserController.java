package com.ticatch.user.controller;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.exception.CommonErrorCode;
import com.ticatch.user.controller.dto.SignUpRequest;
import com.ticatch.user.controller.dto.UserResponse;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.UserResult;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/users")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        UserResult user = userService.signUp(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/me")).body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        // 토큰은 유효하지만 회원이 활성 상태가 아니면 로그인이 풀린 것으로 본다.
        UserResult user = userService.findActiveUser(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return UserResponse.from(user);
    }
}
