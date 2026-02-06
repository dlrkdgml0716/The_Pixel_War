package com.thepixelwar.dto;

import com.thepixelwar.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

// record를 사용하면 모든 필드 매개변수의 getter, 생성자를 자동으로 만들어줌. 
public record CustomUserDetails(User user, Map<String, Object> attributes) implements OAuth2User {
                                                                    // 외부 로그인의 유저는 OAuth2User 타입으로 취급
    @Override
    public Map<String, Object> getAttributes() { return attributes; }

    @Override // 권한 확인
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(user.getRole()));
    }

    @Override
    public String getName() { return String.valueOf(attributes.get("id")); }

    public String getNickname() { return user.getNickname(); }
}
