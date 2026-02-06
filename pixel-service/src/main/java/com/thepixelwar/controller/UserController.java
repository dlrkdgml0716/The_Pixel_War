package com.thepixelwar.controller;

import com.thepixelwar.dto.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.thepixelwar.dto.UserResponse;

@Slf4j // Lombok 라이브러리에서 제공하는 어노테이션. 로그를 남기기 위해 사용
@RestController // 해당 클래스가 JSON 데이터를 주고 받는 API 컨트롤러임을 명시. 응답이 자동으로 JSON 형태로 변환되어 날아감
@RequestMapping("/api/user") // 해당 컨트롤러의 기본 주소를 지정
public class UserController {

    @GetMapping("/me") // 사용자가 GET 방식으로 /api/user/me에 접속하면 실행
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
        // 로그인 실패 -> principal이 비어있음
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String nickname = principal.getNickname();

        log.info("로그인한 사용자 닉네임: {}", nickname);

        return ResponseEntity.ok(new UserResponse(nickname));
    }
}