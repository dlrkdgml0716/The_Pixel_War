package com.thepixelwar.service;

import com.thepixelwar.dto.GuildCreateRequest;
import com.thepixelwar.entity.GuildEntity;
import com.thepixelwar.entity.MemberEntity;
import com.thepixelwar.repository.GuildRepository;
import com.thepixelwar.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class GuildService {

    private final GuildRepository guildRepository;
    private final MemberRepository memberRepository;

    // 1. 길드 생성
    public String createGuild(GuildCreateRequest request, String providerId, String nickname) {
        if (guildRepository.existsByName(request.name())) {
            return "이미 존재하는 길드 이름입니다.";
        }

        // 유저 확인 및 중복 가입 체크
        MemberEntity member = getOrCreateMember(providerId, nickname);
        if (member.getGuild() != null) {
            return "ALREADY_HAS_GUILD"; // 이미 길드가 있으면 생성 불가
        }

        // 길드 저장
        GuildEntity guild = guildRepository.save(new GuildEntity(request.name(), request.description()));

        // 생성한 사람을 바로 길드에 가입시킴
        member.joinGuild(guild);

        return "SUCCESS";
    }

    // 2. 길드 가입
    public String joinGuild(Long guildId, String providerId, String nickname) {
        MemberEntity member = getOrCreateMember(providerId, nickname);

        // 1인 1길드 원칙: 이미 가입된 길드가 있으면 거절
        if (member.getGuild() != null) {
            return "ALREADY_HAS_GUILD";
        }

        GuildEntity guild = guildRepository.findById(guildId)
                .orElseThrow(() -> new IllegalArgumentException("길드가 존재하지 않습니다."));

        member.joinGuild(guild); // 가입!
        return "SUCCESS";
    }

    // 3. 길드 탈퇴
    public String leaveGuild(String providerId) {
        MemberEntity member = memberRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 없습니다."));

        if (member.getGuild() == null) {
            return "NO_GUILD"; // 가입된 길드가 없음
        }

        member.joinGuild(null); // 길드 정보를 비움 (탈퇴)
        return "SUCCESS";
    }

    // 4. 내 길드 ID 조회 (프론트엔드 버튼 상태 결정용)
    @Transactional(readOnly = true)
    public Long getMyGuildId(String providerId) {
        return memberRepository.findByProviderId(providerId)
                .map(MemberEntity::getGuild)
                .map(GuildEntity::getId)
                .orElse(null); // 길드가 없거나 유저가 없으면 null 반환
    }

    // 5. 길드 목록 조회 (전체)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllGuilds() {
        return guildRepository.findAll().stream()
                .map(g -> Map.<String, Object>of(
                        "id", g.getId(),
                        "name", g.getName(),
                        "description", g.getDescription() == null ? "" : g.getDescription()
                ))
                .toList();
    }

    // (내부 헬퍼) 멤버 조회 또는 생성
    private MemberEntity getOrCreateMember(String providerId, String nickname) {
        return memberRepository.findByProviderId(providerId)
                .orElseGet(() -> memberRepository.save(new MemberEntity(providerId, nickname)));
    }
}