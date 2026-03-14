# Data Hub Service

Dự án Spring Boot xử lý event theo mô hình Clean/Hexagonal Architecture: nhận event từ Kafka, xây dựng một service để tiếp nhận, xử lý và quản lý dữ liệu, lưu MongoDB, và cung cấp API truy vấn/report.

## 1. Architecture Overview

### 1.1 Kiến trúc tổng thể

- `interfaces` (REST/Kafka): nhận request/message từ bên ngoài.
- `application` (use case + service): điều phối luồng nghiệp vụ.
- `domain` (model + port): định nghĩa nghiệp vụ lõi, không phụ thuộc framework.
- `shared` (exception + handler): chứa cross-cutting concerns dùng chung giữa các layer.
- `infrastructure` (adapter + repository + config): triển khai kỹ thuật cụ thể (MongoDB, Kafka, Security, Bean).

### 1.2 Sơ đồ component

#### Level 1 - System Context

```mermaid
flowchart LR
    Client["Client Apps\n(Web/Mobile/Backend)"]
    Producer["External Event Producers"]
    Service["Data Hub Service\n(Spring Boot)"]
    Raw["Kafka topic: data-hub.user-orders\n(partitions = 3)"]
    Dlt["Kafka topic: data-hub.user-orders.DLT\n(partitions = 3)"]
    Mongo[("MongoDB\nraw_event, person")]

    Client -->|REST API| Service
    Producer -->|publish events| Raw
    Raw -->|consume| Service
    Service -->|persist + query| Mongo
    Service -->|failed after retries| Dlt
```
#### Level 2 - Container View

```mermaid
flowchart LR
    CLIENT[REST Clients]
    PROD[Kafka Producers]
    KAFKA[(Kafka Topics\npartitions = 3 each)]
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
- `shared`: exception domain-level + `GlobalExceptionHandler` cho REST error mapping.
- `infrastructure/persistence`: adapter + repository + document Mongo.
- `infrastructure/kafka`: listener, consumer config, topic config, producer scheduler.

## 2. Luồng xử lý message

### 2.1 Luồng Kafka consume (chính)

```mermaid
sequenceDiagram
    participant K as Kafka main (partitions = 3)
    participant L as RawEventKafkaListener
    participant S as EventApplicationService
    participant DB as MongoDB
    participant D as Kafka DLT (partitions = 3)

    K->>L: receive message
    L->>L: Step 1 - Parse + Validate

    alt Step 1 FAIL (JSON sai hoặc thiếu field bắt buộc -> không retry, đi DLT)
        opt payload lỗi -> mark FAILED trước khi đẩy DLT
            L->>S: markFailedAfterRetries(eventDto)
            S->>DB: update status = FAILED
        end
        L->>D: publish failure (MAIN_TO_DLT)
        L->>L: ACK offset (done)
    else Step 1 OK
        L->>S: Step 2 - Nhận quyền xử lý theo eventId
        S->>DB: saveIfAbsent(eventId, status=PROCESSING)

        alt inserted (first claim)
            DB-->>S: STORED
            S-->>L: STORED
        else duplicate key
            DB-->>S: DUPLICATE
            S->>DB: reclaimIf(status=FAILED_RETRYABLE) -> PROCESSING (atomic)
            alt reclaimed
                DB-->>S: STORED
                S-->>L: STORED
            else cannot reclaim
                DB-->>S: DUPLICATE
                S-->>L: DUPLICATE
                L->>L: ACK offset (skip)
            end
        end

        alt claim result = STORED
            L->>S: processClaimedEvent(eventDto)
            alt business success
                S->>DB: update status = SUCCESS
                L->>L: ACK offset (done)
            else business fail (retryable)
                S->>DB: update status = FAILED_RETRYABLE
                alt còn retry
                    L->>L: backoff
                    L->>L: retry from Step 1
                else hết retry
                    L->>S: markFailedAfterRetries(eventDto)
                    S->>DB: update status = FAILED
                    L->>D: publish failure (MAIN_TO_DLT)
                    L->>L: ACK offset (done)
                end
            end
        end
    end
```
#### Nhánh lỗi và retry

1. Listener parse + validate trước khi xử lý business.
2. Nếu lỗi ngay từ bước đọc dữ liệu (JSON sai, thiếu field bắt buộc) thì bỏ qua retry, đẩy thẳng DLT và `ack`.
3. Claim dùng `saveIfAbsent(eventId, status=PROCESSING)` (atomic insert-if-absent).
4. Nếu duplicate, service thử reclaim atomically khi record đang `FAILED_RETRYABLE`.
5. Reclaim thành công thì tiếp tục xử lý như event mới; reclaim thất bại thì coi là duplicate và skip.
6. Khi business fail retryable, service set `FAILED_RETRYABLE` (không delete record).
7. Retry luôn quay lại từ Step 1; chỉ retry với lỗi retryable.
8. Hết retry: `markFailedAfterRetries` -> `FAILED` -> publish DLT -> `ack`.

## 3. Tài liệu thiết kế DB

### 3.1 Collection `raw_event`

Mục tiêu: lưu event thô + trạng thái xử lý để support ingest idempotent, audit, và report.

#### Schema chính

- `id` (Mongo ObjectId)
- `eventId` (business key, unique)
- `eventType`
- `sourceSystem`
- `status` (`PROCESSING` | `SUCCESS` | `FAILED_RETRYABLE` | `FAILED`)
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

## 4. Design Decisions

#### 1. Cách xử lý duplicate

1. `claimForProcessing` gọi `saveIfAbsent(eventId, status=PROCESSING)` để claim quyền xử lý.
2. `eventId` có unique index nên chỉ 1 consumer claim thành công cho mỗi eventId.
3. Nếu insert bị duplicate, service thử `reclaimForProcessing(eventId)` khi status hiện tại là `FAILED_RETRYABLE` (atomic).
4. Nếu reclaim thất bại thì coi là duplicate thật, bỏ qua business logic và `ack` offset.

#### 2. Cách commit offset

1. Listener dùng `MANUAL_IMMEDIATE`, tức là chỉ commit khi gọi `acknowledgment.acknowledge()`.
2. Offset được commit ở 3 trường hợp: xử lý thành công, duplicate-skip, hoặc đã publish sang DLT.
3. Khi còn retry nội bộ (`attempt < totalAttempts`) thì chưa `ack`, offset vẫn chưa commit.

#### 3. Chiến lược retry

1. Retry thực hiện trong cùng lần consume bằng vòng lặp `attempt = 1..N` và `backoff`.
2. Chỉ retry lỗi retryable; lỗi `InvalidEventException` và `JsonProcessingException` đi nhánh non-retryable.
3. Hết retry: mark `FAILED` (nếu parse được DTO/eventId), publish DLT, rồi `ack` offset main topic.

#### 4. Cách đảm bảo không mất dữ liệu

1. Dùng semantics at-least-once: chỉ commit offset sau khi có kết quả cuối cùng cho message.
2. Event hợp lệ được lưu vào Mongo với status lifecycle `PROCESSING -> SUCCESS/FAILED_RETRYABLE/FAILED`.
3. Nếu service crash trước khi `ack`, Kafka sẽ deliver lại; duplicate được chặn bởi unique `eventId` + claim/reclaim atomic.
4. Message xử lý thất bại cuối cùng vẫn được giữ ở DLT (kèm payload + metadata lỗi) để điều tra/replay, tránh thất lạc dữ liệu.

## 5. Scaling Strategy

Nếu lưu lượng message tăng gấp 10 lần, hệ thống có thể scale theo các trục sau:

1. **Scale Kafka partition**
- Tăng partition của topic chính và DLT (ví dụ từ `3` lên `12` hoặc `24`) để tăng mức song song.
- Giữ key theo `eventId` để cùng một `eventId` luôn vào cùng partition (giữ ordering theo key).

2. **Scale ngang consumer instance**
- Chạy nhiều instance Data Hub cùng `group-id` để Kafka rebalance partition cho các instance.
- Dùng autoscaling theo `consumer lag`, CPU, và memory.

3. **Tune consumer throughput**
- Điều chỉnh `spring.kafka.listener.concurrency`, `max.poll.records`, `pollTimeout`, `retry.backoff-ms` theo tải thực tế.
- Tách business logic nặng thành bước xử lý bất đồng bộ nếu thời gian xử lý mỗi event tăng cao.

4. **Scale MongoDB**
- Dùng replica set để tăng độ sẵn sàng; khi tải ghi tăng mạnh, cân nhắc sharding.
- Giữ index chính xác theo pattern truy vấn (`eventId`, `status`, `updatedAt`, `sourceSystem`) để tránh full scan.

5. **Observability + capacity planning**
- Theo dõi các chỉ số: Kafka lag, tỉ lệ DLT, latency xử lý event, lỗi Mongo write.
- Chạy load test theo từng mức (2x, 5x, 10x) và chốt ngưỡng scale trước khi lên production.

## 6. Trade-offs

Các quyết định kỹ thuật hiện tại và hạn chế đi kèm:

1. **At-least-once delivery**
- Ưu điểm: giảm nguy cơ mất message.
- Đánh đổi: duplicate có thể xảy ra, cần idempotency (`unique eventId` + claim/reclaim).

2. **Manual ack sau khi có kết quả cuối**
- Ưu điểm: kiểm soát được thời điểm commit offset.
- Đánh đổi: độ trễ xử lý tăng, đặc biệt khi retry/backoff hoặc publish DLT đồng bộ.

3. **Lưu `payload` dạng string**
- Ưu điểm: linh hoạt với schema upstream thay đổi.
- Đánh đổi: khó query sâu theo field trong payload, khó tối ưu index cho field động.

4. **Model status đơn giản (`PROCESSING`, `SUCCESS`, `FAILED_RETRYABLE`, `FAILED`)**
- Ưu điểm: dễ vận hành và dễ đọc trạng thái.
- Đánh đổi: chưa có lịch sử state transition chi tiết (audit trail theo từng bước).

5. **Reclaim chỉ cho `FAILED_RETRYABLE`**
- Ưu điểm: đơn giản, tránh nhiều nhánh trạng thái phức tạp.
- Đánh đổi: nếu instance chết sau khi claim nhưng trước khi cập nhật trạng thái, record có thể bị kẹt ở `PROCESSING` và cần cơ chế recovery riêng (timeout/lease scheduler).

## 7. Chạy local nhanh

### 7.1 Start project bằng Docker Compose (Kafka + MongoDB + Service)

```bash
docker compose down -v --remove-orphans
docker compose up --build -d
docker compose ps
curl http://localhost:8084/ping
```

### 7.2 Start project khi chạy service từ source (optional)

```bash
docker compose up -d mongo kafka kafka-ui
export MONGODB_URI='mongodb://datahub:datahub@localhost:27018/data_hub?authSource=admin&directConnection=true'
export KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
./mvnw spring-boot:run
```

### 7.3 URL/URI cho dev

- App health check: `http://localhost:8084/ping`
- Kafka UI: `http://localhost:18080`
- MongoDB URI (từ local tool/Compass): `mongodb://datahub:datahub@localhost:27018/admin?authSource=admin&directConnection=true`
- MongoDB URI (service chạy trong Docker): `mongodb://datahub:datahub@mongo:27017/data_hub?authSource=admin`
- Kafka bootstrap server (local tool/service local): `localhost:9092`
- Kafka bootstrap server (service trong Docker network): `kafka:29092`

### 7.4 Run test

```bash
./mvnw test
```
