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

    // 길드 탈퇴 (POST /api/guilds/leave)
    @PostMapping("/leave")
    public ResponseEntity<String> leaveGuild(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");
        String result = guildService.leaveGuild(principal.getName());
        return ResponseEntity.ok(result);
    }

    // 내 길드 정보 조회 (GET /api/guilds/my)
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyGuildInfo(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        Long myGuildId = guildService.getMyGuildId(principal.getName());
        // 길드가 없으면 -1 반환 (null은 JSON에서 빠질 수 있으므로)
        return ResponseEntity.ok(Map.of("guildId", myGuildId != null ? myGuildId : -1L));
    }

    // 길드 목록 조회 (GET /api/guilds)
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getGuilds() {
        return ResponseEntity.ok(guildService.getAllGuilds());
    }
}