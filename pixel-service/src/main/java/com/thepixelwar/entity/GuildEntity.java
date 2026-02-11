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
    private String name; // 길드 이름

    private String description; // 길드 소개 (한줄평)

    private String masterProviderId;

    // 길드원 목록 (1:N 관계)
    @OneToMany(mappedBy = "guild")
    private List<MemberEntity> members = new ArrayList<>();

    @Builder
    public GuildEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }
    public void changeMaster(String newMasterProviderId) {
        this.masterProviderId = newMasterProviderId;
    }
}