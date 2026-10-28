# Đối chiếu Master Plan và Jira (dự án TASK)

Nguồn Master Plan: Template-Technical1.xlsx, sheet "3. Master Plan" (Phạm Phương Anh lập, 27/10/2026). Jira: https://plinhle26.atlassian.net/jira/software/projects/TASK/list.

Quy ước trên Jira: tiêu đề mỗi issue bắt đầu bằng mã Master Plan (EPIC n, WMS-xx, WMS-xx.y); đầu phần mô tả có khối "Mã Master Plan" ghi nội dung, phụ trách, phối hợp, kế hoạch và giai đoạn; nhãn giai đoạn (Chuan-bi, Sprint-1 đến Sprint-4, UAT-Deploy) và nhãn vai trò (Backend, Frontend, DevOps, QA).

Dùng bảng này để điền cột key Jira vào Master Plan và mục "PHẦN 5: LINK ISSUE" của tài liệu SRS.

| Mã Master Plan | Key Jira | Loại | Người phụ trách | Tiêu đề trên Jira |
|---|---|---|---|---|
| I | [TASK-19](https://plinhle26.atlassian.net/browse/TASK-19) | Epic | Lê Phú Toàn | EPIC 1: HẠ TẦNG MICROSERVICES, DEVOPS & CI/CD |
| WMS-01 | [TASK-2](https://plinhle26.atlassian.net/browse/TASK-2) | Task | Lê Phú Toàn | WMS-01: Khởi tạo Kiến trúc Microservices & Docker Compose |
| WMS-01.1 | [TASK-28](https://plinhle26.atlassian.net/browse/TASK-28) | Subtask | Lê Phú Toàn | WMS-01.1 [DevOps] Viết Dockerfile chuẩn hóa cho FE và BE |
| WMS-01.2 | [TASK-29](https://plinhle26.atlassian.net/browse/TASK-29) | Subtask | Lê Phú Toàn | WMS-01.2 [DevOps] Cấu hình Docker Compose môi trường Dev |
| WMS-01.3 | [TASK-34](https://plinhle26.atlassian.net/browse/TASK-34) | Subtask | Lê Phú Toàn | WMS-01.3 [Backend] Khởi tạo Eureka Discovery & API Gateway |
| WMS-01.4 | [TASK-88](https://plinhle26.atlassian.net/browse/TASK-88) | Subtask | Lê Phú Toàn | WMS-01.4 [DevOps] Thiết lập CI Pipeline với GitHub Actions |
| WMS-02 | [TASK-16](https://plinhle26.atlassian.net/browse/TASK-16) | Task | Lê Phú Toàn | WMS-02: Hạ tầng Trao đổi Sự kiện & Realtime (RabbitMQ & WebSocket) |
| WMS-02.1 | [TASK-82](https://plinhle26.atlassian.net/browse/TASK-82) | Subtask | Lê Phú Toàn | WMS-02.1 [DevOps] Cấu hình cụm RabbitMQ Server & Topics |
| WMS-02.2 | [TASK-83](https://plinhle26.atlassian.net/browse/TASK-83) | Subtask | Lê Phú Toàn | WMS-02.2 [Backend] Cấu hình RabbitMQ Producer/Consumer |
| WMS-02.3 | [TASK-84](https://plinhle26.atlassian.net/browse/TASK-84) | Subtask | Lê Phú Toàn | WMS-02.3 [Backend] Xây dựng WebSocket Hub Server |
| WMS-02.4 | [TASK-89](https://plinhle26.atlassian.net/browse/TASK-89) | Subtask | Lê Phú Toàn | WMS-02.4 [Frontend] Tích hợp WebSocket Client phía Frontend |
| II | [TASK-27](https://plinhle26.atlassian.net/browse/TASK-27) | Epic | Phạm Phương Anh | EPIC 2: XÁC THỰC NGƯỜI DÙNG & PHÂN QUYỀN KHO (AUTH & RBAC) |
| WMS-03 | [TASK-3](https://plinhle26.atlassian.net/browse/TASK-3) | Story | Phạm Phương Anh | WMS-03: Hệ thống Xác thực Tập trung & Cơ chế Bảo mật JWT |
| WMS-03.1 | [TASK-30](https://plinhle26.atlassian.net/browse/TASK-30) | Subtask | Phạm Phương Anh | WMS-03.1 [Backend] Thiết kế CSDL User, Role & Password Hash |
| WMS-03.2 | [TASK-31](https://plinhle26.atlassian.net/browse/TASK-31) | Subtask | Phạm Phương Anh | WMS-03.2 [Backend] Xây dựng API Đăng nhập, Cấp JWT & Refresh Token |
| WMS-03.3 | [TASK-32](https://plinhle26.atlassian.net/browse/TASK-32) | Subtask | Phạm Phương Anh | WMS-03.3 [Backend] Hiện thực cơ chế Chống dò mật khẩu (Brute-Force) |
| WMS-03.4 | [TASK-33](https://plinhle26.atlassian.net/browse/TASK-33) | Subtask | Phạm Phương Anh | WMS-03.4 [Frontend] Xây dựng Màn hình Đăng nhập & Quên mật khẩu |
| WMS-03.5 | [TASK-90](https://plinhle26.atlassian.net/browse/TASK-90) | Subtask | Phạm Phương Anh | WMS-03.5 [Frontend] Cấu hình Axios Interceptor tự động Refresh Token |
| WMS-04 | [TASK-91](https://plinhle26.atlassian.net/browse/TASK-91) | Story | Phạm Phương Anh | WMS-04: Phân quyền Vai trò (RBAC) & Phân quyền theo Kho |
| WMS-04.1 | [TASK-92](https://plinhle26.atlassian.net/browse/TASK-92) | Subtask | Phạm Phương Anh | WMS-04.1 [Backend] API Quản lý User & Gán quyền theo Kho |
| WMS-04.2 | [TASK-93](https://plinhle26.atlassian.net/browse/TASK-93) | Subtask | Phạm Phương Anh | WMS-04.2 [Backend] Bộ lọc dữ liệu theo Kho (Warehouse Security Scope) |
| WMS-04.3 | [TASK-94](https://plinhle26.atlassian.net/browse/TASK-94) | Subtask | Phạm Phương Anh | WMS-04.3 [Frontend] Giao diện Quản trị Người dùng & Phân quyền Kho |
| WMS-04.4 | [TASK-95](https://plinhle26.atlassian.net/browse/TASK-95) | Subtask | Phạm Phương Anh | WMS-04.4 [Frontend] Màn hình Hồ sơ Cá nhân & Đổi mật khẩu |
| III | [TASK-22](https://plinhle26.atlassian.net/browse/TASK-22) | Epic | Lê Phương Linh | EPIC 3: QUẢN LÝ ĐA KHO & SƠ ĐỒ VỊ TRÍ Ô/KỆ |
| WMS-05 | [TASK-4](https://plinhle26.atlassian.net/browse/TASK-4) | Story | Lê Phương Linh | WMS-05: Quản lý Danh mục Đa Kho & Năng lực Chứa |
| WMS-05.1 | [TASK-35](https://plinhle26.atlassian.net/browse/TASK-35) | Subtask | Lê Phương Linh | WMS-05.1 [Backend] Thiết kế CSDL & REST API Quản lý Kho bãi |
| WMS-05.2 | [TASK-36](https://plinhle26.atlassian.net/browse/TASK-36) | Subtask | Lê Phương Linh | WMS-05.2 [Backend] API Thẻ tóm tắt thông số kho (Warehouse Metrics) |
| WMS-05.3 | [TASK-37](https://plinhle26.atlassian.net/browse/TASK-37) | Subtask | Lê Phương Linh | WMS-05.3 [Frontend] Giao diện Danh sách Kho & Thẻ thống kê năng lực |
| WMS-06 | [TASK-5](https://plinhle26.atlassian.net/browse/TASK-5) | Story | Lê Phương Linh | WMS-06: Quản lý Cấu trúc Vị trí Ô/Kệ (Bin Location) & In Mã Vị Trí |
| WMS-06.1 | [TASK-39](https://plinhle26.atlassian.net/browse/TASK-39) | Subtask | Lê Phương Linh | WMS-06.1 [Backend] CSDL & API Cấu trúc vị trí Ô/Kệ 4 cấp |
| WMS-06.2 | [TASK-40](https://plinhle26.atlassian.net/browse/TASK-40) | Subtask | Lê Phương Linh | WMS-06.2 [Backend] API Sinh mã vạch tem vị trí (Bin Barcode) |
| WMS-06.3 | [TASK-41](https://plinhle26.atlassian.net/browse/TASK-41) | Subtask | Lê Phương Linh | WMS-06.3 [Frontend] Giao diện Sơ đồ Vị trí Ô/Kệ trực quan |
| WMS-06.4 | [TASK-42](https://plinhle26.atlassian.net/browse/TASK-42) | Subtask | Lê Phương Linh | WMS-06.4 [Frontend] Chức năng Xem trước & In tem dán Kệ |
| IV | [TASK-21](https://plinhle26.atlassian.net/browse/TASK-21) | Epic | Trần Đăng Việt | EPIC 4: DANH MỤC HÀNG HÓA, MÃ VẠCH & ĐỐI TÁC (MASTER DATA & BARCODE) |
| WMS-07 | [TASK-12](https://plinhle26.atlassian.net/browse/TASK-12) | Story | Trần Đăng Việt | WMS-07: Quản lý Danh mục SKU, Đơn vị Quy đổi & Tem Barcode |
| WMS-07.1 | [TASK-66](https://plinhle26.atlassian.net/browse/TASK-66) | Subtask | Trần Đăng Việt | WMS-07.1 [Backend] CSDL & REST API Danh mục Sản phẩm (SKU) |
| WMS-07.2 | [TASK-67](https://plinhle26.atlassian.net/browse/TASK-67) | Subtask | Trần Đăng Việt | WMS-07.2 [Backend] API Quy đổi Đơn vị tính Đa cấp (UOM Conversion) |
| WMS-07.3 | [TASK-68](https://plinhle26.atlassian.net/browse/TASK-68) | Subtask | Trần Đăng Việt | WMS-07.3 [Backend] Tích hợp Thư viện ZXing sinh Barcode/QR Code |
| WMS-07.4 | [TASK-69](https://plinhle26.atlassian.net/browse/TASK-69) | Subtask | Trần Đăng Việt | WMS-07.4 [Frontend] Màn hình Danh mục Hàng hóa & Cấu hình Đơn vị |
| WMS-07.5 | [TASK-96](https://plinhle26.atlassian.net/browse/TASK-96) | Subtask | Trần Đăng Việt | WMS-07.5 [Frontend] Giao diện In Tem Nhãn Barcode ra máy in nhiệt |
| WMS-08 | [TASK-97](https://plinhle26.atlassian.net/browse/TASK-97) | Story | Trần Đăng Việt | WMS-08: Quản lý Đối tác Chuỗi cung ứng (Nhà cung cấp & Khách hàng) |
| WMS-08.1 | [TASK-98](https://plinhle26.atlassian.net/browse/TASK-98) | Subtask | Trần Đăng Việt | WMS-08.1 [Backend] API Quản lý Nhà cung cấp & Công nợ phải trả |
| WMS-08.2 | [TASK-99](https://plinhle26.atlassian.net/browse/TASK-99) | Subtask | Trần Đăng Việt | WMS-08.2 [Backend] API Quản lý Khách hàng & Hạn mức nợ (Credit Limit) |
| WMS-08.3 | [TASK-100](https://plinhle26.atlassian.net/browse/TASK-100) | Subtask | Trần Đăng Việt | WMS-08.3 [Frontend] Màn hình Quản lý Danh sách Nhà cung cấp |
| WMS-08.4 | [TASK-101](https://plinhle26.atlassian.net/browse/TASK-101) | Subtask | Trần Đăng Việt | WMS-08.4 [Frontend] Màn hình Quản lý Khách hàng & Cảnh báo Công nợ |
| V | [TASK-25](https://plinhle26.atlassian.net/browse/TASK-25) | Epic | Trần Đăng Việt | EPIC 5: CHUỖI CUNG ỨNG NHẬP KHO (INBOUND LOGISTICS & LÔ HÀNG) |
| WMS-09 | [TASK-7](https://plinhle26.atlassian.net/browse/TASK-7) | Story | Trần Đăng Việt | WMS-09: Quy trình Đơn Mua Hàng (PO) & Phê Duyệt |
| WMS-09.1 | [TASK-47](https://plinhle26.atlassian.net/browse/TASK-47) | Subtask | Trần Đăng Việt | WMS-09.1 [Backend] CSDL & API Đơn mua hàng (Purchase Order) |
| WMS-09.2 | [TASK-48](https://plinhle26.atlassian.net/browse/TASK-48) | Subtask | Lê Phương Linh | WMS-09.2 [Backend] Logic Cập nhật Tồn hàng đang về (Incoming Stock) |
| WMS-09.3 | [TASK-49](https://plinhle26.atlassian.net/browse/TASK-49) | Subtask | Trần Đăng Việt | WMS-09.3 [Frontend] Màn hình Lập đơn mua hàng (PO Form) |
| WMS-09.4 | [TASK-50](https://plinhle26.atlassian.net/browse/TASK-50) | Subtask | Trần Đăng Việt | WMS-09.4 [Frontend] Màn hình Quản lý & Phê duyệt Đơn PO |
| WMS-10 | [TASK-102](https://plinhle26.atlassian.net/browse/TASK-102) | Story | Trần Đăng Việt | WMS-10: Phiếu Nhập Kho (GRN), Quét Mã Barcode & Quản Lý Lô (FIFO/FEFO) |
| WMS-10.1 | [TASK-103](https://plinhle26.atlassian.net/browse/TASK-103) | Subtask | Trần Đăng Việt | WMS-10.1 [Backend] API Tạo Phiếu nhập kho (Goods Receipt Note - GRN) |
| WMS-10.2 | [TASK-104](https://plinhle26.atlassian.net/browse/TASK-104) | Subtask | Trần Đăng Việt | WMS-10.2 [Backend] Quản lý Lô (Batch/Lot) & Khóa lô hết hạn |
| WMS-10.3 | [TASK-105](https://plinhle26.atlassian.net/browse/TASK-105) | Subtask | Trần Đăng Việt | WMS-10.3 [Backend] Xử lý cập nhật Tồn kho OnHand & Công nợ mua |
| WMS-10.4 | [TASK-106](https://plinhle26.atlassian.net/browse/TASK-106) | Subtask | Trần Đăng Việt | WMS-10.4 [Frontend] Form Nhập kho Thông minh hỗ trợ Súng bắn mã |
| WMS-10.5 | [TASK-107](https://plinhle26.atlassian.net/browse/TASK-107) | Subtask | Trần Đăng Việt | WMS-10.5 [Frontend] Modal Nhập thông tin Lô hàng & Hạn sử dụng |
| VI | [TASK-23](https://plinhle26.atlassian.net/browse/TASK-23) | Epic | Trần Đăng Việt | EPIC 6: CHUỖI CUNG ỨNG XUẤT KHO (OUTBOUND LOGISTICS & PICKING) |
| WMS-11 | [TASK-9](https://plinhle26.atlassian.net/browse/TASK-9) | Story | Trần Đăng Việt | WMS-11: Đơn Bán Hàng (SO), Tồn Khả Dụng (ATP) & Giữ Hàng Ảo |
| WMS-11.1 | [TASK-55](https://plinhle26.atlassian.net/browse/TASK-55) | Subtask | Trần Đăng Việt | WMS-11.1 [Backend] API Tạo Đơn bán hàng (SO) & Tính toán ATP |
| WMS-11.2 | [TASK-56](https://plinhle26.atlassian.net/browse/TASK-56) | Subtask | Trần Đăng Việt | WMS-11.2 [Backend] Xử lý Giữ hàng ảo (Soft Reservation) khi duyệt SO |
| WMS-11.3 | [TASK-57](https://plinhle26.atlassian.net/browse/TASK-57) | Subtask | Trần Đăng Việt | WMS-11.3 [Frontend] Màn hình Lập đơn bán hàng (SO Form) |
| WMS-11.4 | [TASK-108](https://plinhle26.atlassian.net/browse/TASK-108) | Subtask | Trần Đăng Việt | WMS-11.4 [Frontend] Màn hình Quản lý Đơn bán hàng & Công nợ phải thu |
| WMS-12 | [TASK-10](https://plinhle26.atlassian.net/browse/TASK-10) | Story | Trần Đăng Việt | WMS-12: Xuất Kho Thông Minh (Smart Picking & GDN) & Khóa Bi Quan Chống Âm Kho |
| WMS-12.1 | [TASK-58](https://plinhle26.atlassian.net/browse/TASK-58) | Subtask | Trần Đăng Việt | WMS-12.1 [Backend] Thuật toán Sinh Picking List & Gợi ý FEFO/FIFO |
| WMS-12.2 | [TASK-59](https://plinhle26.atlassian.net/browse/TASK-59) | Subtask | Lê Phương Linh | WMS-12.2 [Backend] API Xác nhận Xuất kho với Khóa bi quan |
| WMS-12.3 | [TASK-60](https://plinhle26.atlassian.net/browse/TASK-60) | Subtask | Trần Đăng Việt | WMS-12.3 [Frontend] Giao diện Lộ trình lấy hàng (Smart Picking View) |
| WMS-12.4 | [TASK-61](https://plinhle26.atlassian.net/browse/TASK-61) | Subtask | Trần Đăng Việt | WMS-12.4 [Frontend] Form Quét mã xác nhận Xuất xưởng & In phiếu GDN |
| VII | [TASK-20](https://plinhle26.atlassian.net/browse/TASK-20) | Epic | Lê Phương Linh | EPIC 7: ĐIỀU CHUYỂN NỘI BỘ, KIỂM KÊ KHO & CẢNH BÁO TỒN |
| WMS-13 | [TASK-13](https://plinhle26.atlassian.net/browse/TASK-13) | Story | Lê Phương Linh | WMS-13: Quy trình Điều Chuyển Hàng Hóa Nội Bộ (Stock Transfer) |
| WMS-13.1 | [TASK-70](https://plinhle26.atlassian.net/browse/TASK-70) | Subtask | Lê Phương Linh | WMS-13.1 [Backend] CSDL & API Luồng Điều chuyển Kho đa trạng thái |
| WMS-13.2 | [TASK-71](https://plinhle26.atlassian.net/browse/TASK-71) | Subtask | Lê Phương Linh | WMS-13.2 [Backend] API Xác nhận Nhận hàng tại Kho đích |
| WMS-13.3 | [TASK-72](https://plinhle26.atlassian.net/browse/TASK-72) | Subtask | Lê Phương Linh | WMS-13.3 [Frontend] Màn hình Lập lệnh Điều chuyển hàng hóa |
| WMS-13.4 | [TASK-111](https://plinhle26.atlassian.net/browse/TASK-111) | Subtask | Lê Phương Linh | WMS-13.4 [Frontend] Form Xác nhận Nhận hàng & Lập biên bản sự cố |
| WMS-14 | [TASK-109](https://plinhle26.atlassian.net/browse/TASK-109) | Story | Lê Phương Linh | WMS-14: Kiểm Kê Định Kỳ (Stocktake) & Phiếu Điều Chỉnh Cân Đối Kho |
| WMS-14.1 | [TASK-112](https://plinhle26.atlassian.net/browse/TASK-112) | Subtask | Lê Phương Linh | WMS-14.1 [Backend] API Tạo đợt Kiểm kê & Cơ chế Đóng băng (Freeze Snapshot) |
| WMS-14.2 | [TASK-113](https://plinhle26.atlassian.net/browse/TASK-113) | Subtask | Lê Phương Linh | WMS-14.2 [Backend] API Tính chênh lệch & Phê duyệt Phiếu Điều chỉnh (Adjustment) |
| WMS-14.3 | [TASK-114](https://plinhle26.atlassian.net/browse/TASK-114) | Subtask | Lê Phương Linh | WMS-14.3 [Frontend] Giao diện Kiểm đếm mù (Blind Count) cho máy quét |
| WMS-14.4 | [TASK-115](https://plinhle26.atlassian.net/browse/TASK-115) | Subtask | Lê Phương Linh | WMS-14.4 [Frontend] Màn hình Đối soát Chênh lệch & Duyệt Cân đối tồn kho |
| WMS-15 | [TASK-110](https://plinhle26.atlassian.net/browse/TASK-110) | Story | Phạm Phương Anh | WMS-15: Hệ Thống Cảnh Báo Tồn An Toàn, Cận Date & Email Tự Động |
| WMS-15.1 | [TASK-116](https://plinhle26.atlassian.net/browse/TASK-116) | Subtask | Phạm Phương Anh | WMS-15.1 [Backend] Scheduled Job Quét Hàng Cận Date & Hàng Chết |
| WMS-15.2 | [TASK-117](https://plinhle26.atlassian.net/browse/TASK-117) | Subtask | Phạm Phương Anh | WMS-15.2 [Backend] Tích hợp Spring Boot Starter Mail gửi cảnh báo |
| WMS-15.3 | [TASK-118](https://plinhle26.atlassian.net/browse/TASK-118) | Subtask | Lê Phương Linh | WMS-15.3 [Frontend] Bảng Tồn kho Realtime gắn Tag Màu Cảnh báo |
| WMS-15.4 | [TASK-119](https://plinhle26.atlassian.net/browse/TASK-119) | Subtask | Phạm Phương Anh | WMS-15.4 [Frontend] Notification Popover trên Header ứng dụng |
| VIII | [TASK-18](https://plinhle26.atlassian.net/browse/TASK-18) | Epic | Phạm Phương Anh | EPIC 8: BÁO CÁO, GIÁM SÁT ĐIỀU HÀNH & AI ASSISTANT |
| WMS-16 | [TASK-14](https://plinhle26.atlassian.net/browse/TASK-14) | Story | Phạm Phương Anh | WMS-16: Executive Dashboard Thời Gian Thực (KPIs & Biểu Đồ Recharts) |
| WMS-16.1 | [TASK-73](https://plinhle26.atlassian.net/browse/TASK-73) | Subtask | Phạm Phương Anh | WMS-16.1 [Backend] API Tổng hợp Dữ liệu 8 Thẻ KPI điều hành |
| WMS-16.2 | [TASK-74](https://plinhle26.atlassian.net/browse/TASK-74) | Subtask | Phạm Phương Anh | WMS-16.2 [Backend] API Thống kê Biểu đồ Mua/Bán & Tỷ trọng kho |
| WMS-16.3 | [TASK-75](https://plinhle26.atlassian.net/browse/TASK-75) | Subtask | Phạm Phương Anh | WMS-16.3 [Frontend] Xây dựng hàng 8 Thẻ KPI tương tác (Click to Filter) |
| WMS-16.4 | [TASK-76](https://plinhle26.atlassian.net/browse/TASK-76) | Subtask | Phạm Phương Anh | WMS-16.4 [Frontend] Xây dựng Biểu đồ Cột Đôi & Biểu đồ Donut Recharts |
| WMS-17 | [TASK-15](https://plinhle26.atlassian.net/browse/TASK-15) | Story | Phạm Phương Anh | WMS-17: Báo Cáo Sổ Kho, Thẻ Kho & Truy Vết Nguồn Gốc Lô (Traceability) |
| WMS-17.1 | [TASK-77](https://plinhle26.atlassian.net/browse/TASK-77) | Subtask | Phạm Phương Anh | WMS-17.1 [Backend] API Báo cáo Xuất - Nhập - Tồn & Thẻ Kho chi tiết |
| WMS-17.2 | [TASK-78](https://plinhle26.atlassian.net/browse/TASK-78) | Subtask | Phạm Phương Anh | WMS-17.2 [Backend] API Truy vết Nguồn gốc theo Số Lô (Traceability) |
| WMS-17.3 | [TASK-79](https://plinhle26.atlassian.net/browse/TASK-79) | Subtask | Trần Đăng Việt | WMS-17.3 [Backend] Tích hợp Thư viện Apache POI xuất file Excel |
| WMS-17.4 | [TASK-80](https://plinhle26.atlassian.net/browse/TASK-80) | Subtask | Phạm Phương Anh | WMS-17.4 [Frontend] Màn hình Báo cáo Sổ Kho, Thẻ Kho & Tải File Excel |
| WMS-17.5 | [TASK-81](https://plinhle26.atlassian.net/browse/TASK-81) | Subtask | Phạm Phương Anh | WMS-17.5 [Frontend] Giao diện Tra cứu & Hiển thị Cây Truy vết Nguồn gốc |
| WMS-18 | [TASK-17](https://plinhle26.atlassian.net/browse/TASK-17) | Story | Lê Phú Toàn | WMS-18: Tích Hợp AI Assistant (Tóm Tắt Báo Cáo & Chatbot Hỗ Trợ) |
| WMS-18.1 | [TASK-85](https://plinhle26.atlassian.net/browse/TASK-85) | Subtask | Lê Phú Toàn | WMS-18.1 [Backend] Tích hợp Spring AI & Thiết kế Prompt tóm tắt |
| WMS-18.2 | [TASK-86](https://plinhle26.atlassian.net/browse/TASK-86) | Subtask | Lê Phú Toàn | WMS-18.2 [Backend] API Chatbot tra cứu thông tin kho thông minh |
| WMS-18.3 | [TASK-87](https://plinhle26.atlassian.net/browse/TASK-87) | Subtask | Lê Phú Toàn | WMS-18.3 [Frontend] Widget AI Summary trên Executive Dashboard |
| WMS-18.4 | [TASK-120](https://plinhle26.atlassian.net/browse/TASK-120) | Subtask | Lê Phú Toàn | WMS-18.4 [Frontend] Popup Giao diện Chatbot AI hỗ trợ người dùng |
| IX | [TASK-24](https://plinhle26.atlassian.net/browse/TASK-24) | Epic | Lê Phú Toàn | EPIC 9: KIỂM THỬ (BACKUP WEEK) |
| WMS-19 | [TASK-8](https://plinhle26.atlassian.net/browse/TASK-8) | Task | Lê Phương Linh | WMS-19: Tối Ưu Hiệu Năng, Caching & Xử Lý Đồng Thời |
| WMS-19.1 | [TASK-51](https://plinhle26.atlassian.net/browse/TASK-51) | Subtask | Lê Phương Linh | WMS-19.1 [Backend] Cấu hình Redis Cache cho Dữ liệu Tồn kho khả dụng |
| WMS-19.2 | [TASK-52](https://plinhle26.atlassian.net/browse/TASK-52) | Subtask | Lê Phú Toàn | WMS-19.2 [QA] Kiểm thử Tải đồng thời & Chống Deadlock (JMeter) |
| WMS-19.3 | [TASK-53](https://plinhle26.atlassian.net/browse/TASK-53) | Subtask | Lê Phương Linh | WMS-19.3 [Backend] Tối ưu hóa Database Queries & Đánh chỉ mục Index |
| WMS-20 | [TASK-121](https://plinhle26.atlassian.net/browse/TASK-121) | Task | Lê Phú Toàn | WMS-20: Kiểm Thử Toàn Diện (End-to-End Testing), UAT & Khắc Phục Lỗi |
| WMS-20.1 | [TASK-123](https://plinhle26.atlassian.net/browse/TASK-123) | Subtask | Trần Đăng Việt | WMS-20.1 [QA] Kiểm thử E2E Luồng Inbound & Quản lý Lô FIFO/FEFO |
| WMS-20.2 | [TASK-124](https://plinhle26.atlassian.net/browse/TASK-124) | Subtask | Lê Phương Linh | WMS-20.2 [QA] Kiểm thử E2E Luồng Outbound & Điều chuyển, Kiểm kê |
| WMS-20.3 | [TASK-125](https://plinhle26.atlassian.net/browse/TASK-125) | Subtask | Phạm Phương Anh | WMS-20.3 [QA] Kiểm thử Bảo mật, Phân quyền Kho & Giao diện Responsive |
| WMS-20.4 | [TASK-126](https://plinhle26.atlassian.net/browse/TASK-126) | Subtask | Cả 4 thành viên | WMS-20.4 [ALL] Fix Bugs & Tinh chỉnh trải nghiệm người dùng (Bugfix) |
| WMS-21 | [TASK-122](https://plinhle26.atlassian.net/browse/TASK-122) | Task | Lê Phú Toàn | WMS-21: Hoàn Thiện Tài Liệu, Đóng Gói Release & Nghiệm Thu |
| WMS-21.1 | [TASK-127](https://plinhle26.atlassian.net/browse/TASK-127) | Subtask | Lê Phú Toàn | WMS-21.1 [Backend] Tự động sinh tài liệu Swagger / OpenAPI cho tất cả Service |
| WMS-21.2 | [TASK-128](https://plinhle26.atlassian.net/browse/TASK-128) | Subtask | Lê Phú Toàn | WMS-21.2 [DevOps] Viết Hướng dẫn Cài đặt & Vận hành (README & Docs) |
| WMS-21.3 | [TASK-129](https://plinhle26.atlassian.net/browse/TASK-129) | Subtask | Lê Phú Toàn | WMS-21.3 [DevOps] Đóng gói Docker Images chính thức & Deploy Production |
| WMS-21.4 | [TASK-130](https://plinhle26.atlassian.net/browse/TASK-130) | Subtask | Cả 4 thành viên | WMS-21.4 [ALL] Tổng kết Sprint Retrospective & Bàn giao Dự án |

## Ghi chú

- Nhóm thống nhất ngày 28/10/2026 lấy Master Plan làm chuẩn cho người phụ trách. Năm issue cần gán đúng theo Master Plan: TASK-23 (Epic VI), TASK-10 (WMS-12) và TASK-58 (WMS-12.1) cho Trần Đăng Việt; TASK-18 (Epic VIII) cho Phạm Phương Anh; TASK-48 (WMS-09.2) cho Lê Phương Linh.
- WMS-20.4 và WMS-21.4 trong Master Plan ghi "Cả 4 thành viên"; trên Jira gán cho Lê Phú Toàn (TASK-126) và Lê Phương Linh (TASK-130) làm đầu mối.
- TASK-1 (Xây dựng Project Proposal, Done) nằm ngoài Master Plan, giữ nguyên để lưu vết giai đoạn 1.
- Tiêu đề gốc do Lê Phương Linh đặt được lưu trong jira_mapping_backup.txt.
