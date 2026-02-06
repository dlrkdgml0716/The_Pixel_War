package com.thepixelwar.dto;

import lombok.*;

@Getter // Lombok 라이브러리에서 지원하는 어노테이션. 모든 필드에 대한 get함수 자동 생성
@AllArgsConstructor // 모든 필드를 포함하는 생성자를 자동으로 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자를 자동으로 생성
public class UserResponse {
    private String nickname;
}
