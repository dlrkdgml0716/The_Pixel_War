package com.thepixelwar.service;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.entity.GuildEntity;
import com.thepixelwar.entity.MemberEntity;
import com.thepixelwar.repository.GuildRepository;
import com.thepixelwar.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GuildService {

    private final GuildRepository guildRepository;
    private final MemberRepository memberRepository;

    private static final int MAX_MEMBERS = 30;

    // 1. 길드 생성
    public String createGuild(GuildCreateRequest request, String providerId, String nickname) {
        if (guildRepository.existsByName(request.name())) {
            return "이미 존재하는 길드 이름입니다.";
        }
        MemberEntity member = getOrCreateMember(providerId, nickname);
        if (member.getGuild() != null) return "ALREADY_HAS_GUILD";

        GuildEntity guild = guildRepository.save(new GuildEntity(request.name(), request.description(), providerId));
        member.joinGuild(guild);
        return "SUCCESS";
    }

    // 2. 길드 가입
    public String joinGuild(Long guildId, String providerId, String nickname) {
        MemberEntity member = getOrCreateMember(providerId, nickname);
        if (member.getGuild() != null) return "ALREADY_HAS_GUILD";

        GuildEntity guild = guildRepository.findById(guildId)
                .orElseThrow(() -> new IllegalArgumentException("길드가 없습니다."));

        if (guild.getMembers().size() >= MAX_MEMBERS) return "GUILD_FULL";

        member.joinGuild(guild);
        return "SUCCESS";
    }

    // 3. 길드 탈퇴
    public String leaveGuild(String providerId) {
        MemberEntity member = memberRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 없습니다."));

        GuildEntity guild = member.getGuild();
        if (guild == null) return "NO_GUILD";

        member.joinGuild(null);
        memberRepository.save(member);

        List<MemberEntity> remainingMembers = memberRepository.findAll().stream()
                .filter(m -> guild.equals(m.getGuild()))
                .collect(Collectors.toList());

        if (remainingMembers.isEmpty()) {
            guildRepository.delete(guild);
            return "GUILD_DELETED";
        } else {
            if (providerId.equals(guild.getMasterProviderId())) {
                remainingMembers.sort(Comparator.comparing(MemberEntity::getId));
                guild.changeMaster(remainingMembers.get(0).getProviderId());
            }
            return "SUCCESS";
        }
    }

    // 🗺️ [신규] 청사진 업데이트 (길드장 전용)
    public String updateBlueprint(String providerId, String url, Double lat, Double lng) {
        MemberEntity member = memberRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        GuildEntity guild = member.getGuild();
        if (guild == null) return "NO_GUILD";

        // 길드장 권한 체크
        if (!guild.getMasterProviderId().equals(providerId)) {
            return "NOT_MASTER";
        }

        guild.updateBlueprint(url, lat, lng);
        return "SUCCESS";
    }

    // 4. 내 길드 상세 정보 조회 (청사진 정보 추가됨)
    @Transactional(readOnly = true)
    public Map<String, Object> getMyGuildDetail(String providerId) {
        MemberEntity member = memberRepository.findByProviderId(providerId).orElse(null);
        if (member == null || member.getGuild() == null) return null;

        GuildEntity guild = member.getGuild();
        MemberEntity master = memberRepository.findByProviderId(guild.getMasterProviderId()).orElse(null);
        String masterName = (master != null) ? master.getNickname() : "Unknown";

        return Map.of(
                "id", guild.getId(),
                "name", guild.getName(),
                "description", guild.getDescription() == null ? "" : guild.getDescription(),
                "masterName", masterName,
                "memberCount", guild.getMembers().size(),
                "maxMembers", MAX_MEMBERS,
                "isMaster", providerId.equals(guild.getMasterProviderId()),
                // 👇 청사진 정보 추가
                "blueprintUrl", guild.getBlueprintUrl() == null ? "" : guild.getBlueprintUrl(),
                "blueprintLat", guild.getBlueprintLat() == null ? 0.0 : guild.getBlueprintLat(),
                "blueprintLng", guild.getBlueprintLng() == null ? 0.0 : guild.getBlueprintLng()
        );
    }

    // 5. 전체 길드 목록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllGuilds() {
        return guildRepository.findAll().stream()
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
        return memberRepository.findByProviderId(providerId)
                .map(MemberEntity::getGuild)
                .map(GuildEntity::getId)
                .orElse(null);
    }

    private MemberEntity getOrCreateMember(String providerId, String nickname) {
        return memberRepository.findByProviderId(providerId)
                .orElseGet(() -> memberRepository.save(new MemberEntity(providerId, nickname)));
    }
}