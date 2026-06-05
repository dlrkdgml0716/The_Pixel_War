package com.thepixelwar.dto;

public record RankResponse(
        int rank,
        String nickname,
        long score
) {}
