package com.thepixelwar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PixelService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate; // 1. Kafka 도구 추가
    private final ObjectMapper objectMapper; // 2. 데이터를 JSON으로 변환할 도구

    public String updatePixel(PixelRequest request) {
        String lockKey = "pixel:lock:" + request.x() + ":" + request.y();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                try {
                    // [1] Redis에 실시간 저장 (빠른 응답용)
                    String pixelKey = "pixel:" + request.x() + ":" + request.y();
                    redisTemplate.opsForValue().set(pixelKey, request.color());

                    // [2] Kafka로 메시지 전송 (DB 저장용 - 비동기 처리)
                    String message = objectMapper.writeValueAsString(request);
                    kafkaTemplate.send("pixel-updates", message); // "pixel-updates"라는 이름의 대기열에 던짐

                    log.info("픽셀 저장 및 Kafka 전송 완료: {} -> {}", pixelKey, request.color());
                    return "픽셀 업데이트 요청이 접수되었습니다: (" + request.x() + ", " + request.y() + ")";
                } catch (JsonProcessingException e) {
                    log.error("메시지 변환 중 오류 발생", e);
                } finally {
                    // [수정] 내가 락을 잡고 있고, 아직 잠겨 있는 경우에만 해제 시도
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("락 획득 중 오류 발생", e);
        }

        return "시스템이 바쁩니다. 잠시 후 다시 시도해주세요.";
    }
}