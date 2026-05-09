package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;

@Entity // 데이터의 집합을 의미하고 DB 테이블과 1:1로 매핑되는 파일임을 선언
@Table(name = "pixels",
        indexes = {
                @Index(name = "idx_pixel_coords", columnList = "x,y", unique = true)
        }
)
@Getter @Setter // 필드 값을 읽고 수정 위한 메서드를 자동으로 만들어줌
@NoArgsConstructor // 파라미터가 없는 기본 생성자를 만들어주는 롬복(Lombok) 어노테이션 -> JPA가 db의 정보를 객체로 넘겨줄 때 무조건 기본 생성자가 필요
public class PixelEntity {

    @Id // primary key 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // mysql AUTO_INCREMENT와 동일
    private Long id;

    // private LocalDateTime createdAt; // 성능 테스트를 위한 변수
    private int x;
    private int y;
    private String color;
    private String userId;

    public PixelEntity(int x, int y, String color, String userId) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.userId = userId;
    }
}