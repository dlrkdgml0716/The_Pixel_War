package com.thepixelwar.dto;

import jakarta.validation.constraints.Pattern;

public record PixelRequest(
        double lat,
        double lng,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.")
        String color
) {}