package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PixelRepository extends JpaRepository<PixelEntity, Long> {

    @Query("select p from PixelEntity p where p.x = :x and p.y = :y")
    PixelEntity findByCoords(@Param("x") int x, @Param("y") int y);

    @Query("select p from PixelEntity p join fetch p.user where p.x >= :minX and p.x <= :maxX and p.y >= :minY and p.y <= :maxY")
    List<PixelEntity> findByArea(@Param("minX") int minX, @Param("maxX") int maxX,
                                  @Param("minY") int minY, @Param("maxY") int maxY);

    @Query("select p from PixelEntity p join fetch p.user")
    List<PixelEntity> findAllWithUser();

    @Query("select p.user.providerId, count(p) from PixelEntity p group by p.user.providerId")
    List<Object[]> countPixelsByUser();
}