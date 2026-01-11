package com.thepixelwar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.repository.PixelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PixelService {

    private final RedissonClient redissonClient; // 동시성 문제를 해결하기 위한 reddison 객체 -> Lock을 관리
    private final StringRedisTemplate redisTemplate; // 캐시메모리 -> 업데이트된 픽셀을 빠르게 적용시키기 위함
    private final KafkaTemplate<String, String> kafkaTemplate; // 메세지 브로커 -> 업데이트된 픽셀을 db에 기록하기 위함
    private final ObjectMapper objectMapper; // 직열화(java객체 -> Json문자열), 역직열화하기 위한 Jackson 라이브러리의 도구
    private final PixelRepository pixelRepository;

    public String updatePixel(PixelRequest request) {
        String cooldownKey = "user:cooldown:" + request.userId();
        // setIfAbsent는 "데이터가 없을 때만 저장해라"라는 뜻 (있으면 false 반환)
        Boolean canUpdate = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "locked", 1, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(canUpdate)) {
            return "아직 쿨타임 중입니다! 조금만 참으쇼 (10초)";
        }

        String lockKey = "pixel:lock:" + request.lat() + ":" + request.lng();
        RLock lock = redissonClient.getLock(lockKey); // Redisson을 통해 Redis 기반의 분산 자물쇠 가져옴

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) { // lock 얻기 위한 시도, 같은 좌표가 lock 걸려있다면 기다림
                try {
                    // Redis에 실시간 저장 (빠른 응답용)
                    String pixelKey = "pixel:" + request.lat() + ":" + request.lng();
                    redisTemplate.opsForValue().set(pixelKey, request.color()); // redis에 좌표키와 색상 저장 -> 빠른 응답를 위함

                    // Kafka로 메시지 전송 (DB 저장용 - 비동기 처리)
                    String message = objectMapper.writeValueAsString(request); // 직열화
                    kafkaTemplate.send("pixel-updates", message); // pixel-updates(topic), kafka에 메세지 전송

                    log.info("픽셀 저장 및 Kafka 전송 완료: {} -> {}", pixelKey, request.color());
                    return "픽셀 업데이트 요청이 접수되었습니다: (" + request.lat() + ", " + request.lng() + ")";
                } catch (JsonProcessingException e) {
                    log.error("메시지 변환 중 오류 발생", e);
                } finally {
                    // 내가 락을 잡고 있고, 아직 잠겨 있는 경우에만 해제 시도
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("락 획득 중 오류 발생", e);
        }

        return "시스템이 바쁩니다. 잠시 후 다시 시도해주세요.";
    }

    @Transactional(readOnly = true) // 조회 전용이므로 성능 최적화를 위해 붙여줍니다.
    public String getPixelColor(int x, int y) {
        String pixelKey = "pixel:" + x + ":" + y;

        // 1. Redis(캐시)에서 먼저 찾아봅니다.
        String cachedColor = redisTemplate.opsForValue().get(pixelKey);
        if (cachedColor != null) {
            log.info("Redis 캐시 적중! (Cache Hit): {},{}", x, y);
            return cachedColor;
        }

        // 2. Redis에 없다면 DB(JPA)에서 찾습니다.
        log.info("Redis에 없음. DB에서 조회합니다. (Cache Miss): {},{}", x, y);

        // 조회를 위해 Repository의 findOne을 호출하려면 좌표 기반 조회가 필요하겠네요!
        // (이 부분은 뒤에서 Repository를 조금 수정해야 합니다.)
        return "#FFFFFF"; // 임시 반환값
    }

    @Transactional(readOnly = true)
    public List<PixelRequest> getAllPixels() {
        log.info("전체 픽셀 판 데이터를 조회합니다.");

        // DB에서 모든 엔티티를 가져와서 사용자가 보기 편한 DTO(Record) 리스트로 변환합니다.
        return pixelRepository.findAll().stream()
                .map(entity -> new PixelRequest(
                        entity.getLat(),
                        entity.getLng(),
                        entity.getColor(),
                        entity.getUserId()))
                .toList();
    }
}