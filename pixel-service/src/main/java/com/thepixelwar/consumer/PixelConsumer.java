package com.thepixelwar.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PixelConsumer {

    private final ObjectMapper objectMapper; // 역직렬화(문자열 -> 객체)를 위한 도구
    private final PixelRepository pixelRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "pixel-updates", groupId = "pixel-war-group")
    @Transactional
    public void consume(String message) {
        try {
            PixelRequest request = objectMapper.readValue(message, PixelRequest.class);

            // DB 저장
            PixelEntity pixelEntity = new PixelEntity(request.x(), request.y(), request.color(), request.userId());
            pixelRepository.save(pixelEntity);

            // 웹소켓 전송 "/topic/pixel"을 구독 중인 모든 사람한테 픽셀 정보를 던짐!
            messagingTemplate.convertAndSend("/topic/pixel", request);

            log.info("실시간 방송 완료: ({}, {}) -> {}", request.x(), request.y(), request.color());

        } catch (Exception e) {
            log.error("컨슈머 작업 중 에러 발생!", e);
        }
    }
}