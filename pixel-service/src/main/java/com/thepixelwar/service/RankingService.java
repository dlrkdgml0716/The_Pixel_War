package com.thepixelwar.service;

import com.thepixelwar.dto.RankResponse;
import com.thepixelwar.entity.User;
import com.thepixelwar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private static final String RANKING_KEY = "pixel-war:ranking";

    // 점수 증가 (빈 땅 먹음 or 남의 땅 뺏음)
    public void increaseScore(String providerId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, providerId, 1);
    }

    // 점수 감소 (남에게 땅 뺏김)
    public void decreaseScore(String providerId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, providerId, -1);
    }

    // Top 10 조회 (점수 높은 순)
    @Transactional(readOnly = true)
    public List<RankResponse> getTopRanks() {
        Set<ZSetOperations.TypedTuple<String>> topUsers =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, 9);

        List<RankResponse> result = new ArrayList<>();
        int rank = 1;

        if (topUsers != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topUsers) {
                String providerId = tuple.getValue();
                long score = tuple.getScore() != null ? tuple.getScore().longValue() : 0;

                String nickname = userRepository.findByProviderId(providerId)
                        .map(User::getNickname)
                        .orElse(providerId);

                result.add(new RankResponse(rank++, nickname, score));
            }
        }
        return result;
    }
}