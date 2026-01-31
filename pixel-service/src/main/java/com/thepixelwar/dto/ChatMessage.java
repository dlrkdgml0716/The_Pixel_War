package com.thepixelwar.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessage {
    // 메시지 타입: 입장(ENTER), 대화(TALK)
    public enum MessageType {
        ENTER, TALK
    }

    private MessageType type; // 메시지 타입
    private String roomId;    // 방 번호 (지금은 예: "1")
    private String sender;    // 보낸 사람 (닉네임)
    private String message;   // 내용
}