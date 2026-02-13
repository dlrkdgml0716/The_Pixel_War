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

    @PostMapping
    public ResponseEntity<String> createGuild(@RequestBody GuildCreateRequest request, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        String providerId = principal.getName();
        String nickname = (String) ((Map<String, Object>) principal.getAttributes().get("properties")).get("nickname");
        return ResponseEntity.ok(guildService.createGuild(request, providerId, nickname));
    }

    @PostMapping("/{guildId}/join")
    public ResponseEntity<String> joinGuild(@PathVariable Long guildId, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        String providerId = principal.getName();
        String nickname = (String) ((Map<String, Object>) principal.getAttributes().get("properties")).get("nickname");
        return ResponseEntity.ok(guildService.joinGuild(guildId, providerId, nickname));
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leaveGuild(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        return ResponseEntity.ok(guildService.leaveGuild(principal.getName()));
    }

    // 🗺️ [신규] 청사진 업데이트 API
    @PostMapping("/blueprint")
    public ResponseEntity<String> updateBlueprint(@RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        String url = (String) body.get("url");
        // JSON 숫자는 Double로 바로 안 올 수도 있어서 안전하게 변환
        Double lat = Double.valueOf(body.get("lat").toString());
        Double lng = Double.valueOf(body.get("lng").toString());

        String result = guildService.updateBlueprint(principal.getName(), url, lat, lng);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyGuildInfo(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        Map<String, Object> detail = guildService.getMyGuildDetail(principal.getName());
        if (detail == null) {
            return ResponseEntity.ok(Map.of("hasGuild", false));
        } else {
            return ResponseEntity.ok(detail);
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getGuilds() {
        return ResponseEntity.ok(guildService.getAllGuilds());
    }
}