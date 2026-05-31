package com.thepixelwar.dto;

public record HotPixelResponse(
        double lat,
        double lng,
        int hitCount
) {}