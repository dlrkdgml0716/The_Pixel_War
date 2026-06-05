# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# 로컬 실행 (Java 17, MySQL, Redis 필요)
./gradlew bootRun

# 전체 테스트
./gradlew test

# 특정 테스트 클래스 실행
./gradlew :pixel-service:test --tests "com.thepixelwar.service.PixelServiceTest"
./gradlew :pixel-service:test --tests "com.thepixelwar.service.RankingConcurrencyTest"

# Docker Compose로 전체 스택 실행 (앱 + MySQL + Redis)
docker-compose up --build
```

`RankingConcurrencyTest`는 `@SpringBootTest`이므로 실제 Redis가 필요하다. Docker Compose 환경이 아닌 로컬에서는 Redis를 직접 실행해야 통과한다. Redis 미실행으로 인한 실패를 코드 결함으로 오인하지 말 것.

## 작업 방식 (반드시 준수)

모든 비단순 작업은 **계획 → 쪼개기 → 검증 → 확인**의 사이클을 따른다.
큰 작업은 이 사이클을 단위마다 반복하며, 단계를 건너뛰지 않는다.

### 계획

3개 이상 파일을 건드리거나 동시성·인증·트랜잭션 로직을 변경하는 작업은, 코드 수정 전에 먼저 계획을 제시하고 승인을 받는다.

계획에는 다음을 명시한다:
1. 수정·생성할 파일
2. 각 파일에서 바꿀 메서드/클래스
3. 작업 순서
4. 불확실한 가정

한 줄짜리 사소한 수정은 이 절차를 생략한다.

### 쪼개기

큰 작업은 독립적으로 검증 가능한 단위로 나누고, 각 단위가 끝날 때마다 멈춰 확인을 받는다. 승인된 계획에서 벗어나는 상황이 생기면 임의로 진행하지 말고 멈춰서 다시 계획을 제시한다.

### 검증

- 코드 변경 후 관련 테스트를 직접 실행해 통과를 확인한다. 실패하면 통과할 때까지 수정한다.
- 동시성 관련 변경 시 반드시 `RankingConcurrencyTest`를 실행한다.
- 변경이 아래 **불변식**을 위반하지 않는지 점검하고 보고한다.

## 불변식 (절대 위반 금지)

- **인증**: 사용자 식별자는 절대 클라이언트 요청 값에서 받지 않는다. 항상 `SecurityContext`의 `providerId`를 사용한다. 요청 바디·파라미터로 `providerId`를 받는 코드를 추가하지 말 것.
- **트랜잭션**: `PixelService.updatePixel`의 `TransactionTemplate`을 `@Transactional`로 바꾸지 않는다. 락 범위 안에서 트랜잭션 경계를 제어하기 위한 의도적 선택이다.
- **락-커밋 순서**: "트랜잭션 커밋 → 락 해제", "브로드캐스트는 커밋 후" 순서를 반드시 유지한다. 이 순서가 바뀌면 동시성 보장이 깨진다.
- **좌표**: 저장(DB·Redis)에는 항상 정수 `(x, y)`를 쓰고, 위경도 역변환(`x * GRID_SIZE`)은 클라이언트 응답 시점에만 한다.
- **비밀 정보**: `application-local.yml`과 자격증명을 커밋하지 않는다.

## 아키텍처

단일 Spring Boot 모듈(`pixel-service`) 구조이며, 패키지는 `com.thepixelwar` 하위에 `config / controller / dto / entity / repository / service`로 구분된다.

### 좌표 체계

위경도를 `GRID_SIZE = 0.0003도` 단위로 스냅하여 정수 `(x, y)`로 변환해 저장한다 (`PixelConstants`). Redis 키와 DB 컬럼 모두 이 정수 좌표를 사용한다. 클라이언트에 돌려줄 때만 `x * GRID_SIZE`로 역변환한다.

### 동시성 제어 — 이중 방어

`PixelService.updatePixel`의 핵심 흐름:

1. **1차: 쿨다운** — `cooldown:{providerId}` Redis 키 TTL(5초) 확인. 남아있으면 즉시 거부.
2. **2차: 분산 락** — `pixel:lock:{x}:{y}` Redisson 락 (wait 5s / lease 2s). 락 내부에서 `TransactionTemplate`으로 트랜잭션을 명시적으로 열고, **트랜잭션 커밋 후 락 해제** 순서를 보장한다. WebSocket 브로드캐스트는 커밋이 끝난 뒤(`transactionTemplate.executeWithoutResult` 반환 후) 실행한다.

`@Transactional` 대신 `TransactionTemplate`을 쓰는 이유: 락 범위 안에서 트랜잭션 경계를 정확히 제어하기 위함.

### Redis 키 목록

| 키 패턴 | 타입 | 용도 |
|---------|------|------|
| `cooldown:{providerId}` | String | 사용자 쿨다운 (TTL 5s) |
| `pixel:{x}:{y}` | String | 픽셀 색상 캐시 |
| `pixel:lock:{x}:{y}` | Redisson Lock | 좌표 단위 분산 락 |
| `heatmap:{yyyyMMdd}:{HH}` | ZSET | 시간대별 핫픽셀 집계 (TTL 2h) |
| `pixel-war:ranking` | ZSET | 사용자 점수 순위 |

### 인증

Spring Security + Kakao OAuth2. 사용자 식별자는 클라이언트 전달값이 아닌 `SecurityContext`의 인증 주체에서 추출한 `providerId`를 사용한다 (`CustomOAuth2UserService`, `CustomUserDetails`).

### WebSocket

엔드포인트 `/ws-pixel` (SockJS 호환), 발행 prefix `/pub`, 구독 prefix `/sub`. 픽셀 갱신 이벤트는 `/sub/pixel`로 브로드캐스트된다.

## 환경 설정

`application.yml`은 Docker 호스트명(`mysql`, `redis`) 기준이고, `application-local.yml`에서 로컬 자격증명을 오버라이드한다. `application-local.yml`은 `.gitignore`에 포함되어야 하며 커밋하지 않는다.

로컬 실행 시 필요한 환경 변수 또는 `application-local.yml` 항목:
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`
- `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`
- DB/Redis 접속 정보 (기본값: localhost)