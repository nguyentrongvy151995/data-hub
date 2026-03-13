# Data Hub Service

Dự án Spring Boot xử lý event theo mô hình Clean/Hexagonal Architecture: nhận event từ REST/Kafka, chống duplicate theo `eventId`, lưu MongoDB, và cung cấp API truy vấn/report.

## 1. Architecture Overview

### 1.1 Kiến trúc tổng thể

- `interfaces` (REST/Kafka): nhận request/message từ bên ngoài.
- `application` (use case + service): điều phối luồng nghiệp vụ.
- `domain` (model + port): định nghĩa nghiệp vụ lõi, không phụ thuộc framework.
- `infrastructure` (adapter + repository + config): triển khai kỹ thuật cụ thể (MongoDB, Kafka, Security, Bean).

### 1.2 Sơ đồ component (C4-style)

#### Level 1 - System Context

```mermaid
flowchart TB
    Client["Client Applications\n(Web/Mobile/Backend)"]
    Producer["External Event Producers"]
    Kafka[("Kafka Cluster\nraw-events / DLT / parking-lot")]
    DataHub["Data Hub Service\nSpring Boot"]
    Mongo[("MongoDB")]

    Client -- "REST/JSON" --> DataHub
    Producer -- "Publish events" --> Kafka
    Kafka -- "Consume raw-events" --> DataHub
    DataHub -- "Persist + Query" --> Mongo
    DataHub -- "Publish failed events" --> Kafka
```

#### Level 2 - Container View (bên trong Data Hub)

```mermaid
flowchart LR
    CLIENT[REST Clients]
    PROD[Kafka Producers]
    KAFKA[(Kafka Topics)]
    MONGO[(MongoDB)]

    subgraph DH[Data Hub Service]
        REST["REST API Layer\nEventCommandController\nEventQueryController\nReportController\nPersonController"]
        LISTENER["Kafka Consumer Layer\nRawEventKafkaListener"]
        APP["Application Layer\nEventApplicationService\nPersonApplicationService"]
        PORTS["Domain Ports\nEventStorePort\nPersonStorePort\nEventBusinessProcessor"]
        ADAPTER["Infrastructure Adapters\nMongoEventStoreAdapter\nMongoPersonStoreAdapter"]
        REPO["Mongo Repositories\nRawEventMongoRepository\nPersonMongoRepository"]
    end

    CLIENT --> REST
    REST --> APP

    PROD --> KAFKA
    KAFKA --> LISTENER
    LISTENER --> APP

    APP --> PORTS
    PORTS --> ADAPTER
    ADAPTER --> REPO
    REPO --> MONGO

    LISTENER --> KAFKA
```

Cách đọc:
- Context diagram: cho người đọc business/system-level thấy hệ thống giao tiếp với ai.
- Container diagram: cho dev thấy trách nhiệm từng khối trong service và dependency direction.

### 1.3 Mapping package theo tầng

- `interfaces/rest`: HTTP controllers + request/response mapper.
- `application/service`: hiện thực use case.
- `application/usecase`: contract cho tầng interface gọi vào.
- `domain/port`: contract cho tầng application gọi ra infrastructure.
- `infrastructure/persistence`: adapter + repository + document Mongo.
- `infrastructure/kafka`: listener, consumer config, topic config, producer scheduler.

## 2. Luồng xử lý message

### 2.1 Luồng Kafka consume (chính)

```mermaid
sequenceDiagram
    participant K as Kafka Topic (raw-events)
    participant L as RawEventKafkaListener
    participant S as EventApplicationService
    participant P as EventStorePort
    participant DB as MongoDB(raw_event)

    K->>L: message (eventId, eventType, source, payload, createdAt)
    L->>L: parse + validate
    L->>S: ingest(eventDto)
    S->>P: saveIfAbsent(event)
    P->>DB: insert(raw_event)

    alt STORED
        S->>S: processEventWithTimeout
        S->>P: updateStatusByEventId(SUCCESS)
        L->>L: ack offset
    else DUPLICATE
        S-->>L: DUPLICATE
        L->>L: ack offset (skip business logic)
    end
```

#### Nhánh lỗi và retry

1. Listener retry trong cùng lần consume theo `app.kafka.retry.max-attempts`.
2. Mỗi lần fail sẽ publish envelope sang topic DLT (`raw-events-dlt`).
3. Khi hết retry:
- gọi `markFailedAfterRetries` để set `FAILED` trong DB,
- publish thêm sang parking-lot (`raw-events-parking-lot`),
- rồi `ack` offset để tránh stuck consumer.

### 2.2 Luồng REST command/query

#### Command flow (`POST/PUT/DELETE /api/events`)

`Controller -> RestRequestMapper -> ManageEventUseCase(EventApplicationService) -> EventStorePort -> MongoEventStoreAdapter -> RawEventMongoRepository -> MongoDB`

#### Query flow (`GET /api/events`, `GET /api/events/{eventId}`, `GET /api/reports/ingestion-summary`)

`Controller -> QueryEventUseCase(EventApplicationService) -> EventStorePort -> Mongo adapter/repository -> MongoDB -> RestResponseMapper -> response`

## 3. Tài liệu thiết kế DB

### 3.1 Collection `raw_event`

Mục tiêu: lưu event thô + trạng thái xử lý để support ingest idempotent, audit, và report.

#### Schema chính

- `id` (Mongo ObjectId)
- `eventId` (business key, unique)
- `eventType`
- `sourceSystem`
- `status` (`PENDING` | `SUCCESS` | `FAILED`)
- `payload` (JSON string gốc)
- `createdAt` (thời điểm event sinh ra)
- `updatedAt` (thời điểm hệ thống ghi/cập nhật)

#### Index hiện tại

- `uk_event_event_id` (unique trên `eventId`)
- `idx_event_type`
- `idx_event_source_system`
- `idx_event_status`
- `idx_event_updated_at`

#### Vì sao thiết kế như vậy

1. **Unique `eventId`** để chống lưu trùng khi duplicate từ Kafka/REST.
2. **`status` riêng** để theo dõi lifecycle xử lý và hỗ trợ retry/failure analysis.
3. **Tách `createdAt` và `updatedAt`** để phân biệt thời gian nghiệp vụ và thời gian xử lý hệ thống.
4. **Index `updatedAt`** phục vụ filter theo cửa sổ thời gian cho report; **index `sourceSystem`** phục vụ tra cứu và mở rộng truy vấn theo nguồn.
5. **Lưu `payload` dạng string** để giữ nguyên raw event, tránh coupling chặt vào schema payload động từ upstream.

### 3.2 Collection `person`

Mục tiêu: module CRUD đơn giản để minh họa thêm một aggregate khác.

Schema:
- `id`
- `name`
- `age`

Hiện chưa có index custom vì use case hiện tại chỉ tạo mới, chưa có truy vấn phức tạp.

### 3.3 Consistency và duplicate handling

- Duplicate được chặn ở tầng DB (unique index) và phản ánh lên service qua `DUPLICATE`.
- Consumer dùng manual ack (`MANUAL_IMMEDIATE`) + `enable-auto-commit=false` để tránh mất message khi service restart.
- Semantics hiện tại là **at-least-once** (ưu tiên không mất dữ liệu; có thể nhận duplicate và xử lý idempotent theo `eventId`).

## 4. API chính

- `GET /ping`
- `POST /api/events`
- `PUT /api/events/{eventId}`
- `DELETE /api/events/{eventId}`
- `GET /api/events`
- `GET /api/events/{eventId}`
- `GET /api/reports/ingestion-summary?from=&to=`
- `POST /api/person`

## 5. Chạy local nhanh

### 5.1 Start dependencies

```bash
docker compose up -d
```

### 5.2 Run app

```bash
./mvnw spring-boot:run
```

App mặc định chạy tại `http://localhost:8084`.

### 5.3 Run test

```bash
./mvnw test
```
