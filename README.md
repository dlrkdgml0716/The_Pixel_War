The Pixel War
수만 명의 사용자가 동시에 하나의 거대한 지도 기반 캔버스에 픽셀을 찍으며 영토를 점유하는 실시간 서비스입니다.
단순한 CRUD를 넘어 고빈도 쓰기 환경에서의 데이터 정합성 보장과 효율적인 공간 인덱싱 아키텍처 구축을 목표로 합니다.

```
graph TD
    %% Client Layer
    subgraph ClientLayer [Real-time Client]
        Web[Web Browser - Canvas/WebGL]
        Mobile[Mobile App - Native View]
    end

    %% Gateway & Traffic Control
    subgraph TrafficControl [Traffic Management]
        AGW[Spring Cloud Gateway]
        NetFunnel[Virtual Waiting Room - Redis ZSET]
        Auth[JWT Auth]
    end

    %% Microservices Layer
    subgraph Microservices [Core Business Logic]
        direction TB
        PixelSvc[Pixel Core: Lua Concurrency]
        SpatialSvc[Spatial: S2/H3 Indexing]
        EventSvc[Event: Kafka Streaming]
    end

    %% Data & Messaging
    subgraph Persistence [Data & Messaging Layer]
        MainDB[(MySQL/PostGIS)]
        Redis[(Redis: GEO/Lua/ZSET)]
        Kafka[[Apache Kafka]]
        TimeSeries[(InfluxDB: Trace Log)]
    end

    %% Relationships
    ClientLayer <--> |WebSocket| AGW
    AGW --> NetFunnel
    NetFunnel --> Microservices
    
    PixelSvc --- Redis
    SpatialSvc --- MainDB
    Microservices -.-> |OrderCreated| Kafka
```
