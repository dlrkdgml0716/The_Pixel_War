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

    private static final double GRID_DIVISOR = 10000.0;
    private static final double EPSILON = 0.0000001;

    @KafkaListener(topics = "pixel-updates", groupId = "pixel-war-group")
    @Transactional
    public void consume(String message) {
        try {
            PixelRequest request = objectMapper.readValue(message, PixelRequest.class);

            // [수정] floor(내림) 사용
            int x = (int) Math.floor((request.lat() + EPSILON) * GRID_DIVISOR);
            int y = (int) Math.floor((request.lng() + EPSILON) * GRID_DIVISOR);

            PixelEntity existingPixel = pixelRepository.findByCoords(x, y);

            if (existingPixel != null) {
                existingPixel.setColor(request.color());
                existingPixel.setUserId(request.userId());
            } else {
                pixelRepository.save(new PixelEntity(x, y, request.color(), request.userId()));
            }

            double snappedLat = (double) x / GRID_DIVISOR;
            double snappedLng = (double) y / GRID_DIVISOR;

            PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), request.userId());

            messagingTemplate.convertAndSend("/topic/pixel", snappedRequest);

        } catch (Exception e) {
            log.error("Kafka Consume Error", e);
        }
    }
}