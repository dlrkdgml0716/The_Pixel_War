package com.thepixelwar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.service.PixelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PixelController.class) // 컨트롤러만 집중 테스트
class PixelControllerTest {

    @Autowired
    private MockMvc mockMvc; // 가짜 브라우저 역할

    @MockBean
    private PixelService pixelService; // 서비스는 가짜(Mock)로 대체

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("픽셀 찍기 API: 정상 요청 시 200 OK와 '성공' 문자열을 반환한다")
    @WithMockUser // 로그인된 가짜 유저가 있다고 가정
    void updatePixel_ShouldReturnOk() throws Exception {
        // given
        PixelRequest request = new PixelRequest(37.5, 127.5, "red", "user1");
        given(pixelService.updatePixel(any(PixelRequest.class))).willReturn("성공");

        // when & then
        mockMvc.perform(post("/api/pixels")
                        .with(csrf()) // Spring Security 사용 시 CSRF 토큰 필요
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print()) // 콘솔에 요청/응답 내용 출력
                .andExpect(status().isOk())
                .andExpect(content().string("성공"));
    }

    @Test
    @DisplayName("영역 조회 API: 파라미터(minLat 등)를 보내면 리스트를 JSON으로 반환한다")
    @WithMockUser
    void getPixels_ShouldReturnList() throws Exception {
        // given
        PixelRequest pixel1 = new PixelRequest(37.5, 127.5, "red", "user1");
        given(pixelService.getPixelsInBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(pixel1));

        // when & then
        // /api/pixels?minLat=37.0&maxLat=38.0&minLng=127.0&maxLng=128.0
        mockMvc.perform(get("/api/pixels")
                        .param("minLat", "37.0")
                        .param("maxLat", "38.0")
                        .param("minLng", "127.0")
                        .param("maxLng", "128.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user1")) // JSON 내용 검증
                .andExpect(jsonPath("$[0].color").value("red"));
    }
}