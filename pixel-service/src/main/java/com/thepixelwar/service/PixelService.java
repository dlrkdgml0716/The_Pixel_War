package com.thepixelwar.service;

import com.thepixelwar.entity.PixelConstants;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
public class PixelService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final PixelRepository pixelRepository;
    private final RankingService rankingService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final long COOLDOWN_SECONDS = 5;

    @Transactional
    public String updatePixel(PixelRequest request, String userId) {
        String cooldownKey = "cooldown:" + userId;
        Long remainingTime = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);

        if (remainingTime != null && remainingTime > 0) {
            return "쿨타임이 " + remainingTime + "초 남았습니다!";
        }

        int x = (int) Math.floor((request.lat() + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int y = (int) Math.floor((request.lng() + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);

        double snappedLat = x * PixelConstants.GRID_SIZE;
        double snappedLng = y * PixelConstants.GRID_SIZE;

        PixelRequest snappedRequest = new PixelRequest(snappedLat, snappedLng, request.color(), userId);

        String lockKey = "pixel:lock:" + x + ":" + y;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                try {
                    // Redis 캐시 저장
                    redisTemplate.opsForValue().set("pixel:" + x + ":" + y, snappedRequest.color());

                    // 히트맵 갱신
                    String heatmapKey = "heatmap:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd:HH"));
                    redisTemplate.opsForZSet().incrementScore(heatmapKey, x + ":" + y, 1);
                    redisTemplate.expire(heatmapKey, 2, TimeUnit.HOURS);

                    // DB 저장 + 랭킹 갱신
                    PixelEntity existing = pixelRepository.findByCoords(x, y);
                    if (existing != null) {
                        if (!existing.getUserId().equals(userId)) {
                            rankingService.decreaseScore(existing.getUserId());
                            rankingService.increaseScore(userId);
                        }
                        existing.setColor(request.color());
                        existing.setUserId(userId);
                    } else {
                        rankingService.increaseScore(userId);
                        pixelRepository.save(new PixelEntity(x, y, request.color(), userId));
                    }

                    // WebSocket 브로드캐스트
                    messagingTemplate.convertAndSend("/sub/pixel", snappedRequest);

                    // 쿨타임 설정
                    redisTemplate.opsForValue().set(cooldownKey, "active", Duration.ofSeconds(COOLDOWN_SECONDS));

                    return "성공";
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

    @Transactional(readOnly = true)
    public List<PixelRequest> getHotPixels() {
        String heatmapKey = "heatmap:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd:HH"));
        Set<ZSetOperations.TypedTuple<String>> topPixels =
                redisTemplate.opsForZSet().reverseRangeWithScores(heatmapKey, 0, 500);

        List<PixelRequest> result = new ArrayList<>();
        if (topPixels != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topPixels) {
                String coord = tuple.getValue();
                Double score = tuple.getScore();
                if (coord != null) {
                    String[] parts = coord.split(":");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    result.add(new PixelRequest(
                            x * PixelConstants.GRID_SIZE,
                            y * PixelConstants.GRID_SIZE,
                            String.valueOf(score.intValue()),
                            "SYSTEM"
                    ));
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PixelRequest> getPixelsInBounds(double minLat, double maxLat, double minLng, double maxLng) {
        int minX = (int) Math.floor((minLat + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int maxX = (int) Math.ceil((maxLat + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int minY = (int) Math.floor((minLng + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int maxY = (int) Math.ceil((maxLng + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);

        return pixelRepository.findByArea(minX, maxX, minY, maxY).stream()
                .map(entity -> new PixelRequest(
                        entity.getX() * PixelConstants.GRID_SIZE,
                        entity.getY() * PixelConstants.GRID_SIZE,
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
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
                        entity.getX() * PixelConstants.GRID_SIZE,
                        entity.getY() * PixelConstants.GRID_SIZE,
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
    }
}
