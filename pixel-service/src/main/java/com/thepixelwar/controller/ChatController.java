package com.thepixelwar.controller;

import com.thepixelwar.dto.ChatMessage;
import com.thepixelwar.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;

    @MessageMapping("/chat/message")
    public void message(ChatMessage message, @AuthenticationPrincipal CustomUserDetails principal) {
        // 클라이언트가 보낸 sender 대신 서버가 인증된 닉네임으로 덮어씀 (닉네임 위조 방지)
        if (principal != null) {
            message.setSender(principal.getNickname());
        }

        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        }

        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}
