package com.thepixelwar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.CustomUserDetails;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.entity.User;
import com.thepixelwar.service.PixelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PixelController.class)
class PixelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PixelService pixelService;

    @Autowired
    private ObjectMapper objectMapper;

    private OAuth2AuthenticationToken mockAuth() {
        User user = User.builder()
                .provider("kakao").providerId("123").nickname("testUser").role("ROLE_USER")
                .build();
        CustomUserDetails principal = new CustomUserDetails(user, Map.of("id", "123"));
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }

    @Test
    @DisplayName("픽셀 찍기 API: 정상 요청 시 200 OK와 '성공' 문자열을 반환한다")
    void updatePixel_ShouldReturnOk() throws Exception {
        PixelRequest request = new PixelRequest(37.5, 127.5, "red", "user1");
        given(pixelService.updatePixel(any(PixelRequest.class), anyString())).willReturn("성공");

        mockMvc.perform(post("/api/pixels")
                        .with(authentication(mockAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("성공"));
    }

    @Test
    @DisplayName("영역 조회 API: 파라미터(minLat 등)를 보내면 리스트를 JSON으로 반환한다")
    void getPixels_ShouldReturnList() throws Exception {
        PixelRequest pixel1 = new PixelRequest(37.5, 127.5, "red", "user1");
        given(pixelService.getPixelsInBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(pixel1));

        mockMvc.perform(get("/api/pixels")
                        .param("minLat", "37.0")
                        .param("maxLat", "38.0")
                        .param("minLng", "127.0")
                        .param("maxLng", "128.0"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user1"))
                .andExpect(jsonPath("$[0].color").value("red"));
    }
}