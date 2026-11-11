# Hướng dẫn sử dụng WMS Supply Chain

WMS Supply Chain là phần mềm quản lý kho trên web: kho và vị trí ô/kệ, hàng hóa (SKU), nhà cung cấp, khách hàng, mua và nhập kho, bán và xuất kho, điều chuyển, tồn kho và cảnh báo, công nợ, báo cáo, truy vết theo lô, gói dịch vụ, trợ lý AI. Mọi mục menu hiện với mọi người; vai trò không có quyền sẽ thấy thông báo không có quyền. Các vai trò:
- Quản trị hệ thống: toàn quyền, quản lý người dùng và API key.
- Quản lý kho: quản lý kho, vị trí kệ, duyệt đơn mua và điều chuyển.
- Nhân viên kho: nhập, xuất kho, quét mã vạch, kiểm đếm.
- Nhân viên mua hàng: lập đơn mua, theo dõi nhà cung cấp.
- Nhân viên bán hàng: lập đơn bán, theo dõi khách hàng.
- Kế toán: công nợ, hóa đơn, thanh toán.

Hướng dẫn phản ánh giao diện tại ngày 11/11/2026.

## Đăng nhập, quên mật khẩu, đăng ký và kích hoạt doanh nghiệp

Đăng ký doanh nghiệp (dùng thử 30 ngày):
1. Mở trang giới thiệu `/landing` hoặc bấm "Dùng thử 30 ngày" ở trang đăng nhập.
2. Ở khung "Đăng ký Dùng thử cho Doanh nghiệp", nhập tên công ty, người đại diện, Tên đăng nhập quản trị (3 đến 50 ký tự: chữ, số, dấu chấm, gạch dưới, gạch ngang), Mật khẩu (6 đến 72 ký tự), Email công việc, số điện thoại; tích đồng ý Điều khoản dịch vụ.
3. Bấm "Kích hoạt Dùng thử Miễn phí 30 ngày". Tài khoản này là quản trị của doanh nghiệp.
4. Mở email, bấm liên kết kích hoạt (hiệu lực 24 giờ). Trang `/activate` báo "Kích hoạt thành công", bấm "Đăng nhập". Liên kết hỏng hoặc hết hạn thì trang báo "Liên kết kích hoạt không dùng được": nhập email đã dùng để đăng ký và bấm "Gửi lại email kích hoạt".

Đăng nhập: vào `/login`, nhập Tên đăng nhập, Mật khẩu, bấm "Đăng nhập". Doanh nghiệp chưa kích hoạt sẽ thấy cảnh báo "Doanh nghiệp chưa được kích hoạt" kèm ô gửi lại email kích hoạt.

Quên mật khẩu:
1. Ở trang đăng nhập bấm "Quên mật khẩu?" (trang `/forgot-password`), nhập email, bấm "Gửi liên kết đặt lại mật khẩu".
2. Mở liên kết trong email (dùng được một lần, hiệu lực 30 phút). Trang `/reset-password` mở ra.
3. Nhập "Mật khẩu mới" (6 đến 72 ký tự) và "Nhập lại mật khẩu mới", bấm "Đặt mật khẩu mới". Mọi phiên đăng nhập cũ phải đăng nhập lại. Liên kết hết hạn thì bấm "Yêu cầu liên kết mới".

Đăng xuất: bấm tên tài khoản ở góc phải trên, chọn "Đăng xuất".

## Hồ sơ cá nhân và đổi mật khẩu

Bấm tên tài khoản ở góc phải trên, chọn "Thông tin tài khoản", "Cập nhật thông tin" hoặc "Đổi mật khẩu" để mở trang "Hồ sơ & Tài khoản cá nhân" ở tab tương ứng.

- Tab "Thông tin tài khoản": xem tên đăng nhập, họ tên, email, trạng thái, vai trò, kho được phân công.
- Tab "Cập nhật thông tin": sửa Họ và tên, Địa chỉ Email (bắt buộc), bấm "Lưu thay đổi". Tên đăng nhập không đổi được.
- Tab "Đổi mật khẩu": nhập Mật khẩu hiện tại, Mật khẩu mới (6 đến 72 ký tự), Xác nhận mật khẩu mới, bấm "Cập nhật mật khẩu mới".

Vai trò và kho phụ trách của mình chỉ Quản trị hệ thống thay đổi được, trong menu "Người dùng".

## Người dùng và phân quyền

Chỉ Quản trị hệ thống dùng được; vai trò khác thấy thông báo 403.

Thêm người dùng:
1. Vào menu "Người dùng", bấm "Thêm người dùng".
2. Nhập Tên đăng nhập, Họ và tên, Địa chỉ Email, Mật khẩu khởi tạo (tối thiểu 6 ký tự).
3. Chọn một hoặc nhiều Vai trò, chọn Phạm vi kho phụ trách.
4. Bấm "Tạo tài khoản". Tên đăng nhập không đổi được sau khi tạo.

Trên mỗi dòng:
- Xem chi tiết, chỉnh sửa (email, họ tên, vai trò, kho).
- Phân quyền kho làm việc: chọn kho, bấm "Lưu phân quyền"; dòng "Chưa gán kho" có nút "Gán ngay".
- Khóa hoặc mở khóa bằng công tắc cột Trạng thái, bắt buộc nhập lý do (ít nhất 5 ký tự). Không tự khóa được tài khoản của mình.

Quản trị hệ thống và Kế toán chưa gán kho được xem toàn bộ kho; vai trò khác chỉ làm việc với kho được gán.

## Tạo kho và vị trí lưu trữ

Vào menu "Kho và vị trí". Trang có tab danh mục kho và tab "Sơ đồ Vị trí Ô/Kệ & In Tem Mã Vạch".

Tạo kho:
1. Bấm "Thêm kho mới".
2. Nhập Mã kho (ví dụ WH-HCM-01), Tên kho, Sức chứa thiết kế (bắt buộc); địa chỉ, trưởng kho, điện thoại là tùy chọn.
3. Bấm "Tạo kho". Mã kho không sửa được về sau.

Mỗi dòng kho có nút xem sơ đồ vị trí, chỉnh sửa, khóa hoặc mở khóa, xóa.

Tạo vị trí ô/kệ (Khu - Kệ - Ô):
1. Mở tab sơ đồ vị trí, chọn kho.
2. Thêm một ô: bấm "Thêm 1 ô", nhập Khu (Zone), Kệ (Rack), Ô (Bin), bấm "Thêm vị trí".
3. Tạo hàng loạt: bấm "Sinh hàng loạt (Bulk)", nhập Khu vực, tiền tố và dải số kệ, tiền tố và dải số ô, tải trọng mỗi ô, bấm "Tạo ... vị trí".

Sơ đồ lọc theo khu và trạng thái (Trống, Đang có hàng, Bảo trì); mỗi ô có nút in tem mã vạch và nút xóa.

## Thêm hàng hóa (SKU)

Vào menu "Danh mục hàng hóa".

Thêm một mặt hàng:
1. Bấm "Thêm sản phẩm mới".
2. Nhập bắt buộc Mã SKU (duy nhất), Tên sản phẩm, Đơn vị tính cơ sở; các trường khác (mã vạch, nhóm hàng, giá, VAT, định mức tồn) là tùy chọn.
3. Bật "Quản lý theo Lô hàng" hoặc "Quản lý Hạn sử dụng" nếu cần; khi nhập kho sẽ bắt buộc nhập lô và hạn dùng.
4. Bấm "Tạo mới".

Nhập nhiều mặt hàng từ Excel:
1. Bấm "Nhập Excel", bấm "Tải file mẫu (.xlsx)".
2. Điền file; cột bắt buộc là Mã SKU, Tên sản phẩm, Đơn vị tính. Đơn vị tính và nhóm hàng ghi bằng tên (ghi mã có sẵn cũng được); tên chưa có trong doanh nghiệp được tạo mới. Quản lý theo lô và hạn dùng bật sau trong màn hình sửa hàng hóa.
3. Kéo thả file (.xlsx, .xls, .csv), xem trước, bấm "Tiến hành Import". Bảng kết quả ghi từng dòng: Đã tạo, Bỏ qua (SKU đã có, không ghi đè) hoặc Lỗi kèm lý do. File trên 500 dòng được gửi thành nhiều lần.

Nút ngưng hoạt động trên dòng là xóa mềm, dữ liệu vẫn giữ. Nhóm hàng và đơn vị tính chỉ chọn từ danh sách có sẵn; chưa có màn hình thêm mới hai danh mục này.

## Quy đổi đơn vị tính và in tem mã vạch hàng hóa

Quy đổi đơn vị (ví dụ 1 Thùng = 4 Lốc = 24 Chai):
1. Vào menu "Danh mục hàng hóa", trên dòng sản phẩm bấm nút cấu hình quy đổi đơn vị.
2. Ở khung "Thêm quy tắc quy đổi mới", chọn đơn vị nguồn (đóng gói lớn), nhập Hệ số quy đổi, chọn đơn vị đích; tùy chọn barcode quy cách và ghi chú.
3. Bấm "Thêm quy đổi". Bảng tự tính hệ số về đơn vị cơ sở. Tab "Công cụ tính thử tồn kho" giúp tính thử quy đổi.

In tem mã vạch hàng hóa:
1. Trên dòng sản phẩm bấm nút xem và in tem mã vạch.
2. Tab "Mã vạch SKU chuẩn" in mã vạch của SKU.
3. Tab "Tem nhãn Lô & Hạn dùng": nhập Số lô, Hạn sử dụng, chọn QR Code hoặc Code 128, tạo rồi in.

Tem vị trí ô/kệ in ở menu "Kho và vị trí".

## Nhà cung cấp

Vào menu "Nhà cung cấp".

Thêm nhà cung cấp:
1. Bấm "Thêm nhà cung cấp".
2. Nhập bắt buộc Mã nhà cung cấp, Tên nhà cung cấp, Hạn mức nợ tối đa; mã số thuế, liên hệ, ngân hàng, điều khoản thanh toán, công nợ ban đầu là tùy chọn.
3. Bấm "Thêm mới".

Trên mỗi dòng:
- Xem sổ chi tiết công nợ và giao dịch.
- Ghi nhận giao dịch công nợ: chọn loại (hóa đơn nhập, thanh toán, trả hàng, điều chỉnh), nhập số tiền, bấm "Ghi nhận giao dịch".
- Chỉnh sửa, ngưng hoạt động.

Nút "Xuất Excel" tải danh sách. Công nợ từ phiếu nhập kho xem ở tab "Hóa đơn mua & Công nợ" của menu "Mua hàng và nhập kho".

## Khách hàng

Vào menu "Khách hàng".

Thêm khách hàng (Quản trị hệ thống, Nhân viên bán hàng, Quản lý kho):
1. Bấm "Thêm khách hàng".
2. Chọn phân loại: Khách sỉ / Đại lý (có hạn mức công nợ) hoặc Khách lẻ (hạn mức 0, thanh toán ngay).
3. Nhập bắt buộc Mã khách hàng, Tên khách hàng, Hạn mức công nợ tối đa; tùy chọn mã số thuế, liên hệ, địa chỉ giao hàng, điều khoản thanh toán, công nợ ban đầu, ghi chú.
4. Bấm "Tạo khách hàng".

Nợ từ 90% hạn mức hiện "Cận hạn mức"; vượt hạn mức hoặc "Đã khóa nợ" thì bị chặn tạo đơn bán.

Trên mỗi dòng:
- Xem chi tiết hồ sơ, hạn mức và lịch sử công nợ.
- Chỉnh sửa (Quản trị hệ thống, Nhân viên bán hàng, Quản lý kho).
- Khóa hoặc mở khóa nợ, ghi nhận phát sinh nợ / thu tiền (Quản trị hệ thống, Kế toán, Nhân viên bán hàng).
- Ngưng hợp tác (chỉ Quản trị hệ thống).

Nút "Xuất Excel" tải danh sách.

## Đơn mua hàng (PO) và phê duyệt

Vào menu "Mua hàng và nhập kho".

Lập đơn mua:
1. Bấm "Lập đơn mua hàng mới".
2. Chọn Nhà cung cấp và Ngày lập đơn (bắt buộc), Kho tiếp nhận hàng, Ngày nhận dự kiến, ghi chú. Mã PO để trống sẽ tự sinh.
3. Bấm "Thêm dòng sản phẩm", chọn SKU, nhập số lượng, đơn giá, chiết khấu, VAT; tổng tiền tự tính.
4. Bấm "Gửi phê duyệt (PENDING)" hoặc "Lưu nháp (DRAFT)". Màn hình chưa có nút sửa hay gửi duyệt cho đơn đã lưu, nên đơn cần duyệt hãy gửi phê duyệt ngay.

Phê duyệt (Quản trị hệ thống, Quản lý kho, Nhân viên mua hàng): mở đơn "Chờ duyệt", bấm "Phê duyệt đơn hàng", hoặc bấm "Từ chối" và nhập lý do (bắt buộc).

Hủy (Quản trị hệ thống, Nhân viên mua hàng): đơn Bản nháp hoặc Chờ duyệt, bấm "Hủy đơn".

Trạng thái: Bản nháp, Chờ duyệt, Đã phê duyệt, Nhập một phần, Hoàn thành, Đã từ chối, Đã hủy. Danh sách lọc theo trạng thái, nhà cung cấp, kho, ngày và xuất Excel được.

## Nhập kho theo đơn mua (phiếu nhập GRN)

Chỉ nhập được cho đơn mua Đã phê duyệt hoặc Nhập một phần.

1. Vào menu "Mua hàng và nhập kho", bấm "Nhập kho thông minh" (hoặc "Lập phiếu nhập kho" trong chi tiết đơn mua).
2. Chọn đơn mua đã duyệt (bắt buộc), ngày nhập thực tế, ghi chú. Màn hình hiện số đã đặt, đã nhận, còn lại.
3. Ghi số lượng: quét mã vạch SKU (hoặc "Camera quét") để cộng dồn, hoặc nhập cột SL Đạt; "Điền đủ số còn lại" điền nhanh toàn bộ.
4. Hàng lỗi: nhập SL Hỏng kèm lý do (bắt buộc).
5. Hàng quản lý lô hoặc hạn dùng: bấm "Nhập Lô & HSD", nhập số lô, ngày sản xuất, hạn sử dụng. Lô đã hết hạn không được nhập.
6. Chọn vị trí cất hàng (ô/kệ) cho từng dòng.
7. Bấm "Xác nhận Nhập kho (GRN)".

Tab "Phiếu nhập kho" liệt kê các phiếu; bấm "Xem" rồi "In Phiếu Nhập Kho" để in.

## Hóa đơn mua và thanh toán nhà cung cấp

Tab "Hóa đơn mua & Công nợ" trong menu "Mua hàng và nhập kho" chỉ hiện với Quản trị hệ thống, Nhân viên mua hàng, Kế toán, Quản lý kho. Hóa đơn mua là công nợ phát sinh từ các phiếu nhập kho.

Danh sách hiện phiếu nhập, đơn mua, nhà cung cấp, hạn thanh toán, tổng tiền, đã trả, còn nợ, trạng thái.

Ghi nhận thanh toán (chỉ Quản trị hệ thống và Kế toán):
1. Trên hóa đơn còn nợ, bấm "Trả tiền".
2. Nhập số tiền, chọn phương thức (Chuyển khoản, Tiền mặt, Séc, Hình thức khác), ngày thanh toán; tùy chọn mã chứng từ và ghi chú.
3. Bấm "Xác nhận thanh toán".

Trạng thái: Chưa thanh toán, Thanh toán 1 phần, Đã thanh toán, Đã hủy.

## Đơn bán hàng (SO)

Lập đơn bán:
1. Vào menu "Bán hàng và xuất kho", bấm "Lập Đơn Bán Mới".
2. Chọn Khách hàng và Ngày lập đơn (bắt buộc), Kho xuất hàng (để trống thì tính tồn toàn hệ thống), thông tin người nhận, địa chỉ giao, ghi chú.
3. Khách bị khóa nợ hoặc vượt hạn mức không lập được đơn.
4. Bấm "Thêm dòng mặt hàng", chọn SKU, nhập số lượng; mỗi dòng hiện tồn khả dụng.
5. Công tắc "Cho phép chuyển Backorder": bật thì thiếu hàng đơn chuyển Thiếu hàng (Backorder), tắt thì chặn.
6. Bấm "Tạo & Xác nhận đơn (CONFIRMED)" hoặc "Lưu bản nháp (DRAFT)".

Xác nhận đơn Bản nháp hoặc Backorder (Quản trị hệ thống, Nhân viên bán hàng, Quản lý kho): mở đơn, bấm "Xác nhận đơn hàng"; hàng được giữ chỗ.

Hủy (Quản trị hệ thống, Nhân viên bán hàng): đơn Bản nháp, Đã xác nhận, Backorder; hàng giữ chỗ được giải phóng. Đơn Bản nháp hoặc Đã hủy có thể xóa hẳn.

Trạng thái: Bản nháp, Đã xác nhận, Thiếu hàng (Backorder), Đã hoàn thành, Đã hủy. Chưa có nút sửa đơn đã lưu.

## Lấy hàng và phiếu xuất kho (GDN)

Chỉ đơn bán Đã xác nhận mới lấy hàng và xuất kho được.

Lấy hàng:
1. Vào menu "Bán hàng và xuất kho", tab "Xuất kho & Lộ trình lấy hàng", bấm "Lấy hàng" trên đơn (hoặc trong chi tiết đơn).
2. Danh sách lấy hàng được sắp theo Khu, Kệ, Ô. Quét mã vạch hoặc tích chọn để đưa hàng vào xe đẩy; "Lấy nhanh toàn bộ" đánh dấu tất cả.
3. Bấm "In Phiếu Lấy Hàng" để in.

Lập phiếu xuất kho:
1. Bấm "Xuất kho" trên đơn, "Xuất kho (GDN)" trong chi tiết đơn, hoặc "Lập Phiếu Xuất GDN" ở tab "Phiếu xuất kho (GDN)".
2. Quét mã vạch (hoặc "Quét bằng Camera") để cộng dồn, hoặc nhập số lượng; xuất từng phần được.
3. Nhập ghi chú (biển số xe, tài xế), bấm "Xác Nhận Xuất Xưởng & In Phiếu Giao Hàng". Tồn kho bị trừ và phiếu giao hàng mở ra để in.

Ở tab "Xuất kho & Lộ trình lấy hàng", nút lấy hàng và xuất kho chỉ bật cho Quản trị hệ thống, Quản lý kho, Nhân viên kho. Tab "Phiếu xuất kho (GDN)" cho xem và in lại các phiếu.

## Hóa đơn bán và thu công nợ khách hàng

Chỉ Quản trị hệ thống, Nhân viên bán hàng, Kế toán lập được hóa đơn bán.

Lập hóa đơn:
1. Vào menu "Bán hàng và xuất kho", trên đơn Đã xác nhận hoặc Đã hoàn thành bấm nút lập hóa đơn (hoặc "Tạo Hóa đơn bán" trong chi tiết đơn).
2. Chọn khách hàng, nhập số hóa đơn, chọn "Bán nợ" (cộng vào công nợ phải thu) hoặc "Thu tiền ngay", nhập số tiền, ghi chú.
3. Bấm "Phát hành Hóa đơn".

Tab "Hóa đơn bán & Công nợ phải thu" hiện tổng công nợ phải thu và danh sách khách; chọn một khách để xem lịch sử hóa đơn.

Thu tiền nợ:
1. Trên dòng khách bấm nút thu tiền.
2. Nhập số tiền thu, số phiếu thu (bắt buộc), ghi chú.
3. Bấm "Xác nhận Thu tiền".

Công nợ khách hàng cũng ghi nhận được ở menu "Khách hàng".

## Điều chuyển kho

Vào menu "Điều chuyển".

Lập lệnh:
1. Bấm "Lập lệnh điều chuyển".
2. Chọn Kho xuất nguồn và Kho nhập đích (bắt buộc, khác nhau), ngày giao dự kiến, ghi chú.
3. Bấm "Thêm sản phẩm", chọn SKU, nhập số lượng chuyển (không vượt tồn khả dụng ở kho nguồn).
4. Bấm "Tạo lệnh điều chuyển". Lệnh ở trạng thái Bản nháp.

Duyệt xuất: với lệnh Bản nháp, bấm nút duyệt xuất kho rồi "Duyệt xuất"; tồn kho nguồn bị trừ, lệnh chuyển Đang vận chuyển. Lệnh Bản nháp còn sửa hoặc hủy được.

Nhận hàng tại kho đích:
1. Với lệnh Đang vận chuyển, bấm "Nhận hàng".
2. Quét mã vạch hoặc bấm "Nhận đủ 100%", hoặc nhập SL thực nhận, SL hư hỏng.
3. Chọn vị trí cất hàng ở kho đích (bắt buộc); nếu thiếu hoặc hỏng phải ghi biên bản sự cố.
4. Bấm "Xác nhận nhận hàng điều chuyển".

Trạng thái: Bản nháp, Đang vận chuyển, Đã nhận đủ, Nhận một phần, Đã hủy.

## Xem tồn kho và cảnh báo tồn

Vào menu "Tồn kho". Quản trị hệ thống và Kế toán xem mọi kho (để trống ô kho là "Tất cả kho"); vai trò khác chỉ chọn được kho được phân công, tài khoản chưa được gán kho thấy thông báo "Không có quyền xem tồn kho".

Cách xem:
1. Chọn kho, chọn cách xem "Theo hàng hóa" hoặc "Theo lô và vị trí".
2. "Theo hàng hóa": tìm theo SKU hoặc tên, lọc "Danh mục hàng hóa". Cột "Tồn thực tế", "Tồn dùng được" (lô không khóa, chưa hết hạn), "Đã giữ chỗ", "Khả dụng" (tồn dùng được trừ đã giữ chỗ), "Mức an toàn", "Điểm đặt hàng lại", "Cảnh báo". Cách xem này chỉ dành cho Quản trị hệ thống, Kế toán, Quản lý kho, Nhân viên kho; vai trò khác xem theo lô và vị trí.
3. "Theo lô và vị trí": lọc theo hàng hóa, "Số lô", còn hàng hay đã hết, đang khóa hay không, "Tình trạng hạn dùng". Mỗi dòng có số lô, ngày sản xuất, hạn dùng, số ngày còn lại, vị trí (khu - kệ - ô), số lượng, trạng thái "Đã khóa" hoặc "Đang dùng".
4. Bật "Chỉ hiện dòng có cảnh báo" để xem danh sách cảnh báo; "Xóa bộ lọc" đưa bộ lọc về mặc định.

Thẻ cảnh báo (máy chủ tính, thanh "Cảnh báo ngày ..." đếm theo loại và mức; bấm một ô để lọc):
- "HẾT HẠN": lô còn hàng đã tới hoặc quá hạn dùng.
- "CẬN DATE": lô còn hàng sắp hết hạn (mặc định trong 30 ngày tới).
- "CHÁY HÀNG": tồn dùng được nhỏ hơn hoặc bằng mức an toàn.
- "SẮP HẾT": tồn dùng được nhỏ hơn hoặc bằng điểm đặt hàng lại.
- "TỒN LÂU": còn tồn nhưng không nhập xuất từ 90 ngày trở lên (mặc định).
Màu thẻ: đỏ là khẩn cấp, vàng là cảnh báo, xanh là thông tin.

Bảng tự cập nhật khi có nhập, xuất ở kho đang xem; nút "Làm mới" tải lại ngay. Quản trị hệ thống có thêm nút "Quét cảnh báo ngay" để quét lại cảnh báo trong ngày.

Số tồn còn xem được ở màn hình lập đơn bán (tồn khả dụng theo kho xuất), lập lệnh điều chuyển (tồn khả dụng ở kho nguồn), hoặc hỏi trợ lý AI.

## Kiểm kê

Mục menu "Kiểm kê" chưa có màn hình trên hệ thống (đang là trang giữ chỗ).

## Dashboard

Dashboard là trang đầu tiên sau khi đăng nhập.

- Bộ lọc kho: Quản trị hệ thống và Kế toán có thêm "Tất cả các kho (Toàn cục)"; vai trò khác chỉ thấy kho được phân công. Bộ lọc thời gian: Hôm nay, 7 ngày qua, Tháng này, Quý này.
- Khối "Tóm tắt bằng AI" ngay dưới thanh bộ lọc: 3 đến 5 ý về số liệu điều hành của kho đang chọn và phần "Gợi ý" 1 đến 2 việc nên làm, kèm thời điểm lập. Bản tóm tắt được lưu 30 phút ("từ bộ nhớ đệm", không tốn lượt AI); nút "Làm mới" tạo bản mới và dùng một lượt AI (có hỏi xác nhận). Chỉ Quản trị hệ thống và Kế toán có chỉ số tài chính trong bản tóm tắt. Nhãn "Chế độ giả lập" nghĩa là hệ thống chưa bật AI thật, các ý được soạn sẵn từ cùng số liệu. Tài khoản không được xem số liệu điều hành (ví dụ chưa được gán kho) không thấy khối này.
- Thẻ KPI: Quản trị hệ thống và Kế toán thấy chỉ số tài chính; vai trò khác thấy chỉ số vận hành.
- Biểu đồ mua vào và bán ra, mặt hàng bán chạy, bảng cảnh báo tồn kho.
- Nút "Xuất Excel KPI" chỉ dùng được với Quản trị hệ thống và Kế toán.

Lưu ý: thẻ KPI, biểu đồ và bảng cảnh báo trên Dashboard hiện là dữ liệu mẫu minh họa, chưa lấy từ giao dịch thật; khối "Tóm tắt bằng AI" lấy số liệu thật. Cảnh báo tồn kho thật xem ở menu "Tồn kho".

Biểu tượng chuông ở góc phải trên hiện thông báo thời gian thực, có nút "Xóa tất cả" và "Kết nối lại".

## Báo cáo

Vào menu "Báo cáo" để mở trang "Báo cáo kho". Số liệu lấy từ sổ kho thật.

- Quản trị hệ thống và Kế toán xem mọi kho; Quản lý kho và Nhân viên kho xem kho được phân công.
- Nhân viên mua hàng, Nhân viên bán hàng (không kèm vai trò trên) thấy trang "403 - Quyền truy cập bị từ chối".

Tab "Xuất - Nhập - Tồn":
1. Chọn "Kỳ báo cáo" (mặc định từ đầu tháng tới hôm nay, tối đa 366 ngày), "Kho", "Danh mục", "Sản phẩm", "Từ khóa" (mã SKU hoặc tên), "Gom dòng" ("Theo sản phẩm và kho" hoặc "Theo sản phẩm (gộp các kho)").
2. Bảng hiện Tồn đầu kỳ, Nhập trong kỳ, Xuất trong kỳ, Tồn cuối kỳ và dòng "Tổng cộng".
3. Bấm "Tải Excel" để tải báo cáo.
Hệ thống chưa có giá vốn nên báo cáo chưa có cột giá trị tiền.

Tab "Thẻ kho" (lịch sử nhập xuất của một mặt hàng tại một kho):
1. Chọn "Sản phẩm" và "Kho" (bắt buộc), chọn kỳ.
2. Màn hình hiện Tồn đầu kỳ, Tổng nhập, Tổng xuất, Tồn cuối kỳ và từng dòng biến động: thời điểm, loại biến động, chứng từ, số lô, vị trí, nhập, xuất, tồn sau, người thực hiện.
3. Bấm số lô để mở truy vết lô; bấm "Tải Excel" để tải thẻ kho.

Các danh sách đơn mua, phiếu nhập, đơn bán, phiếu xuất, nhà cung cấp, khách hàng cũng có nút "Xuất Excel".

## Truy vết theo lô

Vào menu "Truy vết theo lô" (hoặc nút "Truy vết theo số lô" ở trang "Báo cáo kho"). Cùng quyền xem như trang báo cáo kho.

1. Nhập số lô (số in trên bao bì), bấm "Truy vết".
2. Số lô dùng cho nhiều sản phẩm thì chọn sản phẩm cần xem.
3. Màn hình hiện "Tóm tắt lô" (nhập theo phiếu nhập, đã xuất bán, đã điều chuyển đi, tồn theo sổ kho, tồn thực tế, đang giữ chỗ), "Cây truy vết", nguồn gốc (nhập từ đâu), đang ở đâu (kho, vị trí), đã đi đâu (đơn bán, điều chuyển) và diễn biến theo thời gian.

## API key

Chỉ Quản trị hệ thống dùng được. API key cho phần mềm khác (website bán hàng, kế toán, sàn thương mại điện tử) đọc dữ liệu doanh nghiệp.

Tạo key:
1. Vào menu "API key", bấm "Tạo API key".
2. Nhập Tên gợi nhớ (2 đến 100 ký tự), bấm "Tạo key".
3. Key chỉ hiện một lần: bấm "Sao chép key", lưu lại, rồi bấm "Tôi đã lưu key".

Cách dùng: gửi key trong header `X-Api-Key`. Key chỉ đọc (GET) hàng hóa, tồn kho, đơn hàng, tối đa 20 yêu cầu mỗi giây. Mỗi doanh nghiệp có tối đa 5 key đang hoạt động.

Thu hồi: bấm "Thu hồi" trên dòng key và xác nhận; hệ thống dùng key bị từ chối ngay, không hoàn tác được.

API key chỉ có ở gói Dùng thử và Chuyên Nghiệp; gói Khởi Đầu thấy thông báo "Gói dịch vụ hiện tại không hỗ trợ API key". Khi gói hết hạn không tạo được key mới và các key đang có tạm ngưng hoạt động cho tới khi gia hạn (thu hồi key vẫn làm được).

## Gói dịch vụ và thanh toán

Vào menu "Gói dịch vụ" (trang `/subscription`). Mọi tài khoản xem được; chỉ Quản trị hệ thống đổi gói và thanh toán.

Các gói: Khởi Đầu (miễn phí: 3 người dùng, 1 kho, 200 SKU, 20 lượt AI mỗi tháng, không có API key), Dùng thử (30 ngày kể từ khi đăng ký, giới hạn như Chuyên Nghiệp), Chuyên Nghiệp (499.000 đ mỗi tháng hoặc 4.790.000 đ mỗi năm: 50 người dùng, 20 kho, không giới hạn SKU, 500 lượt AI mỗi tháng, có API key).

Trang gồm:
- "Gói hiện tại": gói, trạng thái, ngày hết hạn hoặc hết dùng thử, số ngày còn lại.
- "Mức sử dụng": người dùng, kho, SKU đang dùng và lượt AI trong tháng so với giới hạn của gói.
- "So sánh các gói": giá và giới hạn của từng gói.
- "Lịch sử giao dịch" (chỉ Quản trị hệ thống): mã đơn, gói, chu kỳ, số tiền, trạng thái (Chờ thanh toán, Đã thanh toán, Đã hủy, Hết hạn).

Nâng cấp hoặc gia hạn gói Chuyên Nghiệp (Quản trị hệ thống):
1. Ở khung "Đổi gói dịch vụ", chọn "Theo tháng" hoặc "Theo năm".
2. Bấm "Thanh toán nâng cấp" (hoặc "Thanh toán gia hạn"). Trình duyệt chuyển sang trang thanh toán (liên kết hiệu lực 15 phút).
3. Thanh toán xong, hệ thống quay về trang kết quả và tự kiểm tra: "Thanh toán thành công", "Giao dịch đã bị hủy", "Liên kết thanh toán đã hết hạn" hoặc "Đang chờ ngân hàng xác nhận" (bấm "Kiểm tra lại"). Bấm "Về trang gói dịch vụ".
Mỗi lần thanh toán cộng thêm 30 hoặc 365 ngày tính từ hôm nay hoặc từ ngày hết hạn đang còn, nên gia hạn sớm không mất ngày. Giao dịch đang chờ trong "Lịch sử giao dịch" có nút "Tiếp tục thanh toán" và "Kiểm tra trạng thái".

Chuyển về gói Khởi Đầu: bấm "Chuyển về gói Khởi Đầu" và xác nhận; chỉ làm được khi đang dùng thử hoặc khi gói đã hết hạn. Dữ liệu đã có được giữ nguyên.

Nhắc hạn và hết hạn:
- Khi đang dùng thử, đầu mọi trang có dải "Còn N ngày dùng thử".
- Khi gói hết hạn, đầu mọi trang có dải "Gói đã hết hạn, hệ thống đang ở chế độ chỉ đọc": vẫn xem được dữ liệu nhưng các nút tạo, sửa, xóa bị khóa; bấm vào sẽ hiện hộp thoại "Gói dịch vụ đã hết hạn". Vẫn đăng nhập, đổi mật khẩu, xem và thanh toán gói dịch vụ được. Gia hạn hoặc chuyển gói để mở lại.
- Tạo thêm người dùng, kho, SKU hay hỏi trợ lý AI khi đã vượt giới hạn của gói sẽ hiện hộp thoại "Đã chạm giới hạn của gói dịch vụ"; Quản trị hệ thống có nút "Nâng cấp gói", vai trò khác liên hệ quản trị viên.

## Nhập dữ liệu bằng AI

Chức năng dùng AI đọc danh mục hàng hóa từ tệp Excel, PDF hoặc ảnh chưa có màn hình trên hệ thống (máy chủ đã hỗ trợ, màn hình đang được xây dựng). Hiện có thể nhập nhiều mặt hàng bằng nút "Nhập Excel" ở menu "Danh mục hàng hóa".

## Trợ lý AI

Trợ lý AI trả lời câu hỏi về cách dùng phần mềm, tra cứu tồn kho theo mã SKU hoặc theo kho, và tra trạng thái đơn theo số chứng từ (PO-... cho đơn mua, SO-... cho đơn bán). Trợ lý chỉ đọc dữ liệu, không tạo, sửa hay xóa, và chỉ trả lời trong phạm vi dữ liệu vai trò của người hỏi được xem. Mọi tài khoản đều dùng được.

Cách hỏi:
1. Bấm nút tròn hình robot ở góc phải dưới của mọi trang để mở khung "Trợ lý AI" (trên điện thoại khung chiếm hết chiều ngang).
2. Gõ câu hỏi (tối đa 2000 ký tự), nhấn Enter để gửi, Shift+Enter để xuống dòng; hoặc bấm một câu hỏi gợi ý rồi điền mã SKU, số chứng từ vào chỗ "...".
3. Chờ trợ lý trả lời (có thể tới 90 giây). Dòng "Đã tra cứu" cho biết trợ lý đã đọc dữ liệu nào.
4. Hỏi tiếp trong cùng khung để trợ lý hiểu theo ngữ cảnh. Bấm "Cuộc trò chuyện mới" để bắt đầu lại. Cuộc trò chuyện hết hạn sau 1 giờ không hỏi thêm và mất khi tải lại trang.

Mỗi câu hỏi được trả lời dùng một lượt AI trong hạn mức hằng tháng của gói dịch vụ (câu bị từ chối không tính); khung chat hiện "Lượt AI tháng này". Lỗi thì bấm "Gửi lại". Nhãn "Chế độ giả lập" nghĩa là hệ thống chưa bật AI thật, trợ lý trả lời theo quy tắc. Khi gói đã hết hạn, trợ lý không nhận câu hỏi mới.
