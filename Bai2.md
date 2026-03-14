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
- Khi working set vượt RAM, Mongo phải đọc dữ liệu ở đĩa nhiều hơn (oage fault) nên độ trễ sẽ tăng lên.
```
- Giải thích:
```