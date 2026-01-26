package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest // JPA 관련 설정만 로드해서 가벼운 DB 테스트 진행
@Import(PixelRepository.class)
class PixelRepositoryTest {

    @Autowired
    private PixelRepository pixelRepository;

    @Test
    @DisplayName("영역 조회: 지정된 범위(min, max) 안에 있는 픽셀만 정확히 조회되어야 한다")
    void findByArea_ShouldReturnOnlyPixelsInBounds() {
        // given (데이터 준비)
        // 범위 안에 들어갈 픽셀 (Target)
        PixelEntity inside1 = new PixelEntity(100, 100, "red", "user1");
        PixelEntity inside2 = new PixelEntity(150, 150, "blue", "user2");

        // 범위 밖에 있는 픽셀 (Noise) -> 조회되면 안 됨!
        PixelEntity outside1 = new PixelEntity(50, 50, "green", "user3");   // 너무 작음
        PixelEntity outside2 = new PixelEntity(200, 200, "black", "user4"); // 너무 큼

        pixelRepository.save(inside1);
        pixelRepository.save(inside2);
        pixelRepository.save(outside1);
        pixelRepository.save(outside2);

        // when (조회 실행)
        // 범위: x(80~160), y(80~160)
        List<PixelEntity> result = pixelRepository.findByArea(80, 160, 80, 160);

        // then (검증)
        // 1. 개수는 딱 2개여야 함
        assertThat(result).hasSize(2);

        // 2. 포함된 녀석들이 진짜 inside1, inside2인지 확인
        assertThat(result).extracting("userId")
                .containsExactlyInAnyOrder("user1", "user2");

        // 3. 밖의 녀석들은 없어야 함
        assertThat(result).extracting("userId")
                .doesNotContain("user3", "user4");
    }
}