package com.thepixelwar.controller;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.service.GuildService;
import com.thepixelwar.service.S3UploadService; // 🚨 추가된 S3 서비스
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // 🚨 추가된 파일 처리 클래스

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildService guildService;
    private final S3UploadService s3UploadService; // 🚨 S3 업로드 공장 주입!

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

    // 🗺️ [수정됨] 청사진(도안) 업데이트 API - S3 업로드 적용!
    // JSON이 아닌 폼 데이터(FormData) 형식으로 파일과 좌표를 받습니다.
    @PostMapping("/blueprint")
    public ResponseEntity<String> updateBlueprint(
            @RequestParam("file") MultipartFile file,   // 1. 프론트에서 보낸 파일
            @RequestParam("lat") Double lat,            // 2. 위도
            @RequestParam("lng") Double lng,            // 3. 경도
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            // 1. 파일을 S3에 업로드하고, 영구적인 인터넷 URL을 발급받습니다.
            String s3Url = s3UploadService.uploadBlueprint(file);

            // 2. 기존 길드 서비스에 S3 URL과 좌표를 넘겨 DB에 저장합니다.
            String result = guildService.updateBlueprint(principal.getName(), s3Url, lat, lng);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("이미지 업로드 중 오류가 발생했습니다: " + e.getMessage());
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