# Kết quả rà giao diện (WMS-26.1)

Ngày rà: 12/11/2026. Người thực hiện: nhóm phát triển, hỗ trợ bởi công cụ tự động.

## Phạm vi và cách rà

Rà trên stack Docker chạy ở máy, đăng nhập bằng tài khoản ADMIN của một doanh nghiệp thử. Mở từng trang bằng trình duyệt trong ứng dụng, đọc nội dung trang và console. Các trang đã rà, đều tải được dữ liệu thật và không có lỗi console:

`/`, `/products`, `/warehouses`, `/suppliers`, `/customers`, `/inventory`, `/transfers`, `/purchases`, `/sales`, `/stocktakes`, `/reports`, `/subscription`, `/settings/api-keys`, `/ai-import`, `/users`, `/landing`, `/login`.

Sau khi rà, toàn bộ `frontend/src` được quét lại bằng tìm kiếm văn bản để tìm các mã yêu cầu, mã trạng thái tiếng Anh và nhãn trộn ngôn ngữ ở những màn hình chưa mở tới.

## Đã sửa trong commit này

Chỉ sửa chữ hiển thị, không đổi hành vi, bố cục, lời gọi API hay phân quyền.

1. Trang đăng nhập: ô tên đăng nhập không còn gợi ý tên tài khoản mặc định, thay bằng "Nhập tên đăng nhập" (WMS-23.0 đã bỏ gợi ý tài khoản mặc định).
2. Bỏ mã yêu cầu khỏi chữ hiển thị (quy ước: không đưa mã FRxx, WMS-xx, SRS vào giao diện): tiêu đề, nhãn cột, thẻ, thông báo và mô tả ở các màn hình Khách hàng, Sản phẩm (nhập Excel, quy đổi đơn vị, tem nhãn), Mua hàng (đơn mua, nhập kho, lô và hạn dùng), Bán hàng (đơn bán, hóa đơn, công nợ phải thu).
3. Bỏ mã trạng thái tiếng Anh trong ngoặc cạnh nhãn tiếng Việt (DRAFT, PENDING, APPROVED, COMPLETED, CONFIRMED, PAID, PREPAID, ACTIVE, LOCKED, ALLOWED, WARNING, BLOCKED).
4. Việt hóa nhãn tiếng Anh có từ tương đương đã dùng trong ứng dụng: Backorder thành "Thiếu hàng", Credit Limit thành "hạn mức nợ", Soft Reservation thành "giữ hàng", Smart Picking Route thành "lộ trình lấy hàng", Auto-Focus thành "Tự động chọn ô quét", Sound Beep thành "Tiếng bíp", Simulator, Recent Invoices, Receivables, Balance.
5. Không tìm thấy biểu tượng cảm xúc trong chuỗi chữ, không tìm thấy chữ tiếng Việt thiếu dấu.

Giá trị gửi lên hoặc so sánh với backend (enum trạng thái, tên trường API, tên biến, mã trong chú thích) được giữ nguyên.

## Lượt bấm thử sau đó trong ngày 12/11/2026

Sau khi các màn hình mới được gộp, bấm thử trên cùng stack bằng tài khoản ADMIN của một doanh nghiệp thử có dữ liệu
(ba mặt hàng, một phiếu nhập, hai cảnh báo tồn thấp, một lô cận hạn):

| Màn hình | Thao tác | Kết quả |
| --- | --- | --- |
| Đăng nhập | Đăng nhập bằng tài khoản thử | Đạt |
| Dashboard | Tám thẻ KPI, tóm tắt bằng AI (Claude thật), danh sách cảnh báo, ba khối mới của WMS-16.4 | Đạt, số khớp dữ liệu thử |
| Chuông cảnh báo (WMS-15.4) | Mở chuông, lọc, bấm "Tạo đơn mua nháp" | Đạt: form đơn mua điền sẵn hàng và kho, số lượng để trống. Chưa bấm lưu đơn |
| Kiểm kê | Tạo đợt, bắt đầu, màn hình đếm: quét mã vị trí, mã vạch, SKU | Tìm thấy lỗi: hàng không quản lý theo lô bị ghi thành hàng phát sinh. Đã sửa, thử lại đạt |
| Nhập dữ liệu bằng AI | Tải file CSV, trích xuất bằng Claude thật, xem trước, nhập | Đạt: hai dòng tạo mới, một dòng bỏ qua vì mã đã có |

Lượt này cũng bỏ nốt các nhãn lẫn tiếng Anh nhìn thấy khi bấm: chữ trong ngoặc ở tiêu đề đơn mua, đơn bán và dòng tổng
tiền, "CẬN DATE" thành "CẬN HẠN".

## Còn lại

- Nhãn viết tắt kỹ thuật "RBAC" ở màn hình Người dùng và trang giới thiệu gói được giữ lại, chờ thống nhất cách gọi với nhóm.
- Dashboard đã dùng số liệu thật ở mọi khối (PR #75, #78, #79). Thẻ Giá trị tồn kho và tỷ trọng theo giá trị để trống hoặc
  tính theo số lượng vì tồn kho chưa có giá vốn.

## Chưa kiểm tra được

- Các form chưa bấm thử từng bước: đăng ký doanh nghiệp, đối soát và duyệt kiểm kê, thanh toán gói, lưu đơn mua tạo từ
  cảnh báo, khung trợ lý AI với Claude thật (đã thử ở chế độ giả lập). Luồng nghiệp vụ tương ứng đã qua bộ Postman
  (1.258 kiểm tra đạt).
- Hiển thị trên điện thoại (375 px): chưa xem được vì khung trình duyệt dùng để rà chạy nền, không dựng hình đúng kích thước.
- Các vai trò khác ADMIN: bảng quyền đã kiểm bằng test backend và bộ Postman, chưa đăng nhập từng vai trò để nhìn giao diện.
- Trình duyệt khác nhân Chromium (Firefox, Safari).
