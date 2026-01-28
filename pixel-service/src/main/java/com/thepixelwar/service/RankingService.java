package com.thepixelwar.service;

import com.thepixelwar.dto.RankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final StringRedisTemplate redisTemplate;
    private static final String RANKING_KEY = "pixel-war:ranking";

    // 점수 증가 (빈 땅 먹음 or 남의 땅 뺏음)
    public void increaseScore(String userId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, userId, 1);
    }

    // 점수 감소 (남에게 땅 뺏김)
    public void decreaseScore(String userId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, userId, -1);
    }

    // Top 10 조회 (점수 높은 순)
    public List<RankResponse> getTopRanks() {
        // Redis ZREVRANGE: 점수 높은 순으로 0등부터 9등까지 조회
        Set<ZSetOperations.TypedTuple<String>> topUsers =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, 9);

        List<RankResponse> result = new ArrayList<>();
        int rank = 1;

        if (topUsers != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topUsers) {
                String userId = tuple.getValue();
                // 점수가 null이면 0 처리
                long score = tuple.getScore() != null ? tuple.getScore().longValue() : 0;
                result.add(new RankResponse(rank++, userId, score));
            }
        }
        return result;
    }
}