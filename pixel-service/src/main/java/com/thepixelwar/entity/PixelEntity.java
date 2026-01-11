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

    private double lat;
    private double lng;
    private String color;
    private String userId;

    public PixelEntity(double lat, double lng, String color, String userId) {
        this.lat = lat;
        this.lng = lng;
        this.color = color;
        this.userId = userId;
    }
}