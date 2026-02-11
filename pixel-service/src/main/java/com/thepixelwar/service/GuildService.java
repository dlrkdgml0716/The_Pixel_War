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

    private static final int MAX_MEMBERS = 30; // 최대 인원 제한

    // 1. 길드 생성
    public String createGuild(GuildCreateRequest request, String providerId, String nickname) {
        if (guildRepository.existsByName(request.name())) {
            return "이미 존재하는 길드 이름입니다.";
        }

        MemberEntity member = getOrCreateMember(providerId, nickname);
        if (member.getGuild() != null) return "ALREADY_HAS_GUILD";

        // 길드 생성 (생성자를 마스터로 지정)
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

        // [신규] 인원 제한 체크
        if (guild.getMembers().size() >= MAX_MEMBERS) {
            return "GUILD_FULL";
        }

        member.joinGuild(guild);
        return "SUCCESS";
    }

    // 3. 길드 탈퇴 (자동 삭제 및 승계 로직 포함)
    public String leaveGuild(String providerId) {
        MemberEntity member = memberRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 없습니다."));

        GuildEntity guild = member.getGuild();
        if (guild == null) return "NO_GUILD";

        // 1. 멤버 탈퇴 처리
        member.joinGuild(null);
        memberRepository.save(member); // DB 반영

        // 2. 남은 멤버 확인
        List<MemberEntity> remainingMembers = memberRepository.findAll().stream()
                .filter(m -> guild.equals(m.getGuild()))
                .collect(Collectors.toList());

        if (remainingMembers.isEmpty()) {
            // A. 남은 사람이 없으면 -> 길드 폭파 💥
            guildRepository.delete(guild);
            return "GUILD_DELETED";
        } else {
            // B. 사람이 남았는데, 나간 사람이 '길드장'이었다면? -> 승계 👑
            if (providerId.equals(guild.getMasterProviderId())) {
                // 가장 오래된 멤버(ID가 작은 순)에게 양도
                remainingMembers.sort(Comparator.comparing(MemberEntity::getId));
                MemberEntity newMaster = remainingMembers.get(0);
                guild.changeMaster(newMaster.getProviderId());
            }
            return "SUCCESS";
        }
    }

    // 4. 내 길드 상세 정보 조회 (UI 개편용)
    @Transactional(readOnly = true)
    public Map<String, Object> getMyGuildDetail(String providerId) {
        MemberEntity member = memberRepository.findByProviderId(providerId).orElse(null);
        if (member == null || member.getGuild() == null) {
            return null; // 길드 없음
        }

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
                "isMaster", providerId.equals(guild.getMasterProviderId()) // 내가 길드장인지 여부
        );
    }

    // 5. 전체 길드 목록 (인원수 포함)
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

    private MemberEntity getOrCreateMember(String providerId, String nickname) {
        return memberRepository.findByProviderId(providerId)
                .orElseGet(() -> memberRepository.save(new MemberEntity(providerId, nickname)));
    }
}