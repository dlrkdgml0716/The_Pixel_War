package com.thepixelwar.controller;

import com.thepixelwar.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;

    // 클라이언트가 "/pub/chat/message"로 보내면 이 메서드가 실행됨
    @MessageMapping("/chat/message")
    public void message(ChatMessage message) {

        // 1. 처음 들어온 유저라면 환영 인사 추가
        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        }

        // 2. "/sub/chat/room/{roomId}"를 구독(듣고) 있는 사람들에게 메시지 전달
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}