package com.thepixelwar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RankResponse {
    private int rank;
    private String nickname;
    private long score;
}