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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

        // 1. [DB 조회] 해당 유저의 마지막 픽셀 설치 시간 조회
        PixelEntity lastPixel = pixelRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if (lastPixel != null && lastPixel.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(5))) {
            return "쿨타임 중";
        }

        int x = (int) Math.floor((request.lat() + EPSILON) / GRID_SIZE);
        int y = (int) Math.floor((request.lng() + EPSILON) / GRID_SIZE);

        try {
            // 2. [DB 저장]
            pixelRepository.save(new PixelEntity(x, y, request.color(), userId));
            return "성공";
        } catch (Exception e) {
            return "실패";
        }
    }

    /**
     * 🔥 [히트맵 추가] 2. 히트맵 데이터 조회 API용 메서드
     * 가장 핫한 좌표 상위 500개를 가져옵니다.
     */
    @Transactional(readOnly = true)
    public List<PixelRequest> getHotPixels() {
        // 현재 시간 기준 히트맵 키
        String heatmapKey = "heatmap:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd:HH"));

        // 점수가 높은 순서대로 상위 500개 가져오기 (Reverse Range)
        // Tuple은 {값(좌표), 점수(클릭수)}를 담고 있음
        Set<ZSetOperations.TypedTuple<String>> topPixels =
                redisTemplate.opsForZSet().reverseRangeWithScores(heatmapKey, 0, 500);

        List<PixelRequest> result = new ArrayList<>();

        if (topPixels != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topPixels) {
                String coord = tuple.getValue(); // "x:y"
                Double score = tuple.getScore(); // 클릭 횟수 (나중에 시각화 강도 조절용으로 쓸 수 있음)

                if (coord != null) {
                    String[] parts = coord.split(":");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);

                    // 좌표를 다시 위도/경도로 변환해서 반환 (색상은 붉은색 계열로 고정하거나 프론트에서 처리)
                    // 여기서는 프론트가 위치를 알 수 있게 좌표만 잘 넘겨줍니다.
                    // color 필드에 score(점수)를 넣어서 보내는 꼼수도 가능하지만, 일단 기본 구조 유지
                    result.add(new PixelRequest(
                            x * GRID_SIZE,
                            y * GRID_SIZE,
                            String.valueOf(score.intValue()), // 색상 필드에 '점수'를 문자열로 담아 보냄 (프론트에서 처리)
                            "SYSTEM"
                    ));
                }
            }
        }
        return result;
    }

    /**
     * 2. 영역 기반 조회 (최적화된 읽기)
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
     * 3. 단일 픽셀 색상 조회
     */
    @Transactional(readOnly = true)
    public String getPixelColor(int x, int y) {
        PixelEntity pixel = pixelRepository.findByCoords(x, y);
        return pixel != null ? pixel.getColor() : "#FFFFFF";
    }

    /**
     * 4. 전체 픽셀 조회
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