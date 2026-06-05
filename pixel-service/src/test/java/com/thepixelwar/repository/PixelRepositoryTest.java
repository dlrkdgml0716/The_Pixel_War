package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import com.thepixelwar.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PixelRepositoryTest {

    @Autowired
    private PixelRepository pixelRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("영역 조회: 지정된 범위(min, max) 안에 있는 픽셀만 정확히 조회되어야 한다")
    void findByArea_ShouldReturnOnlyPixelsInBounds() {
        // given
        User user1 = userRepository.save(User.builder().provider("kakao").providerId("p1").nickname("user1").role("ROLE_USER").build());
        User user2 = userRepository.save(User.builder().provider("kakao").providerId("p2").nickname("user2").role("ROLE_USER").build());
        User user3 = userRepository.save(User.builder().provider("kakao").providerId("p3").nickname("user3").role("ROLE_USER").build());
        User user4 = userRepository.save(User.builder().provider("kakao").providerId("p4").nickname("user4").role("ROLE_USER").build());

        // 범위 안에 들어갈 픽셀 (Target)
        pixelRepository.save(new PixelEntity(100, 100, "red", user1));
        pixelRepository.save(new PixelEntity(150, 150, "blue", user2));

        // 범위 밖에 있는 픽셀 (Noise) -> 조회되면 안 됨!
        pixelRepository.save(new PixelEntity(50, 50, "green", user3));   // 너무 작음
        pixelRepository.save(new PixelEntity(200, 200, "black", user4)); // 너무 큼

        // when (범위: x(80~160), y(80~160))
        List<PixelEntity> result = pixelRepository.findByArea(80, 160, 80, 160);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(p -> p.getUser().getNickname())
                .containsExactlyInAnyOrder("user1", "user2");
    }
}
