package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;

@Entity
@Table(name = "pixels",
        indexes = {
                @Index(name = "idx_pixel_coords", columnList = "x,y", unique = true)
        }
)
@Getter @Setter
@NoArgsConstructor
public class PixelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // private LocalDateTime createdAt; // 성능 테스트를 위해 추가된 변수
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