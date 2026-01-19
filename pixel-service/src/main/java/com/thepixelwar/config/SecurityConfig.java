package com.thepixelwar.config;

import com.thepixelwar.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable) // 일반 로그인 끄기

                .authorizeHttpRequests(auth -> auth
                        // 누구나 접속 가능한 주소
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/api/pixels/**", "/ws-pixel/**").permitAll()
                        // 나머지는 로그인해야 접속 가능
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        // 로그인이 성공하면 유저 정보를 처리할 서비스 등록
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        // 로그인 성공 시 메인 페이지로 이동
                        .defaultSuccessUrl("/", true)
                );

        return http.build();
    }
}