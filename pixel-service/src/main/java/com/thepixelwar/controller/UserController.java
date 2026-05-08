package com.thepixelwar.controller;

import com.thepixelwar.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.thepixelwar.dto.UserResponse;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me") // 사용자가 GET 방식으로 /api/user/me에 접속하면 실행
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
        // 로그인 실패 -> principal이 비어있음
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new UserResponse(principal.getNickname()));
    }
}