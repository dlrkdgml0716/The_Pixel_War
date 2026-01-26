package com.thepixelwar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixelServiceTest {

    @Mock
    private PixelRepository pixelRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, String> valueOperations; // Redis 값 조작용 Mock
    @Mock
    private RLock rLock; // Redisson 락 Mock

    @InjectMocks
    private PixelService pixelService;

    // --- [기존] 쿼드트리 테스트 ---
    @Test
    @DisplayName("영역 기반 조회: 범위 내의 픽셀만 정상적으로 가져와야 한다")
    void getPixelsInBounds_ShouldReturnPixels_WhenInArea() {
        // given
        double minLat = 37.0; double maxLat = 38.0;
        double minLng = 127.0; double maxLng = 128.0;

        PixelEntity pixel1 = new PixelEntity(375000, 1275000, "#FF0000", "User1");
        PixelEntity pixel2 = new PixelEntity(376000, 1276000, "#00FF00", "User2");

        when(pixelRepository.findByArea(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(pixel1, pixel2));

        // when
        List<PixelRequest> result = pixelService.getPixelsInBounds(minLat, maxLat, minLng, maxLng);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).lat()).isEqualTo(37.5);
    }

    // --- [신규 추가] 픽셀 찍기 성공 테스트 ---
    @Test
    @DisplayName("픽셀 업데이트: 락을 획득하면 Redis저장 및 Kafka전송 후 '성공'을 반환한다")
    void updatePixel_ShouldReturnSuccess_WhenLockAcquired() throws Exception {
        // given
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000", "User1");

        // 1. Redisson 락 설정
        when(redissonClient.getLock(anyString())).thenReturn(rLock);

        // 2. 락 시도 (tryLock) -> true(성공) 리턴 설정
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // [★ 핵심 수정] "이 락은 현재 내가 잡고 있다"고 알려줘야 unlock()이 실행됨
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // 3. Redis 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 4. JSON 변환 설정
        when(objectMapper.writeValueAsString(any())).thenReturn("jsonString");

        // when
        String result = pixelService.updatePixel(request);

        // then
        assertThat(result).isEqualTo("성공");

        // verify
        verify(valueOperations).set(anyString(), eq("#FF0000"));
        verify(kafkaTemplate).send(eq("pixel-updates"), anyString());
        verify(rLock).unlock(); // 이제 이 부분이 통과될 것입니다!
    }

    // --- [신규 추가] 락 획득 실패 테스트 ---
    @Test
    @DisplayName("픽셀 업데이트: 락 획득에 실패하면 로직을 수행하지 않고 '실패'를 반환한다")
    void updatePixel_ShouldReturnFail_WhenLockFailed() throws InterruptedException {
        // given
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000", "User1");

        // 락 설정
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        // [핵심] 락 시도 시 false(실패/누가 이미 점유중) 리턴
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        // when
        String result = pixelService.updatePixel(request);

        // then
        assertThat(result).isEqualTo("실패");

        // [중요 검증] 로직이 실행되지 않아야 함
        verify(redisTemplate, never()).opsForValue(); // Redis 접근 안 했어야 함
        verify(kafkaTemplate, never()).send(anyString(), anyString()); // Kafka 전송 안 했어야 함
    }
}