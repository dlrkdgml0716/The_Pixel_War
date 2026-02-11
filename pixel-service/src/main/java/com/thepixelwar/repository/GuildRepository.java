package com.thepixelwar.repository;

import com.thepixelwar.entity.GuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GuildRepository extends JpaRepository<GuildEntity, Long> {
    Optional<GuildEntity> findByName(String name);
    boolean existsByName(String name);
}