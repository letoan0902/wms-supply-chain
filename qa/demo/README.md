# Công cụ tạo dữ liệu demo

`qa/demo/seed-demo.mjs` đăng ký năm doanh nghiệp mẫu và đổ vào đó dữ liệu kinh doanh trông như thật (kho, vị trí, hàng hóa,
đối tác, đơn mua đã nhập kho theo lô, đơn bán ở nhiều trạng thái, điều chuyển, kiểm kê, công nợ) để dashboard, bảng tồn,
cảnh báo, đơn hàng, công nợ và trang quản trị nền tảng có số liệu khi thuyết trình.

- Chỉ gọi API công khai qua api-gateway, giống hệt thao tác của người dùng trên giao diện. Không dùng SQL, không gọi
  endpoint nội bộ, không cần quyền quản trị nền tảng.
- Node.js 20 trở lên, không cần cài gói npm (dùng `fetch` có sẵn).
- Dữ liệu khai báo trong `data/<khóa>.json`, tiếng Việt, mọi tên doanh nghiệp, người, mã số thuế, số điện thoại đều hư
  cấu; email dùng tên miền `.example`.
- Công cụ KHÔNG BAO GIỜ tạo giao dịch thanh toán gói dịch vụ (PayOS, nâng cấp gói). Doanh nghiệp mới ở gói `TRIAL` và toàn
  bộ dữ liệu được tính để nằm trong giới hạn gói này. "Thanh toán" trong dữ liệu chỉ là bút toán nghiệp vụ trong phần mềm
  (trả tiền hóa đơn mua cho nhà cung cấp, phiếu thu tiền của khách), không chuyển tiền thật.

## 1. Năm doanh nghiệp

| Khóa | Doanh nghiệp | Quản trị | Mã dữ liệu |
| --- | --- | --- | --- |
| `trai-cay` | Công ty TNHH Nông Sản Trái Ngọt | `traingot.admin` | `TC-`, `NCC-TC-`, `KH-TC-` |
| `kho-van` | Công ty Cổ phần Kho Vận Tốc Hành | `tochanh.admin` | `KV-`, `NCC-KV-`, `KH-KV-` |
| `dien-may` | Công ty TNHH Điện Máy Gia Phát | `giaphat.admin` | `DM-`, `NCC-DM-`, `KH-DM-` |
| `my-pham` | Công ty TNHH Mỹ Phẩm An Nhiên | `annhien.admin` | `MP-`, `NCC-MP-`, `KH-MP-` |
| `van-phong-pham` | Công ty TNHH Văn Phòng Phẩm Sao Khuê | `saokhue.admin` | `VPP-`, `NCC-VPP-`, `KH-VPP-` |

Tiền tố `TCM-`, `KVM-`, `DMM-`, `NCC-TCM-`, `KH-TCM-` (và tương tự cho KV, DM) dành cho file Excel, PDF mà chủ dự án tự nhập
bằng AI trong buổi demo. Công cụ từ chối chạy nếu file dữ liệu dùng các tiền tố này.

Tài khoản thêm của mỗi doanh nghiệp (cùng mật khẩu `DEMO_PASSWORD`, xem mục `users` trong file dữ liệu):

- `trai-cay`: `traingot.kho` (quản lý kho), `traingot.nhapkho` (nhân viên kho Long Biên), `traingot.banhang` (bán hàng), `traingot.ketoan` (kế toán).
- `kho-van`: `tochanh.ql.bn`, `tochanh.ql.na`, `tochanh.ql.dn` (quản lý từng kho), `tochanh.nhanvien`, `tochanh.kinhdoanh`, `tochanh.muahang`, `tochanh.ketoan`.
- `dien-may`: `giaphat.quanlykho`, `giaphat.thukho`, `giaphat.muahang`, `giaphat.banhang1`, `giaphat.banhang2` (chỉ kho Bình Tân), `giaphat.ketoan`.
- `my-pham`: `annhien.banhang`; `van-phong-pham`: `saokhue.kho`.

Chạy với `--suffix X` thì mọi tên đăng nhập có thêm `-X`, tên doanh nghiệp thêm ` X`.

## 2. Dữ liệu tạo cho từng doanh nghiệp

| | trai-cay | kho-van | dien-may | my-pham | van-phong-pham |
| --- | --- | --- | --- | --- | --- |
| Người dùng (gồm quản trị) | 5 | 8 | 7 | 2 | 2 |
| Kho | 2 (Hà Nội, TP HCM) | 3 (Bắc Ninh, Nghệ An, Đồng Nai) | 2 (Hà Nội, TP HCM) | 1 | 1 |
| Vị trí Khu/Kệ/Ô | 37 | 96 | 52 | 6 | 6 |
| Danh mục, đơn vị tính | 7, 7 | 8, 4 | 7, 3 | 3, 4 | 3, 4 |
| Hàng hóa (có tồn tối thiểu, điểm đặt hàng lại) | 32, theo lô và hạn dùng | 36, đồ ăn uống theo lô | 35, không theo lô | 10 | 10 |
| Nhà cung cấp, khách hàng | 10, 26 | 9, 28 | 9, 28 (hạn mức công nợ) | 3, 5 | 3, 5 |
| Đơn mua | 10: 8 nhập đủ, 1 nhập dở, 1 chờ duyệt | 10: 8 nhập đủ, 1 nhập dở, 1 đã duyệt đang về | 10: 7 nhập đủ, 1 nhập dở, 1 đã duyệt, 1 chờ duyệt | 2 | 2 |
| Đơn bán | 17 | 19 | 20 | 3 | 3 |
| Điều chuyển | 3 (hoàn tất, đang chuyển, nháp) | 8 (hoàn tất, nhận thiếu hỏng, đang chuyển, nháp, đã hủy) | 3 | 0 | 0 |
| Kiểm kê | 1 hoàn tất (lệch -3), 1 kế hoạch | 1 hoàn tất (lệch -2 và +1), 1 kế hoạch | 1 hoàn tất (lệch -1), 1 kế hoạch | 0 | 0 |

Điểm nhấn để kể chuyện:

- `trai-cay`: lô đặt tên theo loại và ngày nhập (ví dụ `XOAI-HL-261106`, `DT-DL-261112`); hạn dùng tính theo ngày chạy nên
  luôn có khoảng 20 dòng cận hạn (30 ngày) và 2 lô đã hết hạn (dâu tây, măng cụt); vài mặt hàng chạm điểm đặt hàng lại.
- `kho-van`: hàng nằm ở cả ba kho, nhiều lệnh điều chuyển ở mọi trạng thái, một lệnh nhận thiếu do hai thùng bánh móp; đợt
  kiểm kê đếm mù khu B kho Bắc Ninh đã qua hai cấp duyệt (quản lý kho, kế toán) và điều chỉnh tồn.
- `dien-may`: hai đại lý vượt hạn mức (`KH-DM-011`, `KH-DM-019`, không lập được đơn mới), ba đại lý dùng 90 đến 95% hạn mức;
  nhiều mặt hàng bán chạy dưới mức tồn an toàn nên có cảnh báo sắp hết hàng; đơn thiếu hàng chờ nhập.
- Đơn bán trải đủ trạng thái: nháp, đã xác nhận và giữ chỗ, thiếu hàng (BACKORDER), giao một phần, hoàn tất, đã hủy. Đơn
  hoàn tất có hóa đơn bán (`INV-<số đơn>`) và một số phiếu thu một phần, cộng với nợ đầu kỳ nên có công nợ phải thu; đơn mua
  có hóa đơn mua tự sinh từ phiếu nhập, một số đã trả một phần nên có công nợ phải trả.

## 3. Mật khẩu `DEMO_PASSWORD`

Mọi lệnh đều cần biến môi trường `DEMO_PASSWORD` (8 đến 72 ký tự): đó là mật khẩu của tài khoản quản trị và mọi tài
khoản thêm. Công cụ không có mật khẩu mặc định, không in, không ghi mật khẩu ra đĩa. Nhập sao cho mật khẩu không nằm trong
lịch sử lệnh:

Bash:

```bash
read -rs -p "Mật khẩu demo: " DEMO_PASSWORD; echo; export DEMO_PASSWORD
node qa/demo/seed-demo.mjs status
unset DEMO_PASSWORD
```

PowerShell:

```powershell
$s = Read-Host "Mật khẩu demo" -AsSecureString
$env:DEMO_PASSWORD = [System.Net.NetworkCredential]::new('', $s).Password
node qa/demo/seed-demo.mjs status
Remove-Item Env:DEMO_PASSWORD
```

Ở máy phát triển của chủ dự án, mật khẩu của bộ dữ liệu đang có trên hệ thống thử được lưu trong
`D:\Rikkei\DevOps\WMS\demo\.demo-local.json` (khóa `demoPassword`, file không nằm trong repo):

```bash
export DEMO_PASSWORD="$(node -p "require('D:/Rikkei/DevOps/WMS/demo/.demo-local.json').demoPassword")"
```

```powershell
$env:DEMO_PASSWORD = (Get-Content D:\Rikkei\DevOps\WMS\demo\.demo-local.json -Raw | ConvertFrom-Json).demoPassword
```

Lưu ý: sai mật khẩu 5 lần trong 30 phút thì máy chủ khóa đăng nhập theo IP. Công cụ dừng ngay ở lần đăng nhập sai đầu tiên.

## 4. Chạy trên hệ thống thử ở máy

Hệ thống thử không yêu cầu kích hoạt email (`REGISTRATION_REQUIRE_ACTIVATION=false`), nên một lệnh là đủ. Chạy từ thư mục
gốc repo:

```bash
node qa/demo/seed-demo.mjs all --base-url http://127.0.0.1:18080
node qa/demo/seed-demo.mjs status --base-url http://127.0.0.1:18080
```

`all` = `register` rồi `populate`. Dùng `127.0.0.1` thay cho `localhost` để Windows không thử IPv6 trước (mỗi lời gọi chậm
2 giây). Lần đầu mất khoảng 2 phút cho năm doanh nghiệp (khoảng 1.200 request).

## 5. Chạy trên máy chủ thật (hai giai đoạn)

Máy chủ thật bắt kích hoạt doanh nghiệp qua email, nên email quản trị phải là hộp thư thật của bạn. Dùng địa chỉ có dấu `+`
(Gmail và nhiều dịch vụ khác giao về cùng một hộp thư), `{key}` được thay bằng khóa doanh nghiệp:

```bash
# Giai đoạn 1: đăng ký năm doanh nghiệp, máy chủ gửi năm email kích hoạt
node qa/demo/seed-demo.mjs register --base-url https://<địa chỉ máy chủ> --email-template "tenban+{key}@gmail.com"

# Mở hộp thư, bấm liên kết kích hoạt trong cả năm email (liên kết có hạn dùng)

# Giai đoạn 2: đổ dữ liệu và xem kết quả
node qa/demo/seed-demo.mjs populate --base-url https://<địa chỉ máy chủ>
node qa/demo/seed-demo.mjs status --base-url https://<địa chỉ máy chủ>
```

`register` in tên đăng nhập, email đã dùng và dừng với mã thoát 0 khi máy chủ yêu cầu kích hoạt, kèm lệnh `populate` cần
chạy tiếp. Nếu chạy `populate` khi chưa kích hoạt, công cụ báo doanh nghiệp nào chưa kích hoạt và dừng. Muốn chạy từng
doanh nghiệp: `--only trai-cay,kho-van`.

Trên máy chủ thật, bước cuối của `populate` gọi quét cảnh báo tồn kho: máy chủ gửi thông báo trong ứng dụng và email cảnh báo
cho quản trị (hộp thư của bạn) và quản lý kho (địa chỉ `.example`, không giao được). Không muốn gửi thì thêm
`--skip-alert-scan`; danh sách cảnh báo trên giao diện vẫn có vì được tính trực tiếp từ tồn kho.

## 6. Giới hạn gói TRIAL

Đọc từ `backend/auth-service/src/main/resources/db/migration/V5__create_plans_and_subscriptions.sql` và `GET /api/auth/plans`:

| Gói | Người dùng | Kho | SKU | Lượt AI/tháng | Thời hạn |
| --- | --- | --- | --- | --- | --- |
| `TRIAL` (doanh nghiệp mới đăng ký) | 50 | 20 | không giới hạn | 500 | 30 ngày |
| `FREE` | 3 | 1 | 200 | 20 | không |

Bộ dữ liệu dùng nhiều nhất 8 người dùng, 3 kho, 36 SKU một doanh nghiệp, nằm trong gói `TRIAL`. Trước khi chạy, công cụ đọc
giới hạn `TRIAL` từ máy chủ và từ chối nếu file dữ liệu vượt. Hết 30 ngày dùng thử, doanh nghiệp chuyển sang chỉ đọc: vẫn
xem được dữ liệu nhưng không ghi được nữa (nếu chuyển về `FREE` thì vượt giới hạn 3 người dùng, 1 kho của gói này).

## 7. Chạy lại

`populate` chạy lại bao nhiêu lần cũng được, kể cả sau khi dừng giữa chừng: bản ghi nào đã có thì bỏ qua, chứng từ nào dở
dang thì làm tiếp bước còn thiếu. Lần chạy thứ hai trên dữ liệu đầy đủ in "0 bản ghi tạo mới hoặc chuyển trạng thái".

| Đối tượng | Cách nhận ra đã có |
| --- | --- |
| Đơn vị tính, danh mục, kho, hàng hóa, nhà cung cấp, khách hàng | mã (`code`, `sku`) |
| Vị trí | khu, kệ, ô trong kho |
| Người dùng | tên đăng nhập |
| Đơn mua, đơn bán | số chứng từ cố định trong file (`PO-TC-001`, `SO-DM-012`, ...) |
| Phiếu nhập kho | khóa chống trùng `Idempotency-Key` = `<số đơn mua>-NK<thứ tự>` |
| Phiếu xuất kho | số phiếu xuất (không hủy) đã có của đơn bán |
| Hóa đơn bán, phiếu thu | mã chứng từ `INV-<số đơn>`, `PT-<số đơn>-<thứ tự>` trong lịch sử công nợ khách |
| Trả tiền hóa đơn mua | hóa đơn mua của đơn đã có số tiền đã trả |
| Điều chuyển, kiểm kê | mã yêu cầu trong ghi chú, ví dụ `(DC-KV-03)`, `(KK-TC-01)` |

Cơ sở dữ liệu đã có doanh nghiệp cùng tên đăng nhập nhưng mật khẩu khác (lần chạy cũ) thì dùng hậu tố, ví dụ
`--suffix r2`: tên đăng nhập, email, tên doanh nghiệp đổi theo, mã số thuế đổi sang dạng mã chi nhánh `XXXXXXXXXX-NNN`.
Công cụ không xóa gì.

Lỗi API (không phải 2xx, trừ trường hợp "đã có" ở trên) làm công cụ in phương thức, đường dẫn, mã HTTP, mã lỗi, thông báo
rồi thoát với mã 1. Sửa nguyên nhân rồi chạy lại cùng lệnh. Công cụ giãn nhịp khoảng 14 request mỗi giây (gateway giới hạn
20/giây theo người dùng) và tự thử lại khi gặp 429.

Máy chủ chậm (đã gặp trên production ngày 16/11/2026) được xử lý như sau, không cần can thiệp:

- Lập phiếu xuất kho trả 503 `SERVICE_UNAVAILABLE` vì order-service không kịp nhận xác nhận trừ tồn từ inventory-service
  trong 5 giây: phiếu đã được lưu ở PICKING, công cụ gọi `POST /api/orders/goods-delivery-notes/{id}/dispatch` cho đúng
  phiếu đó (tối đa 6 lần, giãn dần), không lập phiếu mới. Thao tác xuất lại dùng chung một khóa nên tồn không bị trừ
  hai lần. Phiếu PICKING còn sót từ lần chạy trước cũng được xuất lại khi chạy lại `populate`.
- Lỗi tạm thời khác (rớt mạng, 502, 503, 504): chờ rồi chạy lại cả doanh nghiệp đang dở, tối đa 4 lần; các bước đều kiểm
  tra cái đã có nên không tạo trùng.

## 8. Ngày tháng

Mọi ngày trong file dữ liệu tính tương đối với ngày chạy theo giờ Việt Nam (`daysAgo`, `expInDays`, `mfgDaysAgo`,
`plannedDaysAgo`, `expectedInDays`). API cho ghi ngày ở: ngày đặt đơn mua, đơn bán, ngày dự kiến, ngày nhận của phiếu nhập,
ngày sản xuất, hạn dùng của lô, ngày xuất của phiếu xuất, ngày trả tiền hóa đơn mua, ngày kế hoạch kiểm kê. Những thứ API
không cho ghi lùi nên mang thời điểm chạy: thời điểm tạo bản ghi, lệnh điều chuyển (ngày lập, ngày xuất, ngày nhận), bút
toán công nợ khách hàng (hóa đơn bán, phiếu thu, nợ đầu kỳ), các lần duyệt. Lô đã hết hạn được tạo bằng phiếu nhập ghi lùi
ngày nhận (hạn dùng sau ngày nhận nhưng trước hôm nay), đúng như API cho phép.

## 9. Cấu trúc file dữ liệu

Mỗi file `data/<khóa>.json` gồm: `company` (tên, mã số thuế, quản trị), `users`, `uoms`, `categories`, `warehouses` (kèm
`locations`: khu, khoảng kệ, khoảng ô), `products`, `suppliers`, `customers`, `purchaseOrders` (kèm `receipts` theo lô với
`lot`, `location` dạng `A-R01-B02`, `payment`), `stocktakes`, `transfers`, `salesOrders` (`state`: `draft`, `confirmed`,
`backorder`, `partial`, `completed`, `cancelled`; kèm `deliveries`, `invoice`, `payments`). Trong số lô, `{date}` được thay
bằng ngày nhận dạng `yyMMdd`. Thứ tự đơn bán trong file là thứ tự lập, đơn trước dùng tồn trước.

## 10. Giới hạn đã biết

- Thẻ "Giá trị tồn kho" trên dashboard luôn hiện "Chưa tính được" vì hệ thống chưa có giá vốn tồn kho (thiết kế hiện tại
  của inventory-service), không phải do thiếu dữ liệu.
- Không tạo được cảnh báo hàng chậm luân chuyển (DEAD_STOCK, cần 90 ngày không biến động) vì không ghi lùi được biến động kho.
- Hàng không quản lý theo lô được hệ thống tự gán số lô dạng `LOT-GRN-<ngày>-<số>`.
- `status` đếm qua API của quản trị từng doanh nghiệp; trang quản trị nền tảng (SUPER_ADMIN) không được công cụ đăng nhập.
