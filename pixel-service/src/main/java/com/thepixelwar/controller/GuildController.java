package com.thepixelwar.controller;

import com.thepixelwar.dto.CustomUserDetails;
import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.service.GuildService;
import com.thepixelwar.service.S3UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<String> createGuild(@RequestBody GuildCreateRequest request,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        return ResponseEntity.ok(guildService.createGuild(request, principal.getName(), principal.getNickname()));
    }

    @PostMapping("/{guildId}/join")
    public ResponseEntity<String> joinGuild(@PathVariable Long guildId,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        return ResponseEntity.ok(guildService.joinGuild(guildId, principal.getName(), principal.getNickname()));
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leaveGuild(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");
        return ResponseEntity.ok(guildService.leaveGuild(principal.getName()));
    }

    @PostMapping("/blueprint")
    public ResponseEntity<String> updateBlueprint(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "scale", defaultValue = "1") Double scale,
            @AuthenticationPrincipal CustomUserDetails principal) {

        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            String s3Url = s3UploadService.uploadBlueprint(file);
            String finalUrl = s3Url + "?scale=" + Math.round(scale);
            String result = guildService.updateBlueprint(principal.getName(), finalUrl, lat, lng);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/blueprint")
    public ResponseEntity<String> deleteBlueprint(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        try {
            String result = guildService.updateBlueprint(principal.getName(), "", 0.0, 0.0);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("도안 삭제 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyGuildInfo(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        Map<String, Object> detail = guildService.getMyGuildDetail(principal.getName());
        if (detail == null) {
            return ResponseEntity.ok(Map.of("hasGuild", false));
        }
        return ResponseEntity.ok(detail);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getGuilds() {
        return ResponseEntity.ok(guildService.getAllGuilds());
    }
}
