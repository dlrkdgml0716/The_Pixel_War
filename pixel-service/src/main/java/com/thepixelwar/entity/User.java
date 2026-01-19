package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users") // DB 예약어 충돌 방지
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;    // "kakao"
    private String providerId;  // 카카오 회원번호 (예: 324512...)
    private String nickname;    // 카카오 닉네임
    private String role;        // "ROLE_USER"

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
}