package com.thepixelwar.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        // 1. 로그인이 안 된 경우
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> attributes = principal.getAttributes();
        String nickname = "User"; // 못 찾았을 때 기본값

        try {
            // 2. 카카오 데이터 구조 뜯어보기 (kakao_account -> profile -> nickname)
            if (attributes.containsKey("kakao_account")) {
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                if (kakaoAccount != null && kakaoAccount.containsKey("profile")) {
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    if (profile != null && profile.containsKey("nickname")) {
                        nickname = (String) profile.get("nickname");
                    }
                }
            }
            // 3. 예비용 (properties에 들어있는 경우도 있음)
            else if (attributes.containsKey("properties")) {
                Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
                if (properties != null && properties.containsKey("nickname")) {
                    nickname = (String) properties.get("nickname");
                }
            }
        } catch (Exception e) {
            log.error("닉네임 파싱 실패", e);
        }

        log.info("로그인한 사용자 닉네임: {}", nickname);

        // 4. 프론트엔드가 바로 쓸 수 있게 깔끔하게 포장해서 리턴
        // 결과: { "nickname": "홍길동" }
        return ResponseEntity.ok(Collections.singletonMap("nickname", nickname));
    }
}