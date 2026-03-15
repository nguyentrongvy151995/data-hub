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
- Query 3
    * Quét + group toàn bộ transactions nên gần như tăng tuyến tính theo tổng số document.
- Query 1
    * Tăng theo số transaction của user đó (user càng “nặng” càng chậm).
- Query 4
    * Tăng theo số transaction match * số log/transaction (join fan-out), nên có thể tăng rất nhanh.
- Query 2
    * Nếu có index chuẩn thì tăng ít (gần như ổn định cho limit 20).
    * Nếu thiếu index sort/filter thì cũng tăng theo data size lớn.