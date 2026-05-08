package com.thepixelwar.repository;

import com.thepixelwar.entity.GuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GuildRepository extends JpaRepository<GuildEntity, Long> {
    boolean existsByName(String name);

    // members를 한 번의 JOIN으로 함께 조회 (N+1 방지)
    @Query("SELECT DISTINCT g FROM GuildEntity g LEFT JOIN FETCH g.members")
    List<GuildEntity> findAllWithMembers();
}