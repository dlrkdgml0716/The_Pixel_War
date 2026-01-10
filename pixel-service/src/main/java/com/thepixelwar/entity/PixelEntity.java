package com.thepixelwar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pixels")
@Getter @Setter
@NoArgsConstructor
public class PixelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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