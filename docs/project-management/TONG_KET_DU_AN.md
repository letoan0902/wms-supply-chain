# Tổng kết dự án và danh sách nghiệm thu

Tài liệu chuẩn bị cho buổi họp tổng kết ngày 13/11/2026 (WMS-21.4, TASK-130): phạm vi đã làm, đối soát tiêu chí hoàn
thành, việc còn lại, bàn giao và phần nhìn lại. Số liệu lấy ngày 12/11/2026 từ Jira dự án TASK và repository; mục 5 là
bản nháp do TechLead tổng hợp, cả nhóm bổ sung và thống nhất trong buổi họp.

## 1. Phạm vi đã làm

Jira có 109 subtask (150 ngày công kế hoạch). Ngày 12/11/2026: 99 subtask Done, 8 đã làm xong đang chờ merge Pull
Request #78 và #79, 2 làm trong ngày 13/11 (triển khai bản cuối WMS-21.3, họp tổng kết WMS-21.4).

| Nhóm chức năng | Mã | Tình trạng |
| --- | --- | --- |
| Nền tảng: monorepo, CI, Docker, config, discovery, gateway, sự kiện RabbitMQ, WebSocket | WMS-01, 02 | Xong |
| Đăng nhập, phân quyền sáu vai trò, phạm vi theo kho | WMS-03, 04 | Xong |
| Kho, vị trí Kho-Khu-Kệ-Ô, hàng hóa, đơn vị tính, mã vạch | WMS-05, 06, 07 | Xong |
| Nhà cung cấp, khách hàng, công nợ | WMS-08 | Xong |
| Đơn mua, phê duyệt, nhập kho theo lô và hạn dùng, hóa đơn mua | WMS-09, 10 | Xong |
| Đơn bán, giữ hàng, lấy hàng theo hạn dùng, xuất kho | WMS-11, 12 | Xong |
| Điều chuyển kho, kiểm kê | WMS-13, 14 | Xong |
| Cảnh báo tồn kho, dashboard, báo cáo kho, truy vết lô | WMS-15, 16, 17 | Xong; chuông cảnh báo và biểu đồ chờ merge PR #78, #79 |
| Trợ lý AI, tóm tắt dashboard | WMS-18 | Xong |
| Bộ nhớ đệm, chỉ mục, kiểm thử tải | WMS-19 | Xong; kết quả tải chờ merge PR #78 |
| Kiểm thử đầu cuối, phân quyền API, sửa lỗi, báo cáo test | WMS-20 | Chờ merge PR #78 |
| Swagger, tài liệu vận hành, triển khai, tổng kết | WMS-21 | Triển khai bản cuối và họp tổng kết làm ngày 13/11 |
| Đa doanh nghiệp: tách dữ liệu ở mọi service, kiểm thử cách ly | WMS-22 | Xong; kết quả kiểm thử chờ merge PR #78 |
| Đăng ký doanh nghiệp, gói dịch vụ, giới hạn gói, thanh toán PayOS | WMS-23 | Xong (PayOS ở chế độ giả lập) |
| ai-service, trích xuất danh mục từ Excel, PDF, ảnh | WMS-24 | Xong (đã chạy với Claude thật ngày 12/11) |
| API key cho hệ thống ngoài | WMS-25 | Xong |
| Rà giao diện | WMS-26 | Chờ merge PR #79 |

## 2. Đối soát tiêu chí hoàn thành

Tiêu chí lấy từ [CONTRIBUTING.md](../../CONTRIBUTING.md) mục 7.

| Tiêu chí | Tình trạng | Căn cứ và ngoại lệ |
| --- | --- | --- |
| Đáp ứng tiêu chí chấp nhận trên thẻ | Đạt phần lớn | 99/109 thẻ Done. Ngoại lệ đã ghi trong thẻ và tài liệu: tỷ trọng tồn theo nhóm hàng tính theo số lượng vì chưa có giá vốn; chuông cảnh báo không có nút "đã đọc" vì API chưa có; thẻ Giá trị tồn kho để trống |
| Tuân thủ quy ước code, không còn code thử nghiệm | Chưa rà toàn bộ | `npm run lint` xanh; backend chưa có công cụ kiểm tra quy ước tự động, chưa rà tay toàn bộ mã |
| Có test và test xanh | Đạt ở backend | 1.471 test backend xanh, độ phủ dòng 90,0% và nhánh 69,7% ([TEST_REPORT.md](../TEST_REPORT.md)). Frontend chỉ có lint và build, chưa có test tự động |
| CI xanh trên Pull Request | Đạt | CI là kiểm tra bắt buộc trước khi merge vào `develop` |
| Có người review và approve | Cần nhóm xác nhận | Pull Request của ba thành viên do TechLead review và sửa điểm chặn trước khi merge. Pull Request của TechLead trong tuần cuối: nhóm xác nhận trong buổi họp ai đã xem |
| API có tài liệu, thay đổi cơ sở dữ liệu có migration | Đạt | Swagger ở từng service, hợp đồng ở `docs/api/`, `docs/SAAS_SPEC.md`; mọi thay đổi bảng đi qua Flyway và đã chạy trên MySQL 8 ngày 12/11 |
| Giao diện tiếng Việt có dấu, hiển thị đúng | Đạt một phần | Đã bỏ mã yêu cầu và nhãn lẫn tiếng Anh ([UI_REVIEW.md](../UI_REVIEW.md)). Mới bấm thử trên một trình duyệt nhân Chromium ở màn hình máy tính; chưa thử Firefox, Safari và bề ngang điện thoại |
| Không có bí mật trong commit | Đạt theo kiểm tra đã làm | `.env` nằm trong `.gitignore`; tìm trên nhánh `develop` và lịch sử không thấy khóa Claude, khóa PayOS hay file `.env`. Chưa chạy công cụ quét bí mật chuyên dụng |
| Chạy được bằng `docker compose up -d` | Đạt ở máy phát triển | Ngày 12/11 dựng đủ 13 container, mọi service healthy, chạy bộ kiểm thử đầu cuối đạt. Image ở máy dựng từ file jar build sẵn; máy chủ dùng image do CI đẩy lên GHCR |
| Đã merge vào `develop`, thẻ chuyển Done | Còn 10 thẻ | 8 thẻ chờ PR #78, #79; WMS-21.3 và WMS-21.4 làm ngày 13/11 |

## 3. Việc chưa xong và chưa kiểm chứng

- Máy chủ `production` đang chạy bản cũ; bản cuối chưa được triển khai và chưa chạy bộ kiểm thử trên máy chủ.
- Thanh toán PayOS chưa thử với khóa thật và webhook thật; gửi email (kích hoạt, quên mật khẩu, cảnh báo) chưa thử với
  máy chủ SMTP thật. Cách bật ở [OPERATIONS.md](../OPERATIONS.md) mục 8.
- Độ trễ vượt 500 ms khi tải 40 thao tác mỗi giây dồn vào một mặt hàng (đạt tới 20 thao tác mỗi giây); chưa phân tích
  điểm nghẽn, chưa đo trên máy chủ ([PERFORMANCE.md](../PERFORMANCE.md) mục 5.4).
- Tồn kho chưa có giá vốn: thẻ Giá trị tồn kho và các báo cáo chưa có cột giá trị.
- Giao diện: chưa bấm thử từng bước các màn hình đăng ký doanh nghiệp, nhập dữ liệu bằng AI, đối soát và duyệt kiểm kê,
  thanh toán; chưa thử các vai trò khác Quản trị, chưa thử bề ngang điện thoại; chưa có test tự động.
- Các điểm nghiệp vụ chưa chốt ghi ở [TEST_PLAN.md](../TEST_PLAN.md) mục 2.5 (hóa đơn mua không cộng VAT, người lập tự
  duyệt đơn mua, phiếu xuất cho đơn thiếu hàng).

## 4. Bàn giao

| Nội dung | Ở đâu |
| --- | --- |
| Mã nguồn, lịch sử Pull Request | GitHub `letoan0902/wms-supply-chain`, nhánh `develop` (tích hợp) và `main` (phát hành) |
| Mục lục tài liệu | [docs/README.md](../README.md) |
| Chạy ở máy, biến môi trường | [README.md](../../README.md), `.env.example` |
| Triển khai, sao lưu, phục hồi, xử lý sự cố | [deploy/README.md](../../deploy/README.md), [OPERATIONS.md](../OPERATIONS.md) |
| Danh sách kiểm tra bàn giao máy chủ | [OPERATIONS.md](../OPERATIONS.md) mục 10 |
| Hướng dẫn sử dụng | [USER_GUIDE.md](../USER_GUIDE.md) |
| Kiểm thử | [TEST_PLAN.md](../TEST_PLAN.md), [TEST_REPORT.md](../TEST_REPORT.md), [qa/README.md](../../qa/README.md) |
| Công việc và tiến độ | Jira dự án TASK; ba báo cáo sprint (30/10, 06/11, 13/11) |

Bí mật chỉ nằm trong file `.env` trên máy chủ: mật khẩu cơ sở dữ liệu, RabbitMQ, khóa ký JWT, khóa Claude, khóa PayOS
(khi có). Khi hết môn: thu hồi khóa Claude và khóa PayOS ở trang quản trị của nhà cung cấp rồi xóa khỏi `.env`.

## 5. Nhìn lại (bản nháp để nhóm thống nhất)

Làm được:

- Đủ luồng nghiệp vụ chính từ mua hàng tới xuất kho, điều chuyển, kiểm kê, chạy trên chín service có CI và triển khai
  bằng Docker.
- Mở rộng thành mô hình nhiều doanh nghiệp giữa dự án mà không phải viết lại: cơ chế lọc theo doanh nghiệp đặt trong
  module dùng chung, mỗi service có test cách ly.
- Mỗi Pull Request đều qua CI; Pull Request của thành viên được review và sửa điểm chặn ngay trong ngày nên nhánh
  `develop` không bị treo.
- Hai ngày cuối có bộ kiểm thử đầu cuối 1.258 kiểm tra và kiểm thử tải, tìm ra và sửa được các lỗi nặng trước khi nộp.

Chưa tốt:

- Kế hoạch lập theo hạn 23/11 trong khi hạn nộp là 13/11; tới 11/11 mới phát hiện nên 26 subtask (40 ngày công) phải
  chuyển cho một người.
- Khối lượng chia không đều theo tuần (Sprint 2 một thành viên giữ 24/42 ngày công); Pull Request thường dồn vào cuối ngày.
- Hệ thống đầy đủ chỉ được dựng và chạy thử ở hai ngày cuối; trước đó chỉ dựa vào test trên cơ sở dữ liệu trong bộ nhớ
  nên lỗi tích hợp (Redis, nhóm kết nối, lệch tồn giữa hai service) lộ ra muộn.
- Mở rộng phạm vi giữa dự án (SaaS, thanh toán, AI) làm phần kiểm thử giao diện và thử với dịch vụ thật bị dồn lại.
- Nhiều màn hình làm trước khi có API nên từng dùng số liệu mẫu, phải sửa lại.

Bài học:

- Chốt hạn nộp bằng văn bản ngay khi lập kế hoạch; rà khối lượng từng người mỗi tuần.
- Dựng môi trường tích hợp và bộ kiểm thử đầu cuối từ giữa dự án.
- Luồng liên quan tới tồn kho và tiền phải đồng bộ và chống gửi trùng ngay từ thiết kế.
- Giao diện chỉ hiện số lấy từ API; chưa tính được thì nói rõ lý do.

## 6. Kịch bản demo nghiệm thu

1. Đăng ký một doanh nghiệp mới ở `/register`, đăng nhập, xem gói dùng thử.
2. Tạo kho, vị trí, nhập danh mục hàng hóa bằng AI từ một file Excel hoặc ảnh.
3. Lập đơn mua, duyệt, nhập kho có lô và hạn dùng; xem tồn và cảnh báo trên chuông.
4. Lập đơn bán, xác nhận (giữ hàng), lấy hàng theo hạn dùng, xuất kho; xem thẻ kho và truy vết lô.
5. Tạo đợt kiểm kê, đếm mù, đối soát chênh lệch, duyệt hai cấp.
6. Xem dashboard và tóm tắt bằng AI; hỏi trợ lý tồn của một mã hàng.
7. Đăng nhập doanh nghiệp thứ hai: không thấy dữ liệu của doanh nghiệp đầu.
8. Dùng công cụ demo (cần bật trong cấu hình) đặt hết hạn: hệ thống chuyển chỉ đọc; thanh toán gói để mở lại (chế độ
   giả lập).
