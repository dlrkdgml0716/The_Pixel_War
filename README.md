The Pixel War
수만 명의 사용자가 동시에 하나의 거대한 지도 기반 캔버스에 픽셀을 찍으며 영토를 점유하는 실시간 서비스입니다.
단순한 CRUD를 넘어 고빈도 쓰기 환경에서의 데이터 정합성 보장과 효율적인 공간 인덱싱 아키텍처 구축을 목표로 합니다.
[Client] <--- WebSocket (Real-time Update) ---> [API Gateway / NetFunnel]
                                                       |
                                            [Pixel-Core Service (MSA)]
                                            /          |           \
                    [Redis (Lua/Geo/ZSET)]    [Kafka (Event)]    [S2/H3 Indexer]
                               |                       |
                        [MySQL (Storage)]      [Time-Series DB / Data Lake]
```
Backend & LanguageJava / Spring Boot: 대한민국 엔터프라이즈 환경의 표준이며, 강력한 생태계와 안정적인 MSA 구축을 위해 선택했습니다
Kotlin: Null Safety와 간결한 문법을 통해 런타임 안정성을 높이고 개발 생산성을 극대화했습니다
Database & ConcurrencyRedis: 고빈도 쓰기 데이터의 임시 저장소로 활용하며, Lua 스크립트를 통해 비관적 락의 성능 저하 없이 원자적 연산을 수행합니다
Apache Kafka: 주문/점유 이벤트를 비동기로 처리하여 시스템 간 결합도를 낮추고, 배압(Backpressure) 조절을 통해 연쇄 장애를 방지합니다
1Spatial & Real-timeS2 Geometry (Google): 복잡한 행정 구역(Polygon) 내부의 좌표 포함 여부를 $O(1)$ 또는 $O(\log N)$으로 빠르게 검증합니다
.+1WebSocket: HTTP 폴링의 오버헤드를 줄이고 양방향 실시간 통신을 구현하기 위해 채택했습니다
10.Observability & TestPinpoint / Scouter: MSA 환경에서 서비스 간 호출 관계를 시각화하고 트랜잭션을 추적하여 장애 대응 속도를 높입니다
.+1nGrinder / k6: 부하 테스트를 통해 개선 전후의 RPS 및 Latency 변화를 데이터로 검증합니다12.
