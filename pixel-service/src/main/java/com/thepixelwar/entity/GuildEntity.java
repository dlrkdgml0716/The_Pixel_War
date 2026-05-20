package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity // 데이터의 집합을 의미하고, DB의 테이블과 1:1로 매핑된다는 파일임을 명시
@Getter // 필드를 읽을 수 있는 함수를 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 파라미터가 없는 기본 생성자를 만들어주는 롬복(Lombok) 어노테이션 -> JPA가 db의 정보를 객체로 넘겨줄 때 무조건 기본 생성자가 필요
@Table(name = "guilds")
public class GuildEntity {

    @Id // primary key 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false) // 해당 필드가 유일해야 함을 명시
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_user_id")
    private User master;

    @Column(length = 2000) // URL이 길 수 있으므로 넉넉하게
    private String blueprintUrl;
    private Double blueprintLat;
    private Double blueprintLng;

    @OneToMany(mappedBy = "guild") // 1(길드) : N(멤버), mappedBy = "guild" -> guild 필드가 db에 대한 권한을 가짐
    private List<User> members = new ArrayList<>();

    @Builder
    public GuildEntity(String name, String description, User master) {
        this.name = name;
        this.description = description;
        this.master = master;
    }

    public void changeMaster(User newMaster) {
        this.master = newMaster;
    }

    public void updateBlueprint(String url, Double lat, Double lng) {
        this.blueprintUrl = url;
        this.blueprintLat = lat;
        this.blueprintLng = lng;
    }
}