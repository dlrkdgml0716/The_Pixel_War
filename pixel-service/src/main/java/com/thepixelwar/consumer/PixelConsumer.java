package com.thepixelwar.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import com.thepixelwar.service.RankingService; // [추가]
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
    private final RankingService rankingService; // [추가] 랭킹 서비스 주입

    // [설정] PixelService와 반드시 일치해야 함
    private static final double GRID_SIZE = 0.0003;
    private static final double EPSILON = 0.0000001;

    @KafkaListener(topics = "pixel-updates", groupId = "pixel-war-group")
    @Transactional
    public void consume(String message) {
        try {
            PixelRequest request = objectMapper.readValue(message, PixelRequest.class);

            // 좌표 계산 (Service와 동일)
            int x = (int) Math.floor((request.lat() + EPSILON) / GRID_SIZE);
            int y = (int) Math.floor((request.lng() + EPSILON) / GRID_SIZE);

            PixelEntity existingPixel = pixelRepository.findByCoords(x, y);
            String newOwner = request.userId(); // 현재 픽셀을 찍은 사람

            if (existingPixel != null) {
                // --- [CASE 1: 이미 누가 차지한 땅인 경우] ---
                String oldOwner = existingPixel.getUserId();

                // 주인이 바뀌었는가? (내 땅에 내가 다시 찍는 건 점수 변동 X)
                if (!oldOwner.equals(newOwner)) {
                    rankingService.decreaseScore(oldOwner); // 옛날 주인 점수 깎기 😭
                    rankingService.increaseScore(newOwner); // 새 주인 점수 주기 😎
                }

                // DB 업데이트 (더티 체킹 or Setter)
                existingPixel.setColor(request.color());
                existingPixel.setUserId(newOwner);

            } else {
                // --- [CASE 2: 빈 땅인 경우] ---
                rankingService.increaseScore(newOwner); // 새 주인 점수 +1

                // DB 저장
                pixelRepository.save(new PixelEntity(x, y, request.color(), newOwner));
            }

            // 클라이언트 화면 업데이트용 좌표 계산
            double snappedLat = x * GRID_SIZE;
            double snappedLng = y * GRID_SIZE;

            PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), newOwner);

            // WebSocket 전송
            messagingTemplate.convertAndSend("/sub/pixel", snappedRequest);

        } catch (Exception e) {
            log.error("Kafka Consume Error", e);
        }
    }
}