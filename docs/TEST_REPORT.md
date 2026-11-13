# Báo cáo kiểm thử

Kết quả kiểm thử của hệ thống tính đến ngày 12/11/2026 (WMS-20.4). Bảng test case chi tiết và kết quả từng ca ở
[TEST_PLAN.md](TEST_PLAN.md); cách dựng hệ thống thử và chạy bộ kiểm thử ở [qa/README.md](../qa/README.md).

## 1. Tóm tắt

| Loại kiểm thử | Phạm vi | Kết quả |
| --- | --- | --- |
| Test tự động backend (JUnit, chạy ở mỗi Pull Request) | 8 module, 1.471 test | Đạt toàn bộ |
| Độ phủ của test tự động backend (JaCoCo) | 14.247 dòng lệnh, 6.574 nhánh | Dòng 90,0%; nhánh 69,7% |
| Kiểm thử đầu cuối qua API (Postman CLI) | 4 bộ, 478 ca, 537 request, 1.258 kiểm tra | Đạt toàn bộ (lượt đầu 32 kiểm tra không đạt, đã sửa) |
| Tải đồng thời và bất biến tồn kho | 15 ca, ba mức tải 10, 20, 40 thao tác mỗi giây | 14 ca đạt; ca độ trễ đạt tới 20 thao tác mỗi giây, vượt 500 ms ở 40 |
| AI với Claude thật (model claude-sonnet-5-5) | Trợ lý, tóm tắt dashboard, trích xuất Excel, ảnh, PDF | Đạt khi gọi qua API; trên giao diện đã thử tóm tắt dashboard và màn hình nhập bằng AI |
| Bấm thử giao diện | Các màn hình làm trong tuần 10 đến 12/11 | Xem mục 5; tìm thấy và sửa một lỗi ở màn hình đếm kiểm kê |
| Frontend | `npm run lint`, `npm run build` | Đạt; frontend chưa có test tự động |

Trên máy chủ (13/11/2026): đã triển khai, kiểm tra trang chủ, trạng thái service, webhook PayOS thật và khóa Brevo; chưa
chạy bộ Postman và kịch bản tải trên máy chủ, chưa có giao dịch PayOS bằng tiền thật. Chưa kiểm thử: giao diện ở bề ngang điện
thoại, các vai trò khác Quản trị trên giao diện (phân quyền đã kiểm bằng test backend và bộ Postman).

## 2. Test tự động backend

Chạy `cd backend && ./gradlew test`. Test dùng H2 trong bộ nhớ, không cần Docker; báo cáo độ phủ của từng module nằm ở
`backend/<module>/build/reports/jacoco/test/html/index.html` sau khi chạy. Số liệu ngày 12/11/2026:

| Module | Số test | Độ phủ dòng | Độ phủ nhánh |
| --- | --- | --- | --- |
| common | 145 | 88,9% (639/719) | 80,7% (226/280) |
| api-gateway | 62 | 93,9% (294/313) | 86,7% (104/120) |
| auth-service | 129 | 88,4% (1.497/1.694) | 68,2% (479/702) |
| product-service | 127 | 91,6% (652/712) | 70,9% (210/296) |
| inventory-service | 374 | 90,2% (4.478/4.962) | 67,3% (1.752/2.604) |
| order-service | 286 | 87,9% (2.174/2.472) | 64,3% (660/1.026) |
| notification-service | 138 | 92,6% (981/1.059) | 71,3% (316/443) |
| ai-service | 210 | 91,1% (2.111/2.316) | 75,5% (833/1.103) |
| Tổng | 1.471 | 90,0% (12.826/14.247) | 69,7% (4.580/6.574) |

Độ phủ dòng của mọi module trên 80%. Độ phủ nhánh thấp hơn 80% ở sáu module: phần lớn nhánh chưa chạy tới là nhánh
kiểm tra dữ liệu rỗng và nhánh lỗi của các lời gọi sang service khác. config-server và discovery-server chỉ có test
khởi động, không tính vào bảng.

Giới hạn của loại test này, đã gặp thật trong tuần:

- Test chạy trên H2 và tắt Flyway nên không bắt được lỗi SQL của migration. Mọi migration đã được chạy trên MySQL 8 khi
  dựng hệ thống thử (mục 3).
- Test giả lập Redis nên không bắt được lỗi tuần tự hóa: giới hạn gói và API key qua test tự động nhưng không có tác dụng
  trên hệ thống thật cho tới khi bộ Postman phát hiện (TEST_PLAN mục 2.4).
- Khi bộ Docker đang chạy ở máy, test từng gửi sự kiện vào RabbitMQ thật và ghi vào Redis thật của hệ thống đó. Từ
  12/11 profile test dùng virtual host không tồn tại và Redis database 15.

## 3. Kiểm thử đầu cuối và tải

Chạy trên hệ thống thật dựng bằng Docker ở một máy phát triển (13 container, MySQL 8 với mọi migration, JWT bật). Kết quả
từng bộ, số đo độ trễ ở ba mức tải và bảng lỗi đã tìm thấy nằm ở [TEST_PLAN.md](TEST_PLAN.md) mục 2. Tám lỗi được tìm
thấy qua việc viết và chạy các bộ này, đều đã sửa; ba lỗi nặng nhất:

- Giới hạn gói, chế độ chỉ đọc khi hết hạn và API key không có tác dụng trên hệ thống thật (Redis).
- order-service ngừng trả lời khi nhiều yêu cầu tạo chứng từ cùng lúc (nhóm kết nối cơ sở dữ liệu).
- Tồn kho lệch giữa order-service và inventory-service khi nhập vào vị trí đang kiểm kê, nộp trùng phiếu nhập và xác
  nhận đơn bán đồng thời.

## 4. AI với Claude thật

Ngày 12/11/2026, ai-service trên hệ thống thử ở máy chạy với khóa thật, model `claude-sonnet-5-5`:

| Phép thử | Kết quả | Thời gian |
| --- | --- | --- |
| Trích xuất Excel 50 dòng, tiêu đề cột khác mẫu ("Mã SP", "ĐVT", "Đơn giá mua (VND)") | 300/300 ô đúng; cột "Ghi chú" được ghép vào mô tả với độ tin cậy 0,6 và tô vàng để người dùng xem lại | 5 giây |
| Trích xuất ảnh PNG và file PDF chứa bảng 6 dòng | 30/30 ô đúng ở mỗi file | 9 giây |
| Trợ lý: cách tạo kho; tồn của một SKU; câu hỏi nối tiếp; trạng thái một đơn mua | Trả đúng theo tài liệu hướng dẫn và đúng số liệu (tồn 120, đơn mua hoàn thành) | 2 đến 5 giây |
| Người dùng doanh nghiệp B hỏi tồn của SKU chỉ có ở doanh nghiệp A | Trả "không tìm thấy" | 5 giây |
| Yêu cầu trợ lý xóa dữ liệu | Từ chối, chỉ hướng dẫn thao tác trên màn hình | 4 giây |
| Tóm tắt dashboard | Năm ý và hai gợi ý, số liệu khớp các thẻ KPI | 6 giây |
| Đếm lượt AI | 11 lời gọi thành công, bộ đếm của doanh nghiệp tăng đúng 11 | |

Ảnh và PDF thử là bảng dựng bằng máy, chưa thử ảnh chụp tài liệu giấy. Bộ Postman chạy ở chế độ giả lập.

## 5. Bấm thử giao diện

Trên hệ thống thử ở máy, tài khoản Quản trị của một doanh nghiệp thử, ngày 12/11/2026:

| Màn hình | Đã làm | Kết quả |
| --- | --- | --- |
| Đăng nhập | Đăng nhập bằng tài khoản thử | Đạt; ô tên đăng nhập không còn gợi ý tài khoản mặc định |
| Dashboard | Xem thẻ KPI, tóm tắt AI, cảnh báo, ba biểu đồ mới | Đạt, số khớp dữ liệu thử (một đơn mua 4.840.000 đ, nhập 242, ba cảnh báo) |
| Chuông cảnh báo | Mở chuông, xem ba cảnh báo, bấm "Tạo đơn mua nháp" | Đạt: form đơn mua điền sẵn hàng và kho, số lượng để trống |
| Kiểm kê | Tạo đợt toàn kho, bắt đầu (đóng băng ba vị trí), mở màn hình đếm, quét mã vị trí, quét mã vạch và SKU | Màn hình không hiện số hệ thống. Lỗi: quét hàng không quản lý theo lô bị ghi thành hàng phát sinh thay vì cộng vào dòng cần đếm; đã sửa ở giao diện và thử lại đạt. Hàng theo lô quét thiếu số lô thì bị từ chối kèm hướng dẫn |
| Nhập dữ liệu bằng AI (Claude thật) | Tải file CSV ba dòng có tiêu đề cột khác mẫu, trích xuất, xem trước, bấm nhập | Đạt: AI ghép đúng năm cột, ba dòng đọc đúng; kết quả nhập hai dòng tạo mới, một dòng bỏ qua vì mã đã có |

Phần còn lại của giao diện (đăng ký doanh nghiệp, đối soát và duyệt kiểm kê, thanh toán, khung trợ lý AI với Claude
thật) chưa được bấm thử từng bước trong lượt này; luồng nghiệp vụ tương ứng đã qua bộ Postman.
