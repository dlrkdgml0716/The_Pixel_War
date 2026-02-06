package com.thepixelwar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 해당 클래스 기준으로 하위 패키지에 @Service, @Component, @Controller 등을 모두 찾아 Spring Bean에 등록
// 내장 서버(Tomcat)을 실행시켜 서버를 킴
@SpringBootApplication
public class PixelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PixelServiceApplication.class, args); // Spring Boot를 실행시키는 명령어
    }
}