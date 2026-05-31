package com.thepixelwar.dto;

public record PixelResponse(
        double lat,
        double lng,
        String color,
        String nickname
) {}
