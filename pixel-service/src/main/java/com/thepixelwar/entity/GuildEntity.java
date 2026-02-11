package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guilds")
public class GuildEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    // [신규] 길드장 식별자 (이 부분이 없어서 에러가 난 겁니다!)
    private String masterProviderId;

    @OneToMany(mappedBy = "guild", cascade = CascadeType.ALL)
    private List<MemberEntity> members = new ArrayList<>();

    @Builder
    public GuildEntity(String name, String description, String masterProviderId) {
        this.name = name;
        this.description = description;
        this.masterProviderId = masterProviderId;
    }

    // 길드장 변경 편의 메서드
    public void changeMaster(String newMasterProviderId) {
        this.masterProviderId = newMasterProviderId;
    }
}