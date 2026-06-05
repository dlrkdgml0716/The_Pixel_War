package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PixelRepository extends JpaRepository<PixelEntity, Long> {
                                                    // 관리할 엔티티 클래스, 기본 키 타입
    Optional<PixelEntity> findByXAndY(int x, int y);

    @Query("select p from PixelEntity p join fetch p.user where p.x >= :minX and p.x <= :maxX and p.y >= :minY and p.y <= :maxY")
    List<PixelEntity> findByArea(@Param("minX") int minX, @Param("maxX") int maxX,
                                  @Param("minY") int minY, @Param("maxY") int maxY);

    @Query("select p from PixelEntity p join fetch p.user")
    List<PixelEntity> findAllWithUser();

    @Query("select p.user.providerId, count(p) from PixelEntity p group by p.user.providerId")
    List<Object[]> countPixelsByUser();
}

// 2개 이상의 변수를 전달할 때 @Param 명시, 안하면 Parameter를 찾을 수 없다는 오류 발생