# Bối cảnh

Hệ thống hiện tại nhận dữ liệu giao dịch từ nhiều hệ thống khác nhau.
Dữ liệu được lưu trữ trong MongoDB để phục vụ truy vấn và báo cáo.
Thông tin hệ thống:
~ 500.000 users
~ 10.000.000 transactions
~ 100.000 transactions / ngày
Một số API hiện tại bắt đầu chậm khi dữ liệu tăng.
Mục tiêu của bài test là phân tích và tối ưu MongoDB để đảm bảo hệ thống có thể scale khi dữ liệu lớn.

1. users:
```bash
{
  "_id": "user_id",
  "email": "user@email.com",
  "name": "User Name",
  "status": "active",
  "created_at": "2026-01-01T10:00:00Z"
}
```

- Query – Tổng số tiền giao dịch của user.
```bash
db.transactions.aggregate([
  {
    $match: { user_id: "10001" }
  },
  {
    $group: 
    {
      _id: null,
      total: { $sum: "$amount" },
    },
  },
])
```

2. transactions:
```bash
{
  "_id": "transaction_id",
  "user_id": "user_id",
  "amount": 100,
  "currency": "USD",
  "status": "success",
  "created_at": "2026-01-01T10:00:00Z"
}
```
Query – 20 giao dịch gần nhất của user.
```bash
db.transactions
.find({ user_id: "10001" })
.sort({ created_at: -1 })
.limit(20)
```

3. transaction_logs:
```bash
{
  "_id": "log_id",
  "transaction_id": "transaction_id",
  "event_type": "PAYMENT_CREATED",
  "payload": {...},
  "created_at": "2026-01-01T10:00:00Z"
}
```
Query – Báo cáo tổng tiền theo ngày.
```bash
db.transactions.aggregate([
  {
    $group: 
    {
      _id: { $dateToString: { format: "%Y-%m-%d", date: "$created_at" } },
      total: { $sum: "$amount" }
    }
  }
])
```

## Phân tích vấn đề performance
1. Vì sao các query trên có thể chậm khi dữ liệu lớn
- transactions đang là bảng lớn nhất (~10M, tăng 100k/ngày), nên query nào không có điều kiện tốt hoặc query không đúng field được đánh index sẽ thành COLLSCAN (COLLSCAN là viết tắt của Collection Scan: MongoDB phải quét toàn bộ collection (tất cả document) để tìm dữ liệu phù với câu query).
- Khi working set vượt RAM, Mongo phải đọc dữ liệu ở đĩa nhiều hơn (page fault) nên độ trễ sẽ tăng lên.
```
Giải thích:

1. Page fault: xảy ra khi dữ liệu cần truy cập không nằm trong RAM nên hệ thống phải đọc từ disk vào RAM.
2. Ví dụ thực tế:
- MongoDB chạy trên một server có tài nguyên phần cứng như sau:
    + RAM: 8GB (dùng RAM để cache dữ liệu thường dùng)
    + CPU
    + Disk: SSD 500 GB (Toàn bộ dữ liệu MongoDB lưu trên disk)
- RAM không thể chứa hết dữ liệu nên khi query.
    + Trường hợp 1 — dữ liệu nằm trong RAM:
        Query
        ↓
        RAM có data
        ↓
        trả kết quả
        => kết quả trả về rất nhanh.

    + Trường hợp 2 — RAM không có data:
        Query
        ↓
        RAM không có
        ↓
        MongoDB đọc từ Disk (SSD)
        ↓
        đưa vào RAM
        ↓
        trả kết quả
        => chậm hơn rất nhiều
    => Vì vậy tốt nhất “Working set phải nhỏ hơn RAM”.
    Để làm được việc này chúng ta có thể kiểm soát working set gián tiếp:
        - Thêm RAM (cách đơn giản nhất)
        - Archive dữ liệu cũ (chuyển dữ liệu ít dùng ra khỏi collection chính)
        - Tối ưu index (quan trọng)
        - Query đúng index
        - TTL index (xoá dữ liệu cũ)
        - Dùng projection (lấy những field cần dùng, giảm data load).
        - Sharding chia một collection lớn thành nhiều phần và phân tán lên nhiều node Mongo (Cách này vận hành phức tạp, trừ khi dữ liệu quá lớn mới áp dụng).
```
```
Dựa vào những ý trên để phân tích các câu query trên:
1. Tổng số tiền giao dịch của user.
    - Nếu thiếu đánh index cho user_id thì phải quét gần như toàn bộ collection
    - Nếu có index cho user_id thì với cầu query trên, user có nhiều transaction thì vẫn phải đọc rất nhiều bản ghi để cộng tổng.
2. 20 giao dịch gần nhất của user
    - Nếu không đánh index theo đúng thứ tự (user_id, created_at, desc) thì Mongo sẽ filter xong rồi mới sort..
3. Báo cáo tổng tiền theo ngày
    - Query khôg có $match, nên mỗi lần chạy là quét toàn bộ 10M+ transaction.
    - $group toàn bộ collection nên ngốn CPU cao.
4. Lấy transaction kèm logs
    - Nếu không đánh index cho transaction_logs.transaction_id, join sẽ rất nặng.
    - Cũng cần đánh index cho user_id.
    - Không có limit transaction trong câu query nên khi join sẽ kéo lượng logs rất lớn.
```
- Nếu đánh index không đúng pattern truy vấn, Mongo vẫn phải scan nhiều dữ liệu thô.

2. Query nào có nguy có collection scan.
- Query 1 nếu thiếu đánh index transactions.user_id và Query 2 nếu đánh index không đúng thứ tự thì sẽ quét nhiều hoặc toàn bộ collection.
- Query 3 không có $match nên sẽ scan toàn bộ transactions.
- Query 4
    + Transactions sẽ scan toàn bộ collection nếu thiếu đánh index user_id
    + Bên transaction_logs có thể bị scan nếu thiếu đánh index cho transaction_id

3. Query nào sẽ tăng độ phức tạp theo data size.
- Query 1
    * Tăng theo số transaction của user đó (user càng “nặng” càng chậm).
- Query 2
    * Nếu có index chuẩn thì tăng ít (gần như ổn định cho limit 20).
    * Nếu thiếu index sort/filter thì cũng tăng theo data size lớn.
- Query 3
    * Quét + group toàn bộ transactions nên gần như tăng tuyến tính theo tổng số document.
- Query 4
    * Tăng theo số transaction match * số log/transaction (join fan-out), nên có thể tăng rất nhanh.

## Thiết kế Index để tối ưu hệ thống

1. **db.transactions.createIndex({ user_id: 1, created_at: -1 })**
    * Lý do: cover cả filter theo user_id và sort created_at desc.
    * Tối ưu: 
        - Query 2 trực tiếp. 
        - Query 1 cũng hưởng lợi ở bước lọc theo user_id tránh COLLSPAN toàn bảng.
        - Query 4 ($match user_id + $lookup)

2. **db.transactions.createIndex({ created_at: -1 })**
    * Lý do: nhiều báo cáo lọc thống kê theo thời gian.
    * Tối ưu: Query 3 khi refactor có $match thời gian.

3. **db.transactions.createIndex({ status: 1, created_at: -1 })**
    * Lý do:  nhiều báo cáo lọc theo status và thống kê theo thời gian.
    * Tối ưu: Query 3 khi refactor có $match thời gian và status.

4. **db.transactions.createIndex({ user_id: 1, status: 1, created_at: -1 })**
    * Lý do: nhiều báo cáo lọc theo user_id status và thống kê theo thời gian.
    * Tối ưu: Query 3 khi refactor có $match user_id, status và thời gian.

5. **db.transaction_logs.createIndex({ transaction_id: 1, created_at: -1 })**
    * Lý do: $lookup join theo transaction_id; có index để tránh scan toàn bộ transaction_logs cho mỗi transaction, created_at giúp lấy log mới nhất hiệu quả nếu có sort/limit trong lookup pipeline.
    * Tối ưu: Query 4 (join transaction kèm logs), đặc biệt khi thêm $lookup.pipeline có sort/limit log.

## Tối ưu Query

1. Query 1 - Tổng số tiền giao dịch của user (tối ưu: lọc status + time range trước khi group)
    ```
    db.transactions.aggregate([
    {
        $match: {
        user_id: "10001",
        status: "success",
        created_at: {
            $gte: ISODate("2026-01-01T00:00:00Z"),
            $lt: ISODate("2026-02-01T00:00:00Z")
        }
        }
    },
    {
        $group: {
        _id: null,
        total: { $sum: "$amount" }
        }
    }
    ])
    ```

2. Query 2 - 20 giao dịch gần nhất của user (tối ưu: projection giảm payload)
db.transactions
    ```
    db.transactions
    .find(
        { user_id: "10001" },
        { _id: 1, user_id: 1, amount: 1, currency: 1, status: 1, created_at: 1 }
    )
    .sort({ created_at: -1 })
    .limit(20)
    ```

3. Query 3 - Báo cáo tổng tiền theo ngày (tối ưu: match theo thời gian trước, group theo day)
    ```
    db.transactions.aggregate([
    {
        $match: {
        status: "success",
        created_at: {
            $gte: ISODate("2026-01-01T00:00:00Z"),
            $lt: ISODate("2026-02-01T00:00:00Z")
        }
        }
    },
    {
        $group: {
        _id: { $dateTrunc: { date: "$created_at", unit: "day" } },
        total: { $sum: "$amount" }
        }
    },
    {
        $sort: { _id: 1 }
    }
    ], { allowDiskUse: true })
    ```
4. Query 4 - Lấy transaction kèm logs (tối ưu: sort+limit transaction trước lookup, lookup pipeline + project)
    ```
    db.transactions.aggregate([
    {
        $match: { user_id: "10001" }
    },
    {
        $sort: { created_at: -1 }
    },
    {
        $limit: 20
    },
    {
        $lookup: {
        from: "transaction_logs",
        let: { txId: "$_id" },
        pipeline: [
            {
            $match: {
                $expr: { $eq: ["$transaction_id", "$$txId"] }
            }
            },
            {
            $sort: { created_at: -1 }
            },
            {
            $limit: 50
            },
            {
            $project: {
                _id: 1,
                transaction_id: 1,
                event_type: 1,
                created_at: 1
            }
            }
        ],
        as: "logs"
        }
    },
    {
        $project: {
        _id: 1,
        user_id: 1,
        amount: 1,
        currency: 1,
        status: 1,
        created_at: 1,
        logs: 1
        }
    }
    ], { allowDiskUse: true })
    ```

- Index đi kèm để các query trên chạy tốt:
    ```
    db.transactions.createIndex({ user_id: 1, created_at: -1 })
    db.transactions.createIndex({ user_id: 1, status: 1, created_at: -1 })
    db.transaction_logs.createIndex({ transaction_id: 1, created_at: -1 })
    ```

## Thiết kế Schema tối ưu hơn
I.  đề xuất:

1. **users - transactions: Referencing**
- Giữ transactions.user_id tham chiếu users._id.
- Không embed transactions vào user.
- Lý do: số transaction rất lớn (10M+, tăng 100k/ngày), embed sẽ làm document user phình to, khó scale write/read.

2. **transactions - transaction_logs: Referencing (không embed full logs)**
- Giữ transaction_logs riêng, join bằng transaction_id.
- Không embed toàn bộ logs[] vào transaction vì:
    + logs có thể tăng nhiều theo thời gian.
    + dễ vượt kích thước document.
    + query list transaction thường không cần full payload logs.

3. **Denormalization trong transactions**
- Thêm các field summary để giảm $lookup:
    + log_count (tổng số log của transaction)
    + last_event_type (event log mới nhất)
    + last_event_at ( thời điểm log mới nhất)
- **Lợi ích:**
    + API list/detail transaction thường chỉ cần “trạng thái hiện tại”, không cần full mảng logs.
    + Không cần $lookup mỗi lần => giảm join, giảm RAM/CPU/network.

4. **Denormalization cho báo cáo (nên có collection summary)**
- Tạo collection daily_transaction_summary:
    + { day, total_amount, success_total, failed_total, tx_count }
- Có thể thêm user_daily_summary:
    + { user_id, day, total_amount, tx_count }
- Lợi ích: thay vì group trên raw 10M records mỗi lần, report đọc từ bảng tổng hợp nhỏ.

II. Trade-off:

1. Referencing
    - Ưu: scale tốt, document gọn, linh hoạt audit/history.
    - Nhược: cần $lookup hoặc nhiều query khi cần dữ liệu liên quan.
2. Embedding
    - Ưu: đọc 1 lần ra đủ dữ liệu.
    - Nhược: document phình to, update tốn, không hợp với logs tăng liên tục.
3. Denormalization
    - Ưu: đọc rất nhanh cho API/report nóng.
    - Nhược: write path phức tạp hơn, phải giữ đồng bộ dữ liệu (eventual consistency/reconciliation job).

## Thiết kế giải pháp khi dữ liệu rất lớn

1) Index strategy
    - Chỉ tạo index cho những query chạy thường xuyên, quan trọng với API của hệ thống. Vì mỗi index trong MongoDB là một cấu trúc dữ liệu riêng, thường là B-tree.
    - Khi tạo thêm index, Mongo phải:
        + Tốn thêm disk
        + Tốn thêm RAM để cache index
        + Mỗi lần insert/update/delete phải update cả document lần các index liên quan, dẫn đến làm tăng write latency.
        => nhiều index: đọc nhanh hơn nhưng ghi sẽ chậm hơn, storage tăng, bộ nhớ tốn hơn.

        + transactions:
            ({ user_id: 1, created_at: -1 })
            ({ user_id: 1, status: 1, created_at: -1 })
            ({ created_at: -1 }) (nếu có query/report theo time-range toàn hệ thống)

        + transaction_logs:
            ({ transaction_id: 1, created_at: -1 })

    - Tách index cho hot vs cold data.
        + Hot data là dữ liệu gần đây (ví dụ 3 tháng, 6 tháng) thường được api truy cập liên tục, dashboard dùng liên tục, support tra cứu thường xuyên.
        + Cold data dữ liệu cũ hơn (ví dụ 12 tháng) thường ít được query, chủ yếu tra cứu lịch sử, report dài hạn.
        => nếu nhồi nhét hết vào 1 collection với full index thì collection rất ro, index nhiều, RAM cache kém hiệu quả, write chậm.
        + Chiên lược tách: cách phổ biến là chia làm 2 collections
            - db.transactions_hot (ví dụ data 6 tháng gần nhất):
                + Index:
                    ```
                        db.transactions_hot.createIndex({ user_id: 1, created_at: -1 })
                        db.transactions_hot.createIndex({ user_id: 1, status: 1, created_at: -1 })
                        db.transactions_hot.createIndex({ created_at: -1 })
                    ```
            - db.transactions_archive (ví dụ data 6 tháng trước trở đi):
                + Index:
                    ```
                    db.transactions_archive.createIndex({ user_id: 1, created_at: -1 })
                    db.transactions_archive.createIndex({ created_at: -1 })
                    ```
        + Cách làm:
            - Move định kỳ bằng job, mỗi đêm cho job chạy.
            - Lấy document trong transactions_hot có created_at < (now - 6 tháng) copy sang transactions_archive.
            - Có 1 job riếng để xoá data có thời gian created_at > (now - 6 tháng) khỏi transactions_hot ().

    - Định kỳ review index usage.
        - Lúc đầu bạn tạo index để phục vụ một API. Sau vài tháng:
            + API đó ít dùng
            + business logic đổi
            + query shape thay đổi
            + index cũ không còn ai dùng

            Nếu không review, hệ thống sẽ bị tích tụ “rác index”.
            => Dùng explain, profiler, slow query log để drop index không dùng.

2) Sharding strategy:
    - Nguyên nhân: Khi một collection quá lớn và traffic quá cao, 1 máy/1 replica không gánh nổi.
    - Giải pháp: MongoDB cho phép sharding: chia dữ liệu của một collection ra nhiều shard.
    - Flow đơn giản cho mô hình này:
    ```
    app (query) → mongos → shard phù hợp (có nhiều shard) → gom kết quả → trả về app
    ```
    - MongoDB xử lý như sau:
        ```
        - App gửi query vào mongos
        - Mongos dựa vào shard key + metadata
        - Nếu query có shard key phù hợp → gửi tới đúng shard cần thiết
        - Nếu không xác định được → gửi tới nhiều/tất cả shard
        - Các shard xử lý dữ liệu local của mình
        - Mongos gom, merge, sort, limit nếu cần
        - Trả về cho app như một kết quả thống nhất
        ```
    => Cách này khá phức tạp, dữ liệu cực lớn mới dùng đến cách này.

3) Partition theo time (time-based split)
    - Mục đích là chia dữ liệu theo thời gian để giảm khối lượng dữ liệu phải đọc và làm cho query/report dễ scale hơn. 
    - Chia logical theo tháng/ngày:
        + Ví dụ: transactions_2026_01, transactions_2026_02 (hoặc bucket field month_bucket).
        + Query ngày/tháng sẽ đọc subset nhỏ hơn.
    - Với Mongo, có thể:
        + Dùng collection theo thời gian (manual partition),
        + Hoặc dùng shard key có thành phần thời gian (cẩn trọng hotspot ghi theo thời gian hiện tại).
    - Report theo ngày/tháng:
        + Ưu tiên đọc từ summary table thay vì group raw data.

4) Archive dữ liệu cũ
    - Chính sách dữ liệu:
        + Hot: 6-12 tháng gần nhất trong transactions.
        + Cold: chuyển sang transactions_archive.
    - Cách archive:
        + Job định kỳ (daily/weekly) move theo created_at.
        + Có checksum/count đối soát trước-sau khi move.
        + Archive có thể để ở cluster rẻ hơn.
    - Query chiến lược:
        + API realtime chỉ đọc hot.
        + Query lịch sử dài hạn đọc archive hoặc union (hot + archive) khi cần.
    - Với transaction_logs:
        + TTL hoặc archive theo retention policy (nếu nghiệp vụ cho phép).

5) Kết luận kiến trúc scale lớn
    - OLTP collection nhỏ, index gọn, query nóng nhanh.
    - Sharding để scale ngang ghi/đọc.
    - Time partition + archive để tránh phình vô hạn.
    - Report chuyển sang pre-aggregation (daily_summary, user_daily_summary) thay vì full scan 500M raw docs.
## 5. Deliverables

### 5.1 MongoDB script

- File: `scripts/mongo-deliverables.js`
- Bao gồm:
  - Script tạo model/schema validator cho `users`, `transactions`, `transaction_logs`, và summary collections.
  - Script tạo index cho `transactions` và `transaction_logs`.
  - 4 query đã tối ưu (Query 1..4) theo yêu cầu bài test.

Chạy script:

```bash
mongosh "mongodb://datahub:datahub@localhost:27018/admin?authSource=admin&directConnection=true" --file scripts/mongo-deliverables.js
```

Nếu dùng Mongo local mặc định `27017` thì thay port trong URI tương ứng.

### 5.2 Cài mongosh và chạy script local

Cài `mongosh` (macOS/Homebrew):

```bash
brew install mongosh
mongosh --version
```

Chạy script bằng `mongosh` local:

```bash
mongosh "mongodb://datahub:datahub@localhost:27018/admin?authSource=admin&directConnection=true" --file scripts/mongo-deliverables.js
```

Nếu chưa muốn cài `mongosh` local, chạy trực tiếp bằng `mongosh` trong container Mongo:

```bash
docker exec -it data-hub-mongo mongosh \
  "mongodb://datahub:datahub@127.0.0.1:27017/admin?authSource=admin&directConnection=true" \
  --file scripts/mongo-deliverables.js
```

Ghi chú:
- `localhost:27018` là cổng Mongo publish ra máy local từ Docker Compose.
- Nếu bạn chạy Mongo local mặc định `27017`, thay URI tương ứng để tránh chạy nhầm DB.

### 5.3 Chạy script với Mongo local mặc định

Dùng cách này khi bạn chạy MongoDB local trên `localhost:27017` và **không bật auth**:

```bash
mongosh --file scripts/mongo-deliverables.js
```

Nếu local Mongo có bật auth hoặc chạy port khác, dùng lệnh có URI đầy đủ ở mục 5.1/5.2.
