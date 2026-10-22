# Kế hoạch Sprint

Nhóm làm việc theo Agile Scrum. Sprint 1 dành cho khởi tạo repository và nền tảng; Sprint 2, 3 và 4 kéo dài 3 ngày mỗi sprint.
Mã FRxx là mã yêu cầu chức năng (FR01 đến FR25) trong tài liệu đặc tả của nhóm. Công việc được quản lý trên board theo quy trình
trong [CONTRIBUTING.md](../CONTRIBUTING.md); Daily Standup 20:30 mỗi ngày qua Google Meet; cuối mỗi sprint có buổi demo và
retrospective ngắn.

| Sprint | Thời gian | Trọng tâm |
| --- | --- | --- |
| Sprint 1 | 20/10/2026 - 23/10/2026 | Khởi tạo repository, nền tảng, quy ước |
| Sprint 2 | 24/10/2026 - 26/10/2026 | MVP: xác thực, danh mục hàng hóa, đa kho và vị trí, đơn mua |
| Sprint 3 | 27/10/2026 - 29/10/2026 | MVP: nhập kho, bán và xuất kho, điều chuyển, cảnh báo tồn, Dashboard |
| Sprint 4 | 30/10/2026 - 01/11/2026 | Tính năng nâng cao: kiểm kê, hóa đơn, job hằng ngày, AI, hoàn thiện |

Cuối mỗi sprint, TechLead tạo Pull Request từ `develop` vào `main` và gắn tag `v0.<số sprint>.0`.

## Sprint 1: Khởi tạo (20/10 - 23/10/2026)

Mục tiêu: mọi thành viên chạy được toàn bộ stack trên máy, thống nhất quy ước và vào Sprint 2 mà không bị chặn.

| Hạng mục | Kết quả cần đạt | Phụ trách |
| --- | --- | --- |
| Repository, Git flow, chuẩn API | Cấu trúc repo, README, CONTRIBUTING, template PR và issue, CODEOWNERS, bảo vệ nhánh, `docs/CONVENTIONS.md` | Lê Phú Toàn |
| Nền tảng và Docker | Gradle multi-project 8 module, Config Server, Discovery Server, API Gateway, Dockerfile từng service, `docker-compose.yml`, `init.sql` | Phạm Phương Anh |
| CI | GitHub Actions: backend, frontend, build thử image Docker | Phạm Phương Anh |
| Khung frontend | Layout và menu, Dashboard với dữ liệu mẫu, trang đăng nhập, Axios và router | Trần Đăng Việt |
| Tài liệu yêu cầu | Chốt danh sách yêu cầu chức năng FR01 đến FR25 và yêu cầu phi chức năng | Lê Phương Linh, Trần Đăng Việt |
| Thiết kế dữ liệu | ERD sơ bộ cho từng schema, thống nhất tên bảng và cột theo `docs/CONVENTIONS.md` | Cả nhóm theo phân hệ |

## Sprint 2: MVP đợt 1 (24/10 - 26/10/2026)

Mục tiêu: có người dùng đăng nhập và phân quyền được, có dữ liệu nền (hàng hóa, đối tác, kho, vị trí) và lập được đơn mua.

| Mã | Tính năng | Phụ trách |
| --- | --- | --- |
| FR01 | Đăng nhập, Access Token và Refresh Token, băm mật khẩu BCrypt, khóa tài khoản khi đăng nhập sai nhiều lần | Trần Đăng Việt |
| FR02 | Sáu vai trò RBAC, gán người dùng theo kho, hồ sơ và đổi mật khẩu | Trần Đăng Việt |
| FR03 | Quản lý đa kho | Lê Phú Toàn |
| FR04 | Vị trí lưu trữ Warehouse, Zone, Rack, Bin | Lê Phú Toàn |
| FR05 | Danh mục hàng hóa và SKU, ngưỡng Safety Stock, Max Stock, Reorder Point, import và export Excel | Lê Phương Linh |
| FR06 | Đơn vị tính và quy đổi đa cấp | Lê Phương Linh |
| FR07 | Sinh Barcode và QR cho SKU (phần quét mã tiếp tục ở Sprint 3) | Lê Phương Linh |
| FR09 | Hồ sơ nhà cung cấp | Lê Phương Linh |
| FR10 | Đơn mua (PO), phê duyệt, tăng Incoming Stock | Lê Phương Linh |
| FR14 | Hồ sơ khách hàng, hạn mức công nợ | Lê Phương Linh |
| Nền tảng | Gateway hoàn chỉnh (định tuyến, kiểm tra JWT tập trung, rate limiting), logging hai lớp, kiểm thử tích hợp | Phạm Phương Anh |

## Sprint 3: MVP đợt 2 (27/10 - 29/10/2026)

Mục tiêu: hoàn thành luồng chính mua hàng, bán hàng, kho; tồn kho cập nhật đúng và có cảnh báo; Dashboard dùng dữ liệu thật.

| Mã | Tính năng | Phụ trách |
| --- | --- | --- |
| FR08 | Điều chuyển nội bộ giữa hai kho | Lê Phú Toàn |
| FR21 | Cảnh báo tồn kho an toàn (vàng, đỏ), đề xuất PO | Lê Phú Toàn |
| FR11 | Phiếu nhập kho (GRN) theo PO, nhập từng phần | Lê Phương Linh |
| FR12 | Quản lý lô hàng, hạn dùng, xuất theo FIFO | Lê Phương Linh |
| FR07 | Quét mã vạch khi lập phiếu nhập và xuất | Lê Phương Linh |
| FR15 | Đơn bán (SO), kiểm tra tồn khả dụng ATP | Lê Phương Linh |
| FR16 | Giữ hàng mềm (Soft Reservation) | Lê Phương Linh |
| FR17 | Xuất kho: Picking List và Phiếu xuất (GDN) | Lê Phương Linh |
| FR23 | Dashboard với các thẻ KPI từ dữ liệu thật | Trần Đăng Việt |
| FR24 | Biểu đồ cột Mua vào và Bán ra, donut Trending Items, bảng tổng quan các kho | Trần Đăng Việt |
| FR25 | Recent Invoices và báo cáo Xuất-Nhập-Tồn | Trần Đăng Việt |
| Thông báo | Email cảnh báo tồn kho an toàn | Trần Đăng Việt |
| Nền tảng | Backup MySQL định kỳ, kiểm thử tích hợp luồng PO, GRN, SO, GDN | Phạm Phương Anh |

## Sprint 4: Nâng cao và hoàn thiện (30/10 - 01/11/2026)

Mục tiêu: bổ sung các tính năng nâng cao theo mức độ ưu tiên, kiểm thử toàn hệ thống và chuẩn bị bảo vệ, demo.

| Thành viên | Tính năng |
| --- | --- |
| Lê Phú Toàn | FR19 kiểm kê định kỳ, FR20 điều chỉnh kho có phê duyệt, tối ưu lộ trình lấy hàng, cache tồn kho nâng cao |
| Lê Phương Linh | FR13 hóa đơn mua và công nợ phải trả, FR18 hóa đơn bán và công nợ phải thu, FEFO tự động, in tem máy in nhiệt, email PO và SO kèm PDF |
| Trần Đăng Việt | FR22 job hằng ngày (hàng cận date, dead stock), thẻ kho chi tiết và truy vết lô (phần còn lại của FR25), xuất Excel nâng cao |
| Phạm Phương Anh | AI tóm tắt báo cáo, đẩy thông báo thời gian thực qua WebSocket, giám sát và tối ưu vận hành, kiểm thử toàn hệ thống |
| Cả nhóm | Cập nhật tài liệu, kịch bản demo, tổng kết dự án |

## Tiêu chí hoàn thành MVP

- Chạy trọn vẹn luồng: tạo SKU và kho, lập PO, nhập kho GRN làm tăng tồn, lập SO kiểm tra ATP, xuất kho GDN làm giảm tồn,
  lịch sử giao dịch và thẻ kho ghi nhận đầy đủ.
- Cảnh báo tồn kho thấp xuất hiện trên Dashboard ngay sau giao dịch làm tồn chạm ngưỡng.
- Đăng nhập bằng JWT, phân quyền theo vai trò và theo kho hoạt động đúng với ít nhất ba vai trò.
- Dashboard hiển thị thẻ KPI, hai biểu đồ và bảng giao dịch gần nhất từ dữ liệu thật.
- Toàn bộ hệ thống khởi động bằng một lệnh `docker compose up -d`; pipeline CI xanh trên nhánh `develop` và `main`.

## Quy ước theo dõi

- Mỗi hạng mục được tách thành các thẻ nhỏ trên board, gắn mã task, mã FR và sprint tương ứng.
- Công việc không kịp hoàn thành trong sprint được đưa về Backlog hoặc chuyển sang sprint sau sau khi cả nhóm thống nhất.
- Mốc ngày của Sprint 2 đến Sprint 4 tính liên tiếp ngay sau Sprint 1, mỗi sprint 3 ngày.
