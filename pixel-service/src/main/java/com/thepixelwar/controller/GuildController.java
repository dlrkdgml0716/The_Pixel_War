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
            @RequestParam("file") MultipartFile file,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "scale", defaultValue = "0.05") Double scale, // 🚨 크기(scale) 값 받기 추가!
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            String s3Url = s3UploadService.uploadBlueprint(file);

            // 🚨 S3 URL 뒤에 몰래 크기 정보를 꼬리표처럼 붙여줍니다. (?scale=0.05)
            // 이러면 굳이 데이터베이스(DB) 구조를 바꾸지 않아도 크기를 영구 저장할 수 있습니다!
            String finalUrl = s3Url + "?scale=" + scale;

            String result = guildService.updateBlueprint(principal.getName(), finalUrl, lat, lng);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("이미지 업로드 중 오류가 발생했습니다.");
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