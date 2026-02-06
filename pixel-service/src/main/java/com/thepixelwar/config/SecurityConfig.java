package com.thepixelwar.config;

import com.thepixelwar.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;


// Spring Security(spring-boot-starter-security) 브라우저 요청에서 오는 쿠키를 검사하여 로그인 유무확인 가능

@Configuration // 설정 파일임을 명시
@EnableWebSecurity // 요청이 controller에 가기전에 설정 파일에 조건에 부합하는지 확인하기 위함(Filter Chain)
@RequiredArgsConstructor // 다른 클래스의 객체를 사용함에 있어서 이미 생성된 객체를 공유하기 위함 -> 자동 의존성 주입
// final이 붙은 필드에 대해 생성자를 만들어 줌으로써 Spring이 관리하는 객체를 자동으로 주입받음
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean // @Service, @Controller 등과 달리 메서드에 붙이며 리턴되는 객체를 Spring Container에 등록
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // SecurityFilterChain은 해당 파일에 설정한 Filter들을 사슬 처럼 엮어 하나씩 검사하기 위한 타입
        http
                .csrf(AbstractHttpConfigurer::disable) // 개발 중이라 off, 실제 서비스를 진행할 때는 해킹 막기위해 on
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // permitAll() -> 누구에게나 허락, authenticated() -> 로그인 된 유저에게만 허락
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/style.css", "/script.js", "/favicon.ico").permitAll()
                        .requestMatchers("/api/ranks").permitAll()
                        .requestMatchers("/ws-pixel/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/pixels/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pixels/**").authenticated()

                        .anyRequest().authenticated() // 위에 통제하지 않은 요청은 로그인 된 유저에게만 허락
                )
                
                // 외부 서비스 로그인 설정
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService) // 카카오가 준 정보를 해당 객체로 넘김
                        )
                        .defaultSuccessUrl("/", true) // 로그인 절차가 성공하면 메인페이지로 이동
                );

        return http.build();
    }
}