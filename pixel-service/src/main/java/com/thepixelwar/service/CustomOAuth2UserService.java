package com.thepixelwar.service;

import com.thepixelwar.entity.User;
import com.thepixelwar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 카카오에서 유저 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 2. 서비스 구분 (kakao)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 3. 키값 필드명 (카카오는 "id")
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        // 4. 유저 정보 추출
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 5. DB 저장/업데이트 로직 호출
        User user = saveOrUpdate(registrationId, attributes);

        // 6. SecurityContext에 저장할 객체 반환
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole())),
                attributes,
                userNameAttributeName
        );
    }

    private User saveOrUpdate(String registrationId, Map<String, Object> attributes) {
        String providerId = String.valueOf(attributes.get("id"));

        // 1. 임시 변수로 닉네임 추출
        String tempNickname = "Unknown";

        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            if (kakaoAccount != null) {
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                if (profile != null && profile.containsKey("nickname")) {
                    tempNickname = (String) profile.get("nickname");
                }
            }
        }

        // [중요] 람다식에서 쓰기 위해 'final' 성격의 변수에 최종 값을 담습니다.
        String nickname = tempNickname;

        User user = userRepository.findByProviderAndProviderId(registrationId, providerId)
                .map(entity -> entity.update(nickname)) // 이제 에러가 사라집니다!
                .orElse(User.builder()
                        .provider(registrationId)
                        .providerId(providerId)
                        .nickname(nickname)
                        .role("ROLE_USER")
                        .build());

        return userRepository.save(user);
    }
}