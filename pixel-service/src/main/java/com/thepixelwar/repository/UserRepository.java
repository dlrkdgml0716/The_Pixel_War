package com.thepixelwar.repository;

import com.thepixelwar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 카카오(provider)의 회원번호(providerId)로 사용자 찾기, NullPointerException를 방지하기 위해 Optional<>사용
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    // providerId 단독으로 사용자 찾기 (길드 기능에서 사용)
    Optional<User> findByProviderId(String providerId);
}