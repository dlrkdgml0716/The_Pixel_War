package com.thepixelwar.controller;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.service.GuildService;
import com.thepixelwar.service.S3UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildService guildService;
    private final S3UploadService s3UploadService;

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

    // 🗺️ 청사진 업데이트 API (크기 조절 scale 포함)
    @PostMapping("/blueprint")
    public ResponseEntity<String> updateBlueprint(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "scale", defaultValue = "1") Double scale,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            String s3Url = s3UploadService.uploadBlueprint(file);
            String finalUrl = s3Url + "?scale=" + scale;
            String result = guildService.updateBlueprint(principal.getName(), finalUrl, lat, lng);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    // 🗑️ [신규] 청사진 삭제 API (길드장 전용)
    @DeleteMapping("/blueprint")
    public ResponseEntity<String> deleteBlueprint(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            // DB의 url, lat, lng를 비워버립니다 (0.0 좌표와 빈 문자열)
            String result = guildService.updateBlueprint(principal.getName(), "", 0.0, 0.0);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("도안 삭제 중 오류가 발생했습니다.");
        }
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