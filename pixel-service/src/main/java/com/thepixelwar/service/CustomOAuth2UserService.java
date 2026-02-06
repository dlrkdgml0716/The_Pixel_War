package com.thepixelwar.service;

import com.thepixelwar.dto.CustomUserDetails;
import com.thepixelwar.entity.User;
import com.thepixelwar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j // Lombok 라이브러리에서 제공하는 어노테이션. 로그를 남기기 위해 사용
@Service // 해당 클래스의 객체를 비즈니스 로직으로서 Spring Container에 등록
@RequiredArgsConstructor // final이 붙은 필드에 대해 생성자를 만들어 줌으로써 Spring이 관리하는 객체를 자동으로 주입받음
                                            // Spring Security가 외부 로그인 위해 만든 인터페이스
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    @Transactional // ACID원칙을 지원하는 어노테이션
    // loadUser는 OAuth2UserService 인터페이스에 정의된 함수 -> 사용자를 시스템으로 불러옴
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService(); // 기본적으로 정의된 함수 -> 해당 서비스 서버와 통신해 데이터를 가져옴
        OAuth2User oAuth2User = delegate.loadUser(userRequest); // 서버에서 유저에 관한 데이터를 받아옴

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // 어떤 외부 서비스 로그인인지 이름 받아옴

        Map<String, Object> attributes = oAuth2User.getAttributes(); // 위에서 가져온 유저 데이터를 Map형태로 변환

        User user = saveOrUpdate(registrationId, attributes);

        return new CustomUserDetails(user, attributes);
    }

    private User saveOrUpdate(String registrationId, Map<String, Object> attributes) {
        String providerId = String.valueOf(attributes.get("id"));

        String tempNickname = "Unknown";

        if (attributes.containsKey("kakao_account")) { // 계정의 정보가 담긴 "kakao_account" key가 있는지 확인
            // 있다면 kakaoAccount에 저장
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            // kakaoAccount에 "profile" key가 있는지 확인
            if (kakaoAccount != null && kakaoAccount.containsKey("profile")) {
                // 있다면 profile에 저장
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                // profile에 "nickname" key가 있는지 확인
                if (profile != null && profile.containsKey("nickname")) {
                    tempNickname = (String) profile.get("nickname"); // 있다면 nickname에 저장
                }
            }
        }

        String nickname = tempNickname;

        User user = userRepository.findByProviderAndProviderId(registrationId, providerId) // userRepository가 DB에서 유저를 찾아옴
                .map(entity -> entity.update(nickname)) // 찾아온 Entity의 닉네임 수정
                .orElse(User.builder() // 없으면 새로 만듦
                        .provider(registrationId)
                        .providerId(providerId)
                        .nickname(nickname)
                        .role("ROLE_USER")
                        .build());

        return userRepository.save(user); // userRepository가 바뀐 내용을 DB에 저장
    }
}