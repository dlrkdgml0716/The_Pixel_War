package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String providerId; // 카카오 등 OAuth ID (예: "kakao_12345")

    private String nickname;

    // 내가 속한 길드 (N:1 관계)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id")
    private GuildEntity guild;

    @Builder
    public MemberEntity(String providerId, String nickname) {
        this.providerId = providerId;
        this.nickname = nickname;
    }

    // 길드 가입/변경 편의 메서드
    public void joinGuild(GuildEntity guild) {
        this.guild = guild;
    }
}