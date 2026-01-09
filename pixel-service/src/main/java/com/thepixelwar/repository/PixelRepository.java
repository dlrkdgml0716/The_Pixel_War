package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PixelRepository extends JpaRepository<PixelEntity, Long> {
    // 기본적인 저장(save), 조회 기능이 자동으로 포함됩니다.
}