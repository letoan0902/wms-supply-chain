# Kế hoạch đưa Master Plan lên Jira (dự án TASK)

Nguồn: Template-Technical1.xlsx, sheet 3. Master Plan (người lập: Phạm Phương Anh). Giai đoạn 27/10/2026 - 23/11/2026.

Số lượng: 9 Epic, 21 Task, 86 Subtask (tổng 116 issue).

## Số subtask theo người phụ trách

| Thành viên | Subtask |
|---|---|
| Trần Đăng Việt | 26 |
| Phạm Phương Anh | 21 |
| Lê Phương Linh | 21 |
| Lê Phú Toàn | 16 |
| Cả 4 thành viên | 2 |

## Cây công việc

| Mã | Loại | Summary trên Jira | Phụ trách | Bắt đầu | Kết thúc | Nhãn |
|---|---|---|---|---|---|---|
| I | Epic | EPIC 1: HẠ TẦNG MICROSERVICES, DEVOPS & CI/CD | Lê Phú Toàn | 27/10/2026 | 06/11/2026 | Chuan-bi, Sprint-1, Sprint-2, Sprint-3 |
| WMS-01 | Task | - WMS-01: Khởi tạo Kiến trúc Microservices, Git Flow & Docker Compose | Lê Phú Toàn | 27/10/2026 | 30/10/2026 | Chuan-bi, Sprint-1 |
| WMS-01.1 | Subtask | - - WMS-01.1 [DevOps] Thiết lập Git Flow & Viết Dockerfile Multi-stage cho FE/BE | Lê Phú Toàn | 27/10/2026 | 28/10/2026 | Chuan-bi, Sprint-1, DevOps |
| WMS-01.2 | Subtask | - - WMS-01.2 [DevOps] Cấu hình Docker-Compose Skeleton & Flyway / Actuator | Lê Phú Toàn | 28/10/2026 | 29/10/2026 | Chuan-bi, Sprint-1, DevOps |
| WMS-01.3 | Subtask | - - WMS-01.3 [Backend] Khởi tạo Config, Eureka Discovery & API-Gateway (JWT Filter) | Lê Phú Toàn | 29/10/2026 | 30/10/2026 | Chuan-bi, Sprint-1, Backend |
| WMS-01.4 | Subtask | - - WMS-01.4 [DevOps] Thiết lập Đường ống CI/CD với GitHub Actions | Lê Phú Toàn | 30/10/2026 | 30/10/2026 | Chuan-bi, Sprint-1, DevOps |
| WMS-02 | Task | - WMS-02: Hạ tầng Trao đổi Sự kiện RabbitMQ & Realtime WebSocket Hub | Lê Phú Toàn | 03/11/2026 | 06/11/2026 | Sprint-2, Sprint-3 |
| WMS-02.1 | Subtask | - - WMS-02.1 [DevOps] Cấu hình RabbitMQ Server & Topic (order.created, order.dispatched) | Lê Phú Toàn | 03/11/2026 | 04/11/2026 | Sprint-2, Sprint-3, DevOps |
| WMS-02.2 | Subtask | - - WMS-02.2 [Backend] Cấu hình RabbitMQ Producer/Consumer & Giao tiếp OpenFeign | Lê Phú Toàn | 04/11/2026 | 05/11/2026 | Sprint-2, Sprint-3, Backend |
| WMS-02.3 | Subtask | - - WMS-02.3 [Backend] Xây dựng WebSocket Hub đẩy cảnh báo 'Low-stock' & KPI | Lê Phú Toàn | 05/11/2026 | 06/11/2026 | Sprint-2, Sprint-3, Backend |
| WMS-02.4 | Subtask | - - WMS-02.4 [Frontend] Tích hợp WebSocket Client & Zustand State trên React UI | Lê Phú Toàn | 06/11/2026 | 06/11/2026 | Sprint-2, Sprint-3, Frontend |
| II | Epic | EPIC 2: XÁC THỰC NGƯỜI DÙNG & PHÂN QUYỀN KHO (AUTH & RBAC) | Phạm Phương Anh | 27/10/2026 | 02/11/2026 | Sprint-1 |
| WMS-03 | Task | - WMS-03: Hệ thống Xác thực Tập trung (Auth Service) & Cơ chế Bảo mật JWT | Phạm Phương Anh | 27/10/2026 | 30/10/2026 | Sprint-1 |
| WMS-03.1 | Subtask | - - WMS-03.1 [Backend] Thiết kế CSDL User, Enum UserRole & Mã hóa BCrypt | Phạm Phương Anh | 27/10/2026 | 28/10/2026 | Sprint-1, Backend |
| WMS-03.2 | Subtask | - - WMS-03.2 [Backend] Xây dựng API Register/Login, Cấp JWT & Refresh Token | Phạm Phương Anh | 28/10/2026 | 29/10/2026 | Sprint-1, Backend |
| WMS-03.3 | Subtask | - - WMS-03.3 [Backend] Hiện thực cơ chế Chống dò mật khẩu (Brute-Force Protection) | Phạm Phương Anh | 29/10/2026 | 29/10/2026 | Sprint-1, Backend |
| WMS-03.4 | Subtask | - - WMS-03.4 [Frontend] Xây dựng Màn hình Đăng nhập & Quên mật khẩu | Phạm Phương Anh | 29/10/2026 | 30/10/2026 | Sprint-1, Frontend |
| WMS-03.5 | Subtask | - - WMS-03.5 [Frontend] Cấu hình Axios Interceptor JWT & Tự động Refresh Token | Phạm Phương Anh | 30/10/2026 | 30/10/2026 | Sprint-1, Frontend |
| WMS-04 | Task | - WMS-04: Phân quyền Vai trò (RBAC) & Quản lý Tài khoản Nhân viên theo Kho | Phạm Phương Anh | 31/10/2026 | 02/11/2026 | Sprint-1 |
| WMS-04.1 | Subtask | - - WMS-04.1 [Backend] API Quản lý Tài khoản Nhân viên & Gán quyền theo Kho | Phạm Phương Anh | 31/10/2026 | 01/11/2026 | Sprint-1, Backend |
| WMS-04.2 | Subtask | - - WMS-04.2 [Backend] Bộ lọc Dữ liệu theo Kho được cấp quyền (Warehouse Scope Filter) | Phạm Phương Anh | 01/11/2026 | 01/11/2026 | Sprint-1, Backend |
| WMS-04.3 | Subtask | - - WMS-04.3 [Frontend] Trang Quản lý User & Cấp quyền Vai trò / Kho | Phạm Phương Anh | 01/11/2026 | 02/11/2026 | Sprint-1, Frontend |
| WMS-04.4 | Subtask | - - WMS-04.4 [Frontend] Màn hình Cập nhật Hồ sơ Cá nhân & Đổi mật khẩu an toàn | Phạm Phương Anh | 02/11/2026 | 02/11/2026 | Sprint-1, Frontend |
| III | Epic | EPIC 3: QUẢN LÝ ĐA KHO & SƠ ĐỒ VỊ TRÍ Ô/KỆ (WAREHOUSE & BIN LOCATION) | Lê Phương Linh | 27/10/2026 | 02/11/2026 | Sprint-1 |
| WMS-05 | Task | - WMS-05: Quản lý Danh mục Đa Kho & Giám sát Sức chứa (Warehouse Service) | Lê Phương Linh | 27/10/2026 | 29/10/2026 | Sprint-1 |
| WMS-05.1 | Subtask | - - WMS-05.1 [Backend] Thiết kế CSDL & REST API CRUD Đa Kho (Multi-warehouse) | Lê Phương Linh | 27/10/2026 | 28/10/2026 | Sprint-1, Backend |
| WMS-05.2 | Subtask | - - WMS-05.2 [Backend] API Thẻ tóm tắt nhanh tình trạng Kho (Warehouse Summary Cards) | Lê Phương Linh | 28/10/2026 | 28/10/2026 | Sprint-1, Backend |
| WMS-05.3 | Subtask | - - WMS-05.3 [Frontend] Giao diện Danh sách Kho & Thẻ tóm tắt Sức chứa | Lê Phương Linh | 28/10/2026 | 29/10/2026 | Sprint-1, Frontend |
| WMS-06 | Task | - WMS-06: Quản lý Vị trí Ô/Kệ (Bin Location) & In Tem Mã Vạch Giá Kệ | Lê Phương Linh | 30/10/2026 | 02/11/2026 | Sprint-1 |
| WMS-06.1 | Subtask | - - WMS-06.1 [Backend] Thiết kế CSDL & REST API Vị trí Ô/Kệ 4 cấp (Bin Location) | Lê Phương Linh | 30/10/2026 | 31/10/2026 | Sprint-1, Backend |
| WMS-06.2 | Subtask | - - WMS-06.2 [Backend] API Sinh mã vạch Vị trí Ô/Kệ để in tem dán kệ | Lê Phương Linh | 31/10/2026 | 31/10/2026 | Sprint-1, Backend |
| WMS-06.3 | Subtask | - - WMS-06.3 [Frontend] Xây dựng Giao diện Sơ đồ Vị trí Ô/Kệ (Bin Management) | Lê Phương Linh | 01/11/2026 | 02/11/2026 | Sprint-1, Frontend |
| WMS-06.4 | Subtask | - - WMS-06.4 [Frontend] Chức năng Xem trước & In Tem Mã Vạch Vị trí Giá Kệ | Lê Phương Linh | 02/11/2026 | 02/11/2026 | Sprint-1, Frontend |
| IV | Epic | EPIC 4: DANH MỤC HÀNG HÓA, MÃ VẠCH & ĐỐI TÁC (PRODUCT SERVICE & PARTNERS) | Trần Đăng Việt | 27/10/2026 | 02/11/2026 | Sprint-1 |
| WMS-07 | Task | - WMS-07: Quản lý Danh mục SKU, Đơn vị Quy đổi & Tích hợp Barcode (Product Service) | Trần Đăng Việt | 27/10/2026 | 30/10/2026 | Sprint-1 |
| WMS-07.1 | Subtask | - - WMS-07.1 [Backend] Thiết kế CSDL & REST API Danh mục Sản phẩm (SKU) | Trần Đăng Việt | 27/10/2026 | 28/10/2026 | Sprint-1, Backend |
| WMS-07.2 | Subtask | - - WMS-07.2 [Backend] Phát triển API Đơn vị tính & Quy đổi đa cấp (UOM Conversion) | Trần Đăng Việt | 28/10/2026 | 29/10/2026 | Sprint-1, Backend |
| WMS-07.3 | Subtask | - - WMS-07.3 [Backend] Tích hợp Thư viện ZXing tự động sinh ảnh Barcode / QR Code | Trần Đăng Việt | 29/10/2026 | 29/10/2026 | Sprint-1, Backend |
| WMS-07.4 | Subtask | - - WMS-07.4 [Frontend] Xây dựng Màn hình Quản lý Danh mục SKU & Đơn vị quy đổi | Trần Đăng Việt | 29/10/2026 | 30/10/2026 | Sprint-1, Frontend |
| WMS-07.5 | Subtask | - - WMS-07.5 [Frontend] Chức năng Tạo & In Tem Nhãn Sản phẩm Barcode ra máy in nhiệt | Trần Đăng Việt | 30/10/2026 | 30/10/2026 | Sprint-1, Frontend |
| WMS-08 | Task | - WMS-08: Quản lý Hồ sơ Nhà Cung Cấp (Suppliers) & Khách Hàng (Customers) | Trần Đăng Việt | 31/10/2026 | 02/11/2026 | Sprint-1, Sprint-2 |
| WMS-08.1 | Subtask | - - WMS-08.1 [Backend] Thiết kế CSDL & REST API Quản lý Nhà cung cấp (Suppliers) | Trần Đăng Việt | 31/10/2026 | 01/11/2026 | Sprint-1, Sprint-2, Backend |
| WMS-08.2 | Subtask | - - WMS-08.2 [Backend] Thiết kế CSDL & REST API Quản lý Khách hàng / Đại lý (Customers) | Trần Đăng Việt | 01/11/2026 | 01/11/2026 | Sprint-1, Sprint-2, Backend |
| WMS-08.3 | Subtask | - - WMS-08.3 [Frontend] Xây dựng Màn hình Quản lý Danh sách Nhà cung cấp & Công nợ | Trần Đăng Việt | 01/11/2026 | 02/11/2026 | Sprint-1, Sprint-2, Frontend |
| WMS-08.4 | Subtask | - - WMS-08.4 [Frontend] Xây dựng Màn hình Quản lý Danh sách Khách hàng / Đại lý | Trần Đăng Việt | 02/11/2026 | 02/11/2026 | Sprint-1, Sprint-2, Frontend |
| V | Epic | EPIC 5: CHUỖI CUNG ỨNG MUA HÀNG & NHẬP KHO (INBOUND LOGISTICS: PO → GRN) | Trần Đăng Việt | 03/11/2026 | 09/11/2026 | Sprint-2 |
| WMS-09 | Task | - WMS-09: Quy trình Đơn Mua Hàng (PO), Phê Duyệt & Cập Nhật 'Incoming Stock' | Trần Đăng Việt | 03/11/2026 | 05/11/2026 | Sprint-2 |
| WMS-09.1 | Subtask | - - WMS-09.1 [Backend] Thiết kế CSDL & API Đơn mua hàng (Purchase Order - PO) | Trần Đăng Việt | 03/11/2026 | 04/11/2026 | Sprint-2, Backend |
| WMS-09.2 | Subtask | - - WMS-09.2 [Backend] Xử lý Cập nhật chỉ tiêu 'Incoming Stock' khi duyệt PO (Warehouse Service) | Lê Phương Linh | 04/11/2026 | 04/11/2026 | Sprint-2, Backend |
| WMS-09.3 | Subtask | - - WMS-09.3 [Frontend] Xây dựng Giao diện Form Lập Đơn Mua Hàng (Form PO) | Trần Đăng Việt | 04/11/2026 | 05/11/2026 | Sprint-2, Frontend |
| WMS-09.4 | Subtask | - - WMS-09.4 [Frontend] Màn hình Danh sách PO Chờ Duyệt & Theo dõi Đơn Mua | Trần Đăng Việt | 05/11/2026 | 05/11/2026 | Sprint-2, Frontend |
| WMS-10 | Task | - WMS-10: Phiếu Nhập Kho (GRN), Quét Mã Vạch & Quản Lý Lô / Hạn Dùng | Trần Đăng Việt | 06/11/2026 | 09/11/2026 | Sprint-2 |
| WMS-10.1 | Subtask | - - WMS-10.1 [Backend] Phát triển API Phiếu nhập kho (PO → GRN, hỗ trợ Partial Receipt) | Trần Đăng Việt | 06/11/2026 | 07/11/2026 | Sprint-2, Backend |
| WMS-10.2 | Subtask | - - WMS-10.2 [Backend] Quản lý Lô (Batch/Lot), Hạn sử dụng (Exp Date) & Khóa lô hết hạn | Trần Đăng Việt | 07/11/2026 | 07/11/2026 | Sprint-2, Backend |
| WMS-10.3 | Subtask | - - WMS-10.3 [Backend] Cập nhật OnHand Stock, Giảm Incoming Stock & Hóa đơn mua (Purchase Bill) | Trần Đăng Việt | 08/11/2026 | 08/11/2026 | Sprint-2, Backend |
| WMS-10.4 | Subtask | - - WMS-10.4 [Frontend] Form Lập Phiếu Nhập Kho Thông Minh (Quét mã vạch cộng dồn SL) | Trần Đăng Việt | 08/11/2026 | 09/11/2026 | Sprint-2, Frontend |
| WMS-10.5 | Subtask | - - WMS-10.5 [Frontend] Giao diện Nhập Số Lô, Hạn Sử Dụng & Theo dõi Hóa đơn mua | Trần Đăng Việt | 09/11/2026 | 09/11/2026 | Sprint-2, Frontend |
| VI | Epic | EPIC 6: CHUỖI CUNG ỨNG BÁN HÀNG & XUẤT KHO (OUTBOUND: SO → GDN, FIFO/FEFO) | Trần Đăng Việt | 03/11/2026 | 09/11/2026 | Sprint-3 |
| WMS-11 | Task | - WMS-11: Đơn Bán Hàng (SO), Kiểm Tra Tồn Khả Dụng (ATP) & Giữ Hàng Ảo | Trần Đăng Việt | 03/11/2026 | 05/11/2026 | Sprint-3 |
| WMS-11.1 | Subtask | - - WMS-11.1 [Backend] Phát triển API Đơn bán hàng (SO) & Kiểm tra Tồn khả dụng (ATP) | Trần Đăng Việt | 03/11/2026 | 04/11/2026 | Sprint-3, Backend |
| WMS-11.2 | Subtask | - - WMS-11.2 [Backend] Cơ chế Giữ hàng ảo (Soft Reservation) khi xác nhận Đơn bán SO | Trần Đăng Việt | 04/11/2026 | 04/11/2026 | Sprint-3, Backend |
| WMS-11.3 | Subtask | - - WMS-11.3 [Frontend] Xây dựng Giao diện Form Lập Đơn Bán Hàng (Form SO) | Trần Đăng Việt | 04/11/2026 | 05/11/2026 | Sprint-3, Frontend |
| WMS-11.4 | Subtask | - - WMS-11.4 [Frontend] Màn hình Quản lý Đơn Bán SO, Hóa đơn bán & Công nợ phải thu | Trần Đăng Việt | 05/11/2026 | 05/11/2026 | Sprint-3, Frontend |
| WMS-12 | Task | - WMS-12: Quy trình Xuất Kho (SO → GDN, FIFO/FEFO) & Khóa Bi Quan Chống Âm Kho | Trần Đăng Việt | 06/11/2026 | 09/11/2026 | Sprint-3 |
| WMS-12.1 | Subtask | - - WMS-12.1 [Backend] API Xuất kho (SO → GDN), Sinh Picking List & Thuật toán FIFO/FEFO | Trần Đăng Việt | 06/11/2026 | 07/11/2026 | Sprint-3, Backend |
| WMS-12.2 | Subtask | - - WMS-12.2 [Backend] Xử lý Trừ tồn kho với Khóa bi quan (PESSIMISTIC_WRITE) chống âm kho | Lê Phương Linh | 07/11/2026 | 08/11/2026 | Sprint-3, Backend |
| WMS-12.3 | Subtask | - - WMS-12.3 [Frontend] Giao diện Bảng Xuất Thời Gian Thực & Danh sách Lấy hàng (Picking List) | Trần Đăng Việt | 08/11/2026 | 08/11/2026 | Sprint-3, Frontend |
| WMS-12.4 | Subtask | - - WMS-12.4 [Frontend] Form Lập Phiếu Xuất Kho (GDN) Quét Mã Vạch & In Phiếu Giao Hàng | Trần Đăng Việt | 08/11/2026 | 09/11/2026 | Sprint-3, Frontend |
| VII | Epic | EPIC 7: ĐIỀU CHUYỂN NỘI BỘ, KIỂM KÊ KHO & CẢNH BÁO TỰ ĐỘNG | Lê Phương Linh | 10/11/2026 | 16/11/2026 | Sprint-3, Sprint-4 |
| WMS-13 | Task | - WMS-13: Quy trình Điều Chuyển Hàng Hóa Nội Bộ Giữa Các Kho (Stock Transfer) | Lê Phương Linh | 10/11/2026 | 12/11/2026 | Sprint-3 |
| WMS-13.1 | Subtask | - - WMS-13.1 [Backend] Thiết kế CSDL & REST API Điều chuyển hàng hóa nội bộ (Stock Transfer) | Lê Phương Linh | 10/11/2026 | 11/11/2026 | Sprint-3, Backend |
| WMS-13.2 | Subtask | - - WMS-13.2 [Backend] API Nhận hàng tại Kho đích & Xử lý Biên bản Tổn thất (Partial Received) | Lê Phương Linh | 11/11/2026 | 11/11/2026 | Sprint-3, Backend |
| WMS-13.3 | Subtask | - - WMS-13.3 [Frontend] Xây dựng Màn hình Lập Lệnh Điều Chuyển Hàng Hóa Nội Bộ | Lê Phương Linh | 11/11/2026 | 12/11/2026 | Sprint-3, Frontend |
| WMS-13.4 | Subtask | - - WMS-13.4 [Frontend] Giao diện Xác nhận Nhận hàng Điều chuyển & Lập biên bản thiếu/hỏng | Lê Phương Linh | 12/11/2026 | 12/11/2026 | Sprint-3, Frontend |
| WMS-14 | Task | - WMS-14: Kiểm Kê Kho Định Kỳ (Stocktake) & Phiếu Điều Chỉnh Cân Đối (Adjustment) | Lê Phương Linh | 13/11/2026 | 15/11/2026 | Sprint-3 |
| WMS-14.1 | Subtask | - - WMS-14.1 [Backend] Phát triển REST API Kế hoạch Kiểm kê & Đóng băng vị trí (Freeze Snapshot) | Lê Phương Linh | 13/11/2026 | 13/11/2026 | Sprint-3, Backend |
| WMS-14.2 | Subtask | - - WMS-14.2 [Backend] API Tính chênh lệch Kiểm kê & Phê duyệt Phiếu Điều chỉnh (Adjustment) | Lê Phương Linh | 13/11/2026 | 14/11/2026 | Sprint-3, Backend |
| WMS-14.3 | Subtask | - - WMS-14.3 [Frontend] Xây dựng Giao diện Kiểm đếm thực tế khi Kiểm kê (Blind Count Scanner) | Lê Phương Linh | 14/11/2026 | 14/11/2026 | Sprint-3, Frontend |
| WMS-14.4 | Subtask | - - WMS-14.4 [Frontend] Màn hình Báo cáo Chênh lệch Kiểm kê & Phê duyệt Phiếu Adjustment | Lê Phương Linh | 14/11/2026 | 15/11/2026 | Sprint-3, Frontend |
| WMS-15 | Task | - WMS-15: Cảnh Báo Tồn An Toàn, Cảnh Báo Cận Date & Tích Hợp Spring Mail | Phạm Phương Anh | 13/11/2026 | 16/11/2026 | Sprint-3, Sprint-4 |
| WMS-15.1 | Subtask | - - WMS-15.1 [Backend] Automation Cron Job (00:00) kiểm tra Hàng Cận Date & Dead Stock | Phạm Phương Anh | 13/11/2026 | 14/11/2026 | Sprint-3, Sprint-4, Backend |
| WMS-15.2 | Subtask | - - WMS-15.2 [Backend] Tích hợp Spring Mail gửi Email Cảnh báo Tồn thấp & Cận hạn sử dụng | Phạm Phương Anh | 14/11/2026 | 15/11/2026 | Sprint-3, Sprint-4, Backend |
| WMS-15.3 | Subtask | - - WMS-15.3 [Frontend] Xây dựng Bảng Tồn Kho Thời Gian Thực kèm Bộ lọc & Tag Màu Cảnh Báo | Lê Phương Linh | 15/11/2026 | 15/11/2026 | Sprint-3, Sprint-4, Frontend |
| WMS-15.4 | Subtask | - - WMS-15.4 [Frontend] Cụm Chuông Thông báo Cảnh báo (Notification Popover) trên Header | Phạm Phương Anh | 15/11/2026 | 16/11/2026 | Sprint-3, Sprint-4, Frontend |
| VIII | Epic | EPIC 8: BÁO CÁO THỐNG KÊ, EXECUTIVE DASHBOARD, EXPORT EXCEL & AI ASSISTANT | Phạm Phương Anh | 10/11/2026 | 16/11/2026 | Sprint-4 |
| WMS-16 | Task | - WMS-16: Xây dựng Admin / Executive Dashboard Tổng Quan (KPI Cards & Biểu Đồ Recharts) | Phạm Phương Anh | 10/11/2026 | 13/11/2026 | Sprint-4 |
| WMS-16.1 | Subtask | - - WMS-16.1 [Backend] Phát triển REST API Tổng hợp Dữ liệu Thẻ KPI nhanh (KPI Cards) | Phạm Phương Anh | 10/11/2026 | 11/11/2026 | Sprint-4, Backend |
| WMS-16.2 | Subtask | - - WMS-16.2 [Backend] Phát triển REST API Thống kê Biểu đồ Mua vs Bán & Tỷ trọng Tồn kho | Phạm Phương Anh | 11/11/2026 | 11/11/2026 | Sprint-4, Backend |
| WMS-16.3 | Subtask | - - WMS-16.3 [Frontend] Xây dựng Hàng Thẻ KPI Tương tác trên Admin / Executive Dashboard | Phạm Phương Anh | 11/11/2026 | 12/11/2026 | Sprint-4, Frontend |
| WMS-16.4 | Subtask | - - WMS-16.4 [Frontend] Xây dựng Biểu đồ Cột Nhập & Xuất, Biểu đồ Tròn/Donut (Recharts) & Giao dịch gần nhất | Phạm Phương Anh | 12/11/2026 | 13/11/2026 | Sprint-4, Frontend |
| WMS-17 | Task | - WMS-17: Báo Cáo Xuất-Nhập-Tồn, Thẻ Kho, Truy Vết Lô & Xuất File Excel (Apache POI) | Phạm Phương Anh | 13/11/2026 | 16/11/2026 | Sprint-4 |
| WMS-17.1 | Subtask | - - WMS-17.1 [Backend] Phát triển REST API Báo cáo Xuất-Nhập-Tồn & Sổ Thẻ Kho chi tiết | Phạm Phương Anh | 13/11/2026 | 14/11/2026 | Sprint-4, Backend |
| WMS-17.2 | Subtask | - - WMS-17.2 [Backend] Phát triển REST API Truy vết Nguồn gốc Hàng hóa theo Số Lô (Traceability) | Phạm Phương Anh | 14/11/2026 | 14/11/2026 | Sprint-4, Backend |
| WMS-17.3 | Subtask | - - WMS-17.3 [Backend] Tích hợp Thư viện Apache POI Xuất File Excel Báo cáo chi tiết | Trần Đăng Việt | 14/11/2026 | 15/11/2026 | Sprint-4, Backend |
| WMS-17.4 | Subtask | - - WMS-17.4 [Frontend] Xây dựng Màn hình Báo cáo Xuất-Nhập-Tồn, Thẻ Kho & Nút Xuất Excel | Phạm Phương Anh | 15/11/2026 | 15/11/2026 | Sprint-4, Frontend |
| WMS-17.5 | Subtask | - - WMS-17.5 [Frontend] Xây dựng Giao diện Truy vết Nguồn gốc Lô hàng (Traceability View) | Phạm Phương Anh | 15/11/2026 | 16/11/2026 | Sprint-4, Frontend |
| WMS-18 | Task | - WMS-18: Tích Hợp Trí Tuệ Nhân Tạo – Spring AI Summary & Chatbot Hỗ Trợ | Lê Phú Toàn | 13/11/2026 | 16/11/2026 | Sprint-4 |
| WMS-18.1 | Subtask | - - WMS-18.1 [Backend] Tích hợp Spring AI (Gemini/OpenAI) sinh Báo cáo Tóm tắt (AI Summary) | Lê Phú Toàn | 13/11/2026 | 14/11/2026 | Sprint-4, Backend |
| WMS-18.2 | Subtask | - - WMS-18.2 [Backend] Phát triển Chatbot API Hỗ trợ Tra cứu Tồn kho & Đơn hàng | Lê Phú Toàn | 14/11/2026 | 15/11/2026 | Sprint-4, Backend |
| WMS-18.3 | Subtask | - - WMS-18.3 [Frontend] Xây dựng Khối Hiển thị AI Summary trên Executive Dashboard | Lê Phú Toàn | 15/11/2026 | 15/11/2026 | Sprint-4, Frontend |
| WMS-18.4 | Subtask | - - WMS-18.4 [Frontend] Xây dựng Giao diện UI Popup Chatbot Trợ lý Ảo AI | Lê Phú Toàn | 15/11/2026 | 16/11/2026 | Sprint-4, Frontend |
| IX | Epic | EPIC 9: REDIS CACHE, KIỂM THỬ E2E/TẢI, UAT & DEPLOY PRODUCTION (GHCR) | Lê Phú Toàn | 17/11/2026 | 23/11/2026 | Sprint-4, UAT-Deploy |
| WMS-19 | Task | - WMS-19: Cấu Hình Redis Cache Tồn Kho, Tối Ưu Hiệu Năng & Kiểm Thử Tải | Lê Phương Linh | 17/11/2026 | 19/11/2026 | Sprint-4, UAT-Deploy |
| WMS-19.1 | Subtask | - - WMS-19.1 [Backend] Cấu hình Redis Cache cho Tổng Tồn Kho & Tồn Khả Dụng (ATP) | Lê Phương Linh | 17/11/2026 | 18/11/2026 | Sprint-4, UAT-Deploy, Backend |
| WMS-19.2 | Subtask | - - WMS-19.2 [QA] Kiểm thử Tải Đồng thời (30–50 req/s) & Chống Deadlock (JMeter) | Lê Phú Toàn | 18/11/2026 | 19/11/2026 | Sprint-4, UAT-Deploy, QA |
| WMS-19.3 | Subtask | - - WMS-19.3 [Backend] Tối ưu hóa MySQL Partitioning / Indexing & Phản hồi API ≤ 500ms | Lê Phương Linh | 18/11/2026 | 19/11/2026 | Sprint-4, UAT-Deploy, Backend |
| WMS-20 | Task | - WMS-20: Kiểm Thử End-to-End (E2E), Kiểm Thử Bảo Mật & Tạo Báo Cáo Test (UAT) | Lê Phú Toàn | 19/11/2026 | 21/11/2026 | UAT-Deploy |
| WMS-20.1 | Subtask | - - WMS-20.1 [QA] Kiểm thử E2E & Integration Luồng Mua-Nhập (Inbound: PO → GRN, Lô) | Trần Đăng Việt | 19/11/2026 | 20/11/2026 | UAT-Deploy, QA |
| WMS-20.2 | Subtask | - - WMS-20.2 [QA] Kiểm thử E2E & Integration Luồng Bán-Xuất, Điều chuyển & Kiểm kê | Lê Phương Linh | 19/11/2026 | 20/11/2026 | UAT-Deploy, QA |
| WMS-20.3 | Subtask | - - WMS-20.3 [QA] Kiểm thử Bảo mật (JWT, RBAC, Brute-force, CORS) & UI Responsive | Phạm Phương Anh | 20/11/2026 | 21/11/2026 | UAT-Deploy, QA |
| WMS-20.4 | Subtask | - - WMS-20.4 [Fullstack] Khắc phục lỗi (Bugfix), Đảm bảo Unit Test ≥ 80% & Tạo Báo cáo Test | Cả 4 thành viên | 20/11/2026 | 21/11/2026 | UAT-Deploy, Fullstack |
| WMS-21 | Task | - WMS-21: Deploy Production (GHCR Images) & Bàn Giao Tài Liệu Hướng Dẫn Vận Hành | Lê Phú Toàn | 22/11/2026 | 23/11/2026 | UAT-Deploy |
| WMS-21.1 | Subtask | - - WMS-21.1 [Backend] Xuất bản Tài liệu OpenAPI / Swagger Tự động cho toàn bộ Microservices | Lê Phú Toàn | 22/11/2026 | 22/11/2026 | UAT-Deploy, Backend |
| WMS-21.2 | Subtask | - - WMS-21.2 [DevOps] Bàn giao Tài liệu Hướng dẫn Triển khai, Vận hành & Backup/Restore DB | Lê Phú Toàn | 22/11/2026 | 23/11/2026 | UAT-Deploy, DevOps |
| WMS-21.3 | Subtask | - - WMS-21.3 [DevOps] Deploy Production bằng docker compose up -d (GHCR Images) | Lê Phú Toàn | 23/11/2026 | 23/11/2026 | UAT-Deploy, DevOps |
| WMS-21.4 | Subtask | - - WMS-21.4 [Scrum] Họp Tổng kết Sprint Retrospective, Nghiệm thu & Đóng Dự án | Cả 4 thành viên | 23/11/2026 | 23/11/2026 | UAT-Deploy, Scrum |
