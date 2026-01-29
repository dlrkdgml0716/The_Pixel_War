package com.thepixelwar.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.repository.PixelRepository;
import com.thepixelwar.service.RankingService; // [NEW] Import
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixelConsumerTest {

    @Mock
    private PixelRepository pixelRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RankingService rankingService; // [NEW] 랭킹 서비스 Mock 추가

    @InjectMocks
    private PixelConsumer pixelConsumer;

    // 1. [기존] 빈 땅 먹기 테스트
    @Test
    @DisplayName("빈 땅 점령: DB에 저장하고, 새 주인에게 점수(+1)를 준다")
    void consume_ShouldSaveAndIncreaseScore_WhenPixelIsEmpty() throws Exception {
        // given
        String jsonMessage = "{\"lat\":37.5, \"lng\":127.5, \"color\":\"#FF0000\", \"userId\":\"NewUser\"}";
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000", "NewUser");

        when(objectMapper.readValue(jsonMessage, PixelRequest.class)).thenReturn(request);
        // 빈 땅(null) 리턴
        when(pixelRepository.findByCoords(anyInt(), anyInt())).thenReturn(null);

        // when
        pixelConsumer.consume(jsonMessage);

        // then
        verify(pixelRepository).save(any(PixelEntity.class)); // 저장 확인
        verify(rankingService).increaseScore("NewUser"); // [NEW] 점수 획득 확인
        verify(rankingService, never()).decreaseScore(anyString()); // 감점은 없어야 함
        verify(messagingTemplate).convertAndSend(eq("/topic/pixel"), any(PixelRequest.class));
    }

    // 2. [신규] 땅 뺏기 테스트
    @Test
    @DisplayName("땅 뺏기: 주인이 바뀌면 옛 주인 감점(-1), 새 주인 득점(+1) 되어야 한다")
    void consume_ShouldUpdateScores_WhenOwnershipChanges() throws Exception {
        // given
        String jsonMessage = "{\"lat\":37.5, \"lng\":127.5, \"color\":\"#0000FF\", \"userId\":\"NewWinner\"}";
        PixelRequest request = new PixelRequest(37.5, 127.5, "#0000FF", "NewWinner");

        // 이미 OldLoser가 차지하고 있는 땅 Mocking
        PixelEntity existingPixel = new PixelEntity(125000, 425000, "#FF0000", "OldLoser");

        when(objectMapper.readValue(jsonMessage, PixelRequest.class)).thenReturn(request);
        when(pixelRepository.findByCoords(anyInt(), anyInt())).thenReturn(existingPixel);

        // when
        pixelConsumer.consume(jsonMessage);

        // then
        verify(rankingService).decreaseScore("OldLoser"); // [검증] 옛 주인 감점
        verify(rankingService).increaseScore("NewWinner"); // [검증] 새 주인 득점
        verify(pixelRepository, never()).save(any(PixelEntity.class)); // 기존 엔티티 업데이트이므로 save 호출 안함 (Dirty Checking)
    }

    // 3. [신규] 내 땅 덧칠 테스트
    @Test
    @DisplayName("내 땅 덧칠: 주인이 같으면 점수 변동 로직이 실행되지 않아야 한다")
    void consume_ShouldNotChangeScore_WhenOwnerIsSame() throws Exception {
        // given
        String jsonMessage = "{\"lat\":37.5, \"lng\":127.5, \"color\":\"#00FF00\", \"userId\":\"SameUser\"}";
        PixelRequest request = new PixelRequest(37.5, 127.5, "#00FF00", "SameUser");

        // 이미 SameUser가 차지하고 있는 땅 Mocking
        PixelEntity existingPixel = new PixelEntity(125000, 425000, "#FF0000", "SameUser");

        when(objectMapper.readValue(jsonMessage, PixelRequest.class)).thenReturn(request);
        when(pixelRepository.findByCoords(anyInt(), anyInt())).thenReturn(existingPixel);

        // when
        pixelConsumer.consume(jsonMessage);

        // then
        // [검증] 랭킹 서비스가 아예 호출되지 않아야 함 (불필요한 Redis 부하 방지)
        verify(rankingService, never()).increaseScore(anyString());
        verify(rankingService, never()).decreaseScore(anyString());

        // 색깔은 바뀌었는지 확인 (Entity 내부 상태 변경)
        assert existingPixel.getColor().equals("#00FF00");
    }
}