package com.thepixelwar.consumer;

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

    @InjectMocks
    private PixelConsumer pixelConsumer;

    @Test
    @DisplayName("Kafka 메시지 소비: 정상적인 메시지가 오면 DB에 저장하고 웹소켓으로 알린다")
    void consume_ShouldSaveAndBroadcast_WhenMessageReceived() throws Exception {
        // given
        String jsonMessage = "{\"lat\":37.5, \"lng\":127.5, \"color\":\"#FF0000\", \"userId\":\"User1\"}";
        PixelRequest request = new PixelRequest(37.5, 127.5, "#FF0000", "User1");

        // 1. JSON 파싱 모킹 (문자열 -> 객체 변환 성공 설정)
        when(objectMapper.readValue(jsonMessage, PixelRequest.class)).thenReturn(request);

        // 2. DB 조회 시 '없음(null)' 리턴 -> 새로 저장하는 로직 유도
        when(pixelRepository.findByCoords(anyInt(), anyInt())).thenReturn(null);

        // when
        pixelConsumer.consume(jsonMessage);

        // then (검증)
        // 1. DB 저장 메서드가 호출되었는가? (가장 중요!)
        verify(pixelRepository).save(any(PixelEntity.class));

        // 2. 다른 사용자들에게 웹소켓으로 소식을 전했는가?
        verify(messagingTemplate).convertAndSend(eq("/topic/pixel"), any(PixelRequest.class));
    }
}