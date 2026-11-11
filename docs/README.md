# Tài liệu WMS Supply Chain

Mục lục tài liệu của dự án. Hướng dẫn chạy dự án và thông tin chung nằm ở [README.md](../README.md) ở thư mục gốc.

## Tài liệu hiện có

| Tài liệu | Nội dung | Đọc khi nào |
| --- | --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Kiến trúc microservice, bảng service và trách nhiệm, luồng dữ liệu PO, GRN, tồn kho, SO, GDN | Trước khi bắt đầu làm một phân hệ hoặc thêm service |
| [SAAS_PLAN.md](SAAS_PLAN.md) | Kế hoạch mở rộng SaaS: đa doanh nghiệp, đăng ký và gói dịch vụ, thanh toán PayOS, AI nhập liệu, API key; task WMS-22 đến WMS-26 và lịch từng người tới 23/11 | Khi làm các task WMS-22 trở đi, khi cần biết phạm vi và kịch bản demo |
| [SAAS_SPEC.md](SAAS_SPEC.md) | Đặc tả chức năng từng task WMS-22 đến WMS-26: dữ liệu, API, quy tắc nghiệp vụ, tiêu chí nghiệm thu; quy ước chung về JWT, header, mã lỗi, gói dịch vụ | Trước khi bắt đầu một task SaaS và khi review Pull Request của task đó |
| [MULTI_TENANT.md](MULTI_TENANT.md) | Cơ chế tách dữ liệu theo doanh nghiệp trong module `common` (`TenantContext`, `TenantEntity`, bộ lọc `X-Company-Id`), hành vi cần nắm, các bước chuyển một service, mẫu migration và mẫu test cách ly | Khi thêm `company_id` cho một service (WMS-22.3, WMS-22.4) và khi tạo bảng nghiệp vụ mới |
| [PERFORMANCE.md](PERFORMANCE.md) | Hiệu năng: bộ nhớ đệm Redis cho tổng tồn và tồn khả dụng (khóa, cách xóa sau mỗi thay đổi tồn, khi Redis lỗi), chỉ mục và sửa truy vấn lặp, cách đo, những gì cố ý không làm | Khi sửa đường thay đổi tồn kho, thêm API danh sách, thêm truy vấn hoặc migration chỉ mục |
| [SPRINT_PLAN.md](SPRINT_PLAN.md) | Kế hoạch bốn sprint, mục tiêu từng sprint theo phân hệ và người phụ trách | Khi lập kế hoạch và theo dõi tiến độ |
| [CONVENTIONS.md](CONVENTIONS.md) | Quy ước đặt tên API, mã lỗi, định dạng response, chuẩn code Java và TypeScript | Trước khi viết code và khi review |
| [USER_GUIDE.md](USER_GUIDE.md) | Hướng dẫn sử dụng theo từng màn hình (menu, nút, trường, trạng thái); trợ lý AI gửi kèm tài liệu này khi trả lời câu hỏi cách dùng, bản chép nằm ở `backend/ai-service/src/main/resources/assistant/` | Khi hướng dẫn người dùng mới; sửa giao diện thì cập nhật tài liệu và chép lại bản trong ai-service |
| [CI_CD.md](CI_CD.md) | Các job GitHub Actions, kiểm tra bắt buộc trước khi merge, tên và thẻ image trên GHCR, cách kéo image | Khi sửa workflow CI, khi cần image để triển khai |
| [MESSAGING.md](MESSAGING.md) | Sự kiện RabbitMQ: exchange `wms.events`, quy ước routing key và queue, danh sách sự kiện, dead-letter, cách thêm sự kiện mới, tài khoản broker | Khi publish hoặc nhận sự kiện giữa các service, khi thêm sự kiện mới |
| [INTEGRATION.md](INTEGRATION.md) | Hướng dẫn cho hệ thống ngoài: tạo API key, gửi `X-Api-Key`, các API chỉ đọc (hàng hóa, tồn kho, đơn hàng), ví dụ `curl`, bảng lỗi, giới hạn tốc độ, Swagger | Khi tích hợp một hệ thống ngoài với WMS |
| [OPERATIONS.md](OPERATIONS.md) | Bàn giao vận hành: yêu cầu máy chủ, cài đặt lần đầu, bảng biến môi trường, cập nhật và quay lại bản cũ, kiểm tra sự cố (log, RabbitMQ dead-letter, Redis, Flyway), sao lưu và phục hồi MySQL, việc định kỳ, danh sách bàn giao | Khi tiếp quản hoặc vận hành máy chủ |
| [../deploy/README.md](../deploy/README.md) | Hướng dẫn triển khai lên VPS Ubuntu bằng Docker Compose và Caddy: cài Docker, DNS, HTTPS tự động, script `deploy.sh`, sao lưu MySQL, khắc phục sự cố | Khi triển khai hoặc cập nhật máy chủ production |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Git flow, Conventional Commits, Pull Request, quy trình board, Definition of Done | Trước khi tạo nhánh và mở Pull Request |

## Tài liệu sẽ bổ sung theo tiến độ sprint

| Tài liệu | Dự kiến |
| --- | --- |
| Sơ đồ ERD của từng schema (`auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`) | Sprint 2 |
| Tài liệu API của từng service (Swagger/OpenAPI) | Từ Sprint 2, cập nhật cùng tính năng |
| Cấu trúc JSON chi tiết của từng sự kiện RabbitMQ (bổ sung vào MESSAGING.md) | Sprint 3 |

## Quy ước viết tài liệu

- Viết bằng tiếng Việt có dấu, định danh kỹ thuật (tên service, tên API, mã lỗi) giữ nguyên tiếng Anh.
- Không dùng icon hoặc emoji.
- Sơ đồ dùng ASCII để xem trực tiếp trong repository và trong diff của Pull Request.
- Thay đổi hành vi hệ thống thì cập nhật tài liệu trong cùng Pull Request.

- [Phân chia nhân sự và phạm vi tính năng (Project Proposal)](PROPOSAL_PHAN_CONG_PHAM_VI.md)
