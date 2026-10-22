# Tài liệu WMS Supply Chain

Mục lục tài liệu của dự án. Hướng dẫn chạy dự án và thông tin chung nằm ở [README.md](../README.md) ở thư mục gốc.

## Tài liệu hiện có

| Tài liệu | Nội dung | Đọc khi nào |
| --- | --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Kiến trúc microservice, bảng service và trách nhiệm, luồng dữ liệu PO, GRN, tồn kho, SO, GDN | Trước khi bắt đầu làm một phân hệ hoặc thêm service |
| [SPRINT_PLAN.md](SPRINT_PLAN.md) | Kế hoạch bốn sprint, mục tiêu từng sprint theo phân hệ và người phụ trách | Khi lập kế hoạch và theo dõi tiến độ |
| [CONVENTIONS.md](CONVENTIONS.md) | Quy ước đặt tên API, mã lỗi, định dạng response, chuẩn code Java và TypeScript | Trước khi viết code và khi review |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Git flow, Conventional Commits, Pull Request, quy trình board, Definition of Done | Trước khi tạo nhánh và mở Pull Request |

## Tài liệu sẽ bổ sung theo tiến độ sprint

| Tài liệu | Dự kiến |
| --- | --- |
| Sơ đồ ERD của từng schema (`auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`) | Sprint 2 |
| Tài liệu API của từng service (Swagger/OpenAPI) | Từ Sprint 2, cập nhật cùng tính năng |
| Danh sách sự kiện RabbitMQ (exchange, routing key, nội dung message) | Sprint 3 |
| Hướng dẫn triển khai và vận hành | Sprint 4 |

## Quy ước viết tài liệu

- Viết bằng tiếng Việt có dấu, định danh kỹ thuật (tên service, tên API, mã lỗi) giữ nguyên tiếng Anh.
- Không dùng icon hoặc emoji.
- Sơ đồ dùng ASCII để xem trực tiếp trong repository và trong diff của Pull Request.
- Thay đổi hành vi hệ thống thì cập nhật tài liệu trong cùng Pull Request.

- [Phân chia nhân sự và phạm vi tính năng (Project Proposal)](PROPOSAL_PHAN_CONG_PHAM_VI.md)
