package com.thepixelwar.consumer;

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

    private final ObjectMapper objectMapper;
    private final PixelRepository pixelRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // [설정] PixelService와 반드시 일치해야 함
    private static final double GRID_SIZE = 0.0003;
    private static final double EPSILON = 0.0000001;

    @KafkaListener(topics = "pixel-updates", groupId = "pixel-war-group")
    @Transactional
    public void consume(String message) {
        try {
            PixelRequest request = objectMapper.readValue(message, PixelRequest.class);

            // [수정 1] 곱하기(*)가 아니라 나누기(/)여야 인덱스가 나옵니다.
            int x = (int) Math.floor((request.lat() + EPSILON) / GRID_SIZE);
            int y = (int) Math.floor((request.lng() + EPSILON) / GRID_SIZE);

            PixelEntity existingPixel = pixelRepository.findByCoords(x, y);

            if (existingPixel != null) {
                existingPixel.setColor(request.color());
                existingPixel.setUserId(request.userId());
            } else {
                pixelRepository.save(new PixelEntity(x, y, request.color(), request.userId()));
            }

            // [수정 2] 나누기(/)가 아니라 곱하기(*)여야 좌표가 복원됩니다.
            double snappedLat = x * GRID_SIZE;
            double snappedLng = y * GRID_SIZE;

            PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), request.userId());

            // 클라이언트들에게 "DB 저장됐으니 화면 업데이트 해!" 하고 전송
            messagingTemplate.convertAndSend("/topic/pixel", snappedRequest);

        } catch (Exception e) {
            log.error("Kafka Consume Error", e);
        }
    }
}