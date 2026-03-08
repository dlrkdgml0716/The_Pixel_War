package com.thepixelwar.repository;

import com.thepixelwar.entity.PixelEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // 데이터를 저장하는 클래스로 bean에 등록
@RequiredArgsConstructor // final이 붙은 객체에 의존성 주입
public class PixelRepository {

    // JPA의 핵심 관리자를 주입
    private final EntityManager em; // (@PersistenceContext 어노테이션을 쓰기도 하지만 생성자 주입도 가능

    // 저장하기
    public void save(PixelEntity pixel) {
        em.persist(pixel); //  영속화
    }

    // 하나 찾기
    public PixelEntity findOne(Long id) {
        return em.find(PixelEntity.class, id);
    }

    // 전체 찾기 (JPQL이라는 JPA 전용 언어를 사용)
    public List<PixelEntity> findAll() {
        return em.createQuery("select p from PixelEntity p", PixelEntity.class)
                .getResultList();
    }

    public PixelEntity findByCoords(int x, int y) {
        return em.createQuery(
                        "select p from PixelEntity p where p.x = :x and p.y = :y", PixelEntity.class)
                .setParameter("x", x)
                .setParameter("y", y)
                .getResultStream().findFirst().orElse(null);
    }
    public List<PixelEntity> findByArea(int minX, int maxX, int minY, int maxY) {
        return em.createQuery(
                        "select p from PixelEntity p where p.x >= :minX and p.x <= :maxX and p.y >= :minY and p.y <= :maxY", PixelEntity.class)
                .setParameter("minX", minX)
                .setParameter("maxX", maxX)
                .setParameter("minY", minY)
                .setParameter("maxY", maxY)
                .getResultList();
    }
    
    //성능 테스트를 위해 추가된 함수
//    public PixelEntity findTopByUserIdOrderByCreatedAtDesc(String userId) {
//        return em.createQuery(
//                        "select p from PixelEntity p where p.userId = :userId order by p.id desc", PixelEntity.class)
//                .setParameter("userId", userId)
//                .setFirstResult(0) // 0번째부터
//                .setMaxResults(1)  // 딱 1개만 (가장 최근 것)
//                .getResultStream()
//                .findFirst()
//                .orElse(null);
//    }
}