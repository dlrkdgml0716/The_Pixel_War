package com.thepixelwar.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingConcurrencyTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String RANKING_KEY = "pixel-war:ranking";

    @BeforeEach
    void setUp() {
        // 테스트 전 랭킹 데이터 초기화
        redisTemplate.delete(RANKING_KEY);
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리 (선택 사항)
        redisTemplate.delete(RANKING_KEY);
    }

    @Test
    @DisplayName("동시성 테스트: 100명이 동시에 점수를 올려도 누락되지 않아야 한다.")
    void increaseScoreConcurrencyTest() throws InterruptedException {
        // Given
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        String userId = "test_user";

        // When: 100개의 스레드가 동시에 increaseScore 호출
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    rankingService.increaseScore(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 끝날 때까지 대기

        // Then: 점수가 정확히 100점이어야 함
        Double score = redisTemplate.opsForZSet().score(RANKING_KEY, userId);
        assertThat(score).isEqualTo(100.0);

        System.out.println("✅ 테스트 성공! 최종 점수: " + score);
    }

    @Test
    @DisplayName("경쟁 테스트: A가 땅을 1000번 먹고, B가 500번 뺏으면 점수가 정확해야 한다.")
    void competitionTest() throws InterruptedException {
        // Given
        int totalClicks = 1000;
        int stealClicks = 500;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 32개 스레드 풀
        CountDownLatch latch = new CountDownLatch(totalClicks + stealClicks);

        String userA = "User_A";
        String userB = "User_B";

        // When
        // 1. User A가 1000번 점수 획득
        for (int i = 0; i < totalClicks; i++) {
            executorService.submit(() -> {
                try {
                    rankingService.increaseScore(userA);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 2. User B가 동시에 500번 A의 땅을 뺏음 (A점수 차감, B점수 획득)
        // (실제 로직에서는 PixelConsumer가 이 역할을 하지만, 여기서는 Service 로직만 검증)
        for (int i = 0; i < stealClicks; i++) {
            executorService.submit(() -> {
                try {
                    rankingService.decreaseScore(userA);
                    rankingService.increaseScore(userB);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // Then
        Double scoreA = redisTemplate.opsForZSet().score(RANKING_KEY, userA);
        Double scoreB = redisTemplate.opsForZSet().score(RANKING_KEY, userB);

        // A 예상 점수: 1000 - 500 = 500
        // B 예상 점수: 500
        assertThat(scoreA).isEqualTo(500.0);
        assertThat(scoreB).isEqualTo(500.0);

        System.out.println("✅ 경쟁 테스트 성공!");
        System.out.println("User A Score: " + scoreA);
        System.out.println("User B Score: " + scoreB);
    }
}