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

    private String masterProviderId;

    // 🗺️ [신규] 청사진(오버레이) 정보
    @Column(length = 2000) // URL이 길 수 있으므로 넉넉하게
    private String blueprintUrl;
    private Double blueprintLat;
    private Double blueprintLng;

    @OneToMany(mappedBy = "guild", cascade = CascadeType.ALL)
    private List<MemberEntity> members = new ArrayList<>();

    @Builder
    public GuildEntity(String name, String description, String masterProviderId) {
        this.name = name;
        this.description = description;
        this.masterProviderId = masterProviderId;
    }

    public void changeMaster(String newMasterProviderId) {
        this.masterProviderId = newMasterProviderId;
    }

    // 🗺️ [신규] 청사진 업데이트 메서드
    public void updateBlueprint(String url, Double lat, Double lng) {
        this.blueprintUrl = url;
        this.blueprintLat = lat;
        this.blueprintLng = lng;
    }
}