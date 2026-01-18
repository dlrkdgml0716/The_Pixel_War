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

    private static final double GRID_DIVISOR = 10000.0;
    private static final double EPSILON = 0.0000001;

    public String updatePixel(PixelRequest request) {
        // [수정] round(반올림) -> floor(내림) 변경
        // 클릭한 위치가 포함된 격자의 "남서쪽(Left-Bottom)" 모서리 좌표를 인덱스로 잡습니다.
        int x = (int) Math.floor((request.lat() + EPSILON) * GRID_DIVISOR);
        int y = (int) Math.floor((request.lng() + EPSILON) * GRID_DIVISOR);

        double snappedLat = (double) x / GRID_DIVISOR;
        double snappedLng = (double) y / GRID_DIVISOR;

        PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), request.userId());
        // 테스트를 위해 쿨타임 잠시 해제하고 싶으면 아래 줄 주석 처리
//        String cooldownKey = "user:cooldown:" + request.userId();
//        Boolean canUpdate = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "locked", 1, TimeUnit.SECONDS);
//        if (Boolean.FALSE.equals(canUpdate)) {
//            return "쿨타임 중입니다!";
//        }

        String lockKey = "pixel:lock:" + x + ":" + y;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                try {
                    String pixelKey = "pixel:" + x + ":" + y;
                    redisTemplate.opsForValue().set(pixelKey, snappedRequest.color());

                    String message = objectMapper.writeValueAsString(snappedRequest);
                    kafkaTemplate.send("pixel-updates", message);

                    return "성공";
                } catch (JsonProcessingException e) {
                    log.error("JSON 에러", e);
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            log.error("락 에러", e);
        }
        return "실패";
    }

    @Transactional(readOnly = true)
    public String getPixelColor(int x, int y) {
        PixelEntity pixel = pixelRepository.findByCoords(x, y);
        return pixel != null ? pixel.getColor() : "#FFFFFF";
    }

    @Transactional(readOnly = true)
    public List<PixelRequest> getAllPixels() {
        return pixelRepository.findAll().stream()
                .map(entity -> new PixelRequest(
                        (double) entity.getX() / GRID_DIVISOR,
                        (double) entity.getY() / GRID_DIVISOR,
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
    }
}