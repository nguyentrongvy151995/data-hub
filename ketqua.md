Github: https://github.com/nguyentrongvy151995/data-hub

## Bài 1 – Service xử lý message Kafka

### Kết quả phân tích và triển khai
- `README.md`: mô tả kiến trúc, luồng xử lý message (consume/produce, duplicate handling, retry/DLT), quyết định thiết kế dữ liệu/index, hướng dẫn chạy và test.
- `Dockerfile`: build và chạy Spring Boot service trong container.
- `compose.yaml`: chạy local đầy đủ `Kafka + MongoDB + Service`.
---

## Bài 2 – Phân tích và tối ưu MongoDB

### Bối cảnh
- ~500.000 users
- ~10.000.000 transactions
- ~100.000 transactions/ngày
- Mục tiêu: tối ưu query và scale khi dữ liệu lớn.

### Kết quả phân tích và triển khai
- `Bai2.md`: phân tích performance bottleneck, chiến lược index, query optimization, schema trade-offs, scaling strategy.
- `scripts/mongo-deliverables.js`: script thực thi gồm tạo index và các query tối ưu để reviewer chạy trực tiếp bằng `mongosh`.
