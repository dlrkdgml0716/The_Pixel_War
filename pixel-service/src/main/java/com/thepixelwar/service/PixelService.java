package com.thepixelwar.service;

import com.thepixelwar.entity.PixelConstants;
import com.thepixelwar.dto.HotPixelResponse;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.dto.PixelResponse;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.entity.User;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    private static final long COOLDOWN_SECONDS = 5;

    public String updatePixel(PixelRequest request, User user) {
        String cooldownKey = "cooldown:" + user.getProviderId();
        Long remainingTime = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);

        if (remainingTime != null && remainingTime > 0) {
            return "쿨타임이 " + remainingTime + "초 남았습니다!";
        }

        int x = (int) Math.floor((request.lat() + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int y = (int) Math.floor((request.lng() + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);

        double snappedLat = x * PixelConstants.GRID_SIZE;
        double snappedLng = y * PixelConstants.GRID_SIZE;

        // WebSocket 브로드캐스트용 — 닉네임을 표시명으로 사용
        PixelResponse pixelResponse = new PixelResponse(snappedLat, snappedLng, request.color(), user.getNickname());

        String lockKey = "pixel:lock:" + x + ":" + y;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        List<Runnable> rankingOps = new ArrayList<>();

                        // DB 저장 — 랭킹 변경 내용은 rankingOps에 적재 후 afterCommit에서 실행
                        pixelRepository.findByXAndY(x, y).ifPresentOrElse(
                            existing -> {
                                if (!existing.getUser().getProviderId().equals(user.getProviderId())) {
                                    String prevOwner = existing.getUser().getProviderId();
                                    rankingOps.add(() -> rankingService.decreaseScore(prevOwner));
                                    rankingOps.add(() -> rankingService.increaseScore(user.getProviderId()));
                                }
                                existing.setColor(request.color());
                                existing.setUser(user);
                            },
                            () -> {
                                rankingOps.add(() -> rankingService.increaseScore(user.getProviderId()));
                                pixelRepository.save(new PixelEntity(x, y, request.color(), user));
                            }
                        );

                        // DB 커밋 후 Redis 연산 — 커밋 실패 시 실행되지 않음
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                rankingOps.forEach(Runnable::run);
                                String heatmapKey = "heatmap:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd:HH"));
                                redisTemplate.opsForZSet().incrementScore(heatmapKey, x + ":" + y, 1);
                                redisTemplate.expire(heatmapKey, 2, TimeUnit.HOURS);
                                redisTemplate.opsForValue().set(cooldownKey, "active", Duration.ofSeconds(COOLDOWN_SECONDS));
                            }
                        });
                    }); // ← 여기서 트랜잭션 커밋, afterCommit 순으로 실행

                    // WebSocket 브로드캐스트는 커밋 후 실행
                    messagingTemplate.convertAndSend("/sub/pixel", pixelResponse);

                    return "성공";
                } finally {
                    // 커밋 완료 후에 락 해제 → 유저 B는 반드시 커밋된 데이터를 읽음
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
    public List<HotPixelResponse> getHotPixels() {
        String heatmapKey = "heatmap:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd:HH"));
        Set<ZSetOperations.TypedTuple<String>> topPixels =
                redisTemplate.opsForZSet().reverseRangeWithScores(heatmapKey, 0, 500);

        List<HotPixelResponse> result = new ArrayList<>();
        if (topPixels != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topPixels) {
                String coord = tuple.getValue();
                Double score = tuple.getScore();
                if (coord != null && score != null) {
                    String[] parts = coord.split(":");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    result.add(new HotPixelResponse(
                            x * PixelConstants.GRID_SIZE,
                            y * PixelConstants.GRID_SIZE,
                            score.intValue()
                    ));
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PixelResponse> getPixelsInBounds(double minLat, double maxLat, double minLng, double maxLng) {
        int minX = (int) Math.floor((minLat + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int maxX = (int) Math.ceil((maxLat + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int minY = (int) Math.floor((minLng + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);
        int maxY = (int) Math.ceil((maxLng + PixelConstants.EPSILON) / PixelConstants.GRID_SIZE);

        return pixelRepository.findByArea(minX, maxX, minY, maxY).stream()
                .map(entity -> new PixelResponse(
                        entity.getX() * PixelConstants.GRID_SIZE,
                        entity.getY() * PixelConstants.GRID_SIZE,
                        entity.getColor(),
                        entity.getUser().getNickname()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getPixelColor(int x, int y) {
        return pixelRepository.findByXAndY(x, y)
                .map(PixelEntity::getColor)
                .orElse("#FFFFFF");
    }

    @Transactional(readOnly = true)
    public List<PixelResponse> getAllPixels() {
        return pixelRepository.findAllWithUser().stream()
                .map(entity -> new PixelResponse(
                        entity.getX() * PixelConstants.GRID_SIZE,
                        entity.getY() * PixelConstants.GRID_SIZE,
                        entity.getColor(),
                        entity.getUser().getNickname()))
                .toList();
    }
}