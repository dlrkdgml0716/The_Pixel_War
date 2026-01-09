package com.thepixelwar.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PixelConsumer {

    private final PixelRepository pixelRepository;
    private final ObjectMapper objectMapper; // JSON 파싱용

    @KafkaListener(topics = "pixel-updates", groupId = "pixel-group")
    public void consume(String message) {
        try {
            // 1. Kafka에서 받은 JSON 메시지를 객체로 변환
            PixelRequest request = objectMapper.readValue(message, PixelRequest.class);

            // 2. Entity로 변환하여 MySQL에 저장
            PixelEntity entity = new PixelEntity(
                    request.x(),
                    request.y(),
                    request.color(),
                    request.userId()
            );
            pixelRepository.save(entity);

            log.info("DB 저장 완료: ({}, {}) -> {}", request.x(), request.y(), request.color());
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", e.getMessage());
        }
    }
}