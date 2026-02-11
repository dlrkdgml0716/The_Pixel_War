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

        // 길드 저장
        GuildEntity guild = guildRepository.save(new GuildEntity(request.name(), request.description()));

        // 생성한 사람을 바로 길드에 가입시킴
        joinGuildLogic(guild.getId(), providerId, nickname);

        return "SUCCESS";
    }

    // 2. 길드 가입
    public String joinGuild(Long guildId, String providerId, String nickname) {
        return joinGuildLogic(guildId, providerId, nickname);
    }

    // (내부 로직) 가입 처리
    private String joinGuildLogic(Long guildId, String providerId, String nickname) {
        GuildEntity guild = guildRepository.findById(guildId)
                .orElseThrow(() -> new IllegalArgumentException("길드가 존재하지 않습니다."));

        // 멤버 찾기 (없으면 새로 생성 - OAuth 로그인 시점과 싱크를 맞추기 위함)
        MemberEntity member = memberRepository.findByProviderId(providerId)
                .orElseGet(() -> memberRepository.save(new MemberEntity(providerId, nickname)));

        // 이미 같은 길드면 패스
        if (guild.equals(member.getGuild())) {
            return "이미 가입된 길드입니다.";
        }

        member.joinGuild(guild); // 가입!
        return "SUCCESS";
    }

    // 3. 길드 목록 조회 (테스트용)
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
}