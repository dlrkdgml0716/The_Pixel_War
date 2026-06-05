package com.thepixelwar.service;

import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.dto.PixelResponse;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.entity.User;
import com.thepixelwar.repository.PixelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixelServiceTest {

    @Mock private PixelRepository pixelRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RankingService rankingService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private RLock rLock;

    @InjectMocks
    private PixelService pixelService;

    private User buildUser(String providerId, String nickname) {
        return User.builder()
                .provider("kakao").providerId(providerId).nickname(nickname).role("ROLE_USER")
                .build();
    }

    @Test
    @DisplayName("영역 기반 조회: 범위 내의 픽셀만 정상적으로 가져와야 한다")
    void getPixelsInBounds_ShouldReturnPixels_WhenInArea() {
        User user1 = buildUser("p1", "User1");
        User user2 = buildUser("p2", "User2");
        PixelEntity pixel1 = new PixelEntity(125000, 425000, "#FF0000", user1); // lat=37.5, lng=127.5
        PixelEntity pixel2 = new PixelEntity(125500, 425500, "#00FF00", user2); // lat=37.65, lng=127.65

        when(pixelRepository.findByArea(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(pixel1, pixel2));

        List<PixelResponse> result = pixelService.getPixelsInBounds(37.0, 38.0, 127.0, 128.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).lat()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("픽셀 업데이트: 락을 획득하면 Redis 저장, DB 저장, WebSocket 브로드캐스트 후 '성공'을 반환한다")
    void updatePixel_ShouldReturnSuccess_WhenLockAcquired() throws Exception {
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000");
        User user = buildUser("User1", "User1");

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(pixelRepository.findByXAndY(anyInt(), anyInt())).thenReturn(Optional.empty()); // 빈 땅
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        String result = pixelService.updatePixel(request, user);

        assertThat(result).isEqualTo("성공");
        verify(valueOperations).set(anyString(), eq("#FF0000"));
        verify(pixelRepository).save(any(PixelEntity.class));
        verify(rankingService).increaseScore("User1");
        verify(messagingTemplate).convertAndSend(eq("/sub/pixel"), any(PixelResponse.class));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("픽셀 업데이트: 락 획득 실패 시 로직을 수행하지 않고 '다른 사람이 작업 중입니다.'를 반환한다")
    void updatePixel_ShouldReturnFail_WhenLockFailed() throws InterruptedException {
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000");
        User user = buildUser("User1", "User1");

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        String result = pixelService.updatePixel(request, user);

        assertThat(result).isEqualTo("다른 사람이 작업 중입니다.");
        verify(redisTemplate, never()).opsForValue();
        verify(pixelRepository, never()).findByXAndY(anyInt(), anyInt());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("픽셀 업데이트: 남의 땅 뺏기 시 기존 주인 감점, 새 주인 득점이 되어야 한다")
    void updatePixel_ShouldUpdateRanking_WhenOwnerChanges() throws InterruptedException {
        PixelRequest request = new PixelRequest(37.5, 127.5, "#0000FF");
        User newUser = buildUser("NewUser", "NewUser");
        User oldUser = buildUser("OldUser", "OldUser");
        PixelEntity existing = new PixelEntity(375000, 1275000, "#FF0000", oldUser);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(pixelRepository.findByXAndY(anyInt(), anyInt())).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        String result = pixelService.updatePixel(request, newUser);

        assertThat(result).isEqualTo("성공");
        verify(rankingService).decreaseScore("OldUser");
        verify(rankingService).increaseScore("NewUser");
        verify(pixelRepository, never()).save(any()); // 기존 엔티티 수정이므로 save 미호출
    }
}