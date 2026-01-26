package com.thepixelwar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PixelService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PixelRepository pixelRepository;

    // [설정] HTML과 동일한 격자 크기
    private static final double GRID_SIZE = 0.0003;
    private static final long COOLDOWN_SECONDS = 5;
    private static final double EPSILON = 0.0000001;

    /**
     * 1. 픽셀 찍기 (쓰기)
     */
    public String updatePixel(PixelRequest request) {
        String userId = request.userId();

        // 쿨타임 체크
        String cooldownKey = "cooldown:" + userId;
        Long remainingTime = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);

        if (remainingTime != null && remainingTime > 0) {
            return "쿨타임이 " + remainingTime + "초 남았습니다!";
        }

        // 좌표 계산
        int x = (int) Math.floor((request.lat() + EPSILON) / GRID_SIZE);
        int y = (int) Math.floor((request.lng() + EPSILON) / GRID_SIZE);

        double snappedLat = x * GRID_SIZE;
        double snappedLng = y * GRID_SIZE;

        PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), request.userId());

        // 락 획득
        String lockKey = "pixel:lock:" + x + ":" + y;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                try {
                    // Redis 저장
                    String pixelKey = "pixel:" + x + ":" + y;
                    redisTemplate.opsForValue().set(pixelKey, snappedRequest.color());

                    // Kafka 전송
                    String message = objectMapper.writeValueAsString(snappedRequest);
                    kafkaTemplate.send("pixel-updates", message);

                    // 쿨타임 설정
                    redisTemplate.opsForValue().set(cooldownKey, "active", Duration.ofSeconds(COOLDOWN_SECONDS));

                    return "성공";
                } catch (JsonProcessingException e) {
                    log.error("JSON 에러", e);
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            } else {
                return "다른 사람이 작업 중입니다.";
            }
        } catch (InterruptedException e) {
            log.error("락 에러", e);
            Thread.currentThread().interrupt();
        }
        return "실패";
    }

    /**
     * 2. 영역 기반 조회 (최적화된 읽기)
     * 화면에 보이는 부분만 가져옵니다.
     */
    @Transactional(readOnly = true)
    public List<PixelRequest> getPixelsInBounds(double minLat, double maxLat, double minLng, double maxLng) {
        int minX = (int) Math.floor((minLat + EPSILON) / GRID_SIZE);
        int maxX = (int) Math.ceil((maxLat + EPSILON) / GRID_SIZE);
        int minY = (int) Math.floor((minLng + EPSILON) / GRID_SIZE);
        int maxY = (int) Math.ceil((maxLng + EPSILON) / GRID_SIZE);

        return pixelRepository.findByArea(minX, maxX, minY, maxY).stream()
                .map(entity -> new PixelRequest(
                        entity.getX() * GRID_SIZE,
                        entity.getY() * GRID_SIZE,
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
    }

    /**
     * [복구됨] 3. 단일 픽셀 색상 조회
     * Controller의 getPixel 메서드에서 사용합니다.
     */
    @Transactional(readOnly = true)
    public String getPixelColor(int x, int y) {
        PixelEntity pixel = pixelRepository.findByCoords(x, y);
        // 픽셀이 없으면 기본값(흰색) 또는 null 반환
        return pixel != null ? pixel.getColor() : "#FFFFFF";
    }

    /**
     * [복구됨] 4. 전체 픽셀 조회
     * Controller의 getPixels 메서드(범위 없을 때)에서 사용합니다.
     */
    @Transactional(readOnly = true)
    public List<PixelRequest> getAllPixels() {
        return pixelRepository.findAll().stream()
                .map(entity -> new PixelRequest(
                        entity.getX() * GRID_SIZE,
                        entity.getY() * GRID_SIZE,
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
    }
}