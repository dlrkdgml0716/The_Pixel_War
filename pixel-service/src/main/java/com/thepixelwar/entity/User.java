package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity // 데이터의 집합을 의미하고 DB 테이블과 1:1로 매핑되는 파일임을 선언
@Getter // 필드 값을 읽기 위한 getNickname() 같은 메서드를 자동으로 만들어줌
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 파라미터가 없는 기본 생성자를 만들어주는 롬복(Lombok) 어노테이션 -> JPA가 db의 정보를 객체로 넘겨줄 때 무조건 기본 생성자가 필요
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerId"})) // DB 예약어 충돌 방지
public class User {

    @Id // primary key 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // mysql AUTO_INCREMENT와 동일
    private Long id;

    private String provider;    // "kakao"
    private String providerId;  // 카카오 회원번호 (예: 324512...)
    private String nickname;    // 카카오 닉네임
    private String role;        // "ROLE_USER"

    // 여러명이 한 길드에 가입 가능 (N:1 관계)
    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩(내가 필요한 것을 직접 호출하면 조회) <-> EAGER(필요하다 싶으면 자동으로 조회)
    @JoinColumn(name = "guild_id") // GuildEntity에서 만든 테이블과 연결할 외래키를 해당 테이블에 삽입함
    private GuildEntity guild;

    @Builder
    public User(String provider, String providerId, String nickname, String role) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.role = role;
    }

    public User update(String nickname) {
        this.nickname = nickname;
        return this;
    }

    // 길드 가입/변경 편의 메서드
    public void joinGuild(GuildEntity guild) {
        this.guild = guild;
    }
}