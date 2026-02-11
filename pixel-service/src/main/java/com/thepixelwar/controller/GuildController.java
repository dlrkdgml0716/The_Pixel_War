package com.thepixelwar.controller;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.service.GuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildService guildService;

    // 길드 생성 (POST /api/guilds)
    @PostMapping
    public ResponseEntity<String> createGuild(@RequestBody GuildCreateRequest request,
                                              @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");

        // 카카오 로그인의 경우 attributes에서 id와 properties를 가져옴
        String providerId = principal.getName();
        Map<String, Object> properties = (Map<String, Object>) principal.getAttributes().get("properties");
        String nickname = (String) properties.get("nickname");

        String result = guildService.createGuild(request, providerId, nickname);
        return ResponseEntity.ok(result);
    }

    // 길드 가입 (POST /api/guilds/{guildId}/join)
    @PostMapping("/{guildId}/join")
    public ResponseEntity<String> joinGuild(@PathVariable Long guildId,
                                            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");

        String providerId = principal.getName();
        Map<String, Object> properties = (Map<String, Object>) principal.getAttributes().get("properties");
        String nickname = (String) properties.get("nickname");

        String result = guildService.joinGuild(guildId, providerId, nickname);
        return ResponseEntity.ok(result);
    }

    // 길드 목록 조회 (GET /api/guilds)
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getGuilds() {
        return ResponseEntity.ok(guildService.getAllGuilds());
    }
}