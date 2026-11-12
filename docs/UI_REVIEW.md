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

## Còn lại và thuộc về ai

- Dashboard: tám thẻ KPI, trung tâm cảnh báo và biểu đồ còn là dữ liệu mẫu cứng, mâu thuẫn với khối "Tóm tắt bằng AI" ngay phía trên (đang dùng số thật). Việc này nằm trong PR #75 (WMS-16.2, WMS-16.3) và WMS-16.4, thuộc người phụ trách các task đó.
- Nhãn viết tắt kỹ thuật "RBAC" ở màn hình Người dùng và trang giới thiệu gói được giữ lại, chờ thống nhất cách gọi với nhóm.

## Chưa kiểm tra được

- Thao tác trên form (tạo, sửa, quét mã) bằng tay trên giao diện: luồng nghiệp vụ đã được kiểm thử qua API bằng bộ Postman (1.258 kiểm tra đạt), còn thao tác trên form chưa bấm thử từng cái.
- Hiển thị trên điện thoại (375 px): chưa chụp được màn hình vì khung trình duyệt chạy nền.
- Các vai trò khác ADMIN: bảng quyền đã kiểm bằng script và test backend, chưa đăng nhập từng vai trò để nhìn giao diện.
