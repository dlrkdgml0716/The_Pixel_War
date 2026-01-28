package com.thepixelwar.controller;

import com.thepixelwar.dto.RankResponse;
import com.thepixelwar.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ranks")
@RequiredArgsConstructor
public class RankController {

    private final RankingService rankingService;

    @GetMapping
    public List<RankResponse> getTopRanks() {
        return rankingService.getTopRanks();
    }
}