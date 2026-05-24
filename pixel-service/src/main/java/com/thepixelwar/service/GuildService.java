package com.thepixelwar.service;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.entity.GuildEntity;
import com.thepixelwar.entity.User;
import com.thepixelwar.repository.GuildRepository;
import com.thepixelwar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class GuildService {

    private final GuildRepository guildRepository;
    private final UserRepository userRepository;

    private static final int MAX_MEMBERS = 30;

    // 1. 길드 생성
    public String createGuild(GuildCreateRequest request, String providerId, String nickname) {
        if (guildRepository.existsByName(request.name())) {
            return "이미 존재하는 길드 이름입니다.";
        }
        User member = getMember(providerId);
        if (member.getGuild() != null) return "ALREADY_HAS_GUILD";

        GuildEntity guild = guildRepository.save(new GuildEntity(request.name(), request.description(), member));
        member.joinGuild(guild);
        return "SUCCESS";
    }

    // 2. 길드 가입
    public String joinGuild(Long guildId, String providerId, String nickname) {
        User member = getMember(providerId);
        if (member.getGuild() != null) return "ALREADY_HAS_GUILD";

        GuildEntity guild = guildRepository.findById(guildId)
                .orElseThrow(() -> new IllegalArgumentException("길드가 없습니다."));

        if (guild.getMembers().size() >= MAX_MEMBERS) return "GUILD_FULL";

        member.joinGuild(guild);
        return "SUCCESS";
    }

    // 3. 길드 탈퇴
    public String leaveGuild(String providerId) {
        User member = getMember(providerId);

        GuildEntity guild = member.getGuild();
        if (guild == null) return "NO_GUILD";

        List<User> remainingMembers = guild.getMembers().stream()
                .filter(m -> !m.getProviderId().equals(providerId))
                .collect(Collectors.toList());

        member.joinGuild(null);

        if (remainingMembers.isEmpty()) {
            userRepository.saveAndFlush(member); // FK 제약 해제 후 길드 삭제
            guildRepository.delete(guild);
            return "GUILD_DELETED";
        } else {
            if (guild.getMaster().getProviderId().equals(providerId)) {
                remainingMembers.sort(Comparator.comparing(User::getId));
                guild.changeMaster(remainingMembers.get(0));
            }
            return "SUCCESS";
        }
    }

    // 🗺️ [신규] 청사진 업데이트 (길드장 전용)
    public String updateBlueprint(String providerId, String url, Double lat, Double lng) {
        User member = getMember(providerId);

        GuildEntity guild = member.getGuild();
        if (guild == null) return "NO_GUILD";

        // 길드장 권한 체크
        if (!guild.getMaster().getProviderId().equals(providerId)) {
            return "NOT_MASTER";
        }

        guild.updateBlueprint(url, lat, lng);
        return "SUCCESS";
    }

    // 4. 내 길드 상세 정보 조회 (청사진 정보 추가됨)
    @Transactional(readOnly = true)
    public Map<String, Object> getMyGuildDetail(String providerId) {
        User member = userRepository.findByProviderId(providerId).orElse(null);
        if (member == null || member.getGuild() == null) return null;

        GuildEntity guild = member.getGuild();
        User master = guild.getMaster();
        String masterName = (master != null) ? master.getNickname() : "Unknown";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", guild.getId());
        result.put("name", guild.getName());
        result.put("description", guild.getDescription() == null ? "" : guild.getDescription());
        result.put("masterName", masterName);
        result.put("memberCount", guild.getMembers().size());
        result.put("maxMembers", MAX_MEMBERS);
        result.put("isMaster", guild.getMaster() != null && guild.getMaster().getProviderId().equals(providerId));
        result.put("blueprintUrl", guild.getBlueprintUrl() == null ? "" : guild.getBlueprintUrl());
        result.put("blueprintLat", guild.getBlueprintLat() == null ? 0.0 : guild.getBlueprintLat());
        result.put("blueprintLng", guild.getBlueprintLng() == null ? 0.0 : guild.getBlueprintLng());
        return result;
    }

    // 5. 전체 길드 목록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllGuilds() {
        return guildRepository.findAllWithMembers().stream()
                .map(g -> Map.<String, Object>of(
                        "id", g.getId(),
                        "name", g.getName(),
                        "description", g.getDescription() == null ? "" : g.getDescription(),
                        "memberCount", g.getMembers().size(),
                        "maxMembers", MAX_MEMBERS
                ))
                .toList();
    }

    public Long getMyGuildId(String providerId) {
        return userRepository.findByProviderId(providerId)
                .map(User::getGuild)
                .map(GuildEntity::getId)
                .orElse(null);
    }

    private User getMember(String providerId) {
        return userRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 없습니다."));
    }
}
