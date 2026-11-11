# Bộ kiểm thử tự động WMS

Thư mục `qa/` chứa bộ kiểm thử đầu cuối chạy qua api-gateway (`http://localhost:8080`) cho các task QA:
WMS-20.1 (luồng mua, nhập kho), WMS-20.2 (bán, xuất kho, điều chuyển, kiểm kê), WMS-22.5 (cách ly doanh nghiệp, giới hạn gói, hết hạn, API key), WMS-19.2 (tải đồng thời).
Bảng test case, hành vi dự kiến không đạt và truy vết yêu cầu ở [docs/TEST_PLAN.md](../docs/TEST_PLAN.md).

## 1. Thành phần

| Đường dẫn | Nội dung |
| --- | --- |
| `postman/00-setup.postman_collection.json` | Đăng ký doanh nghiệp A, B; tạo và đăng nhập tài khoản sáu vai trò của A |
| `postman/10-inbound.postman_collection.json` | WMS-20.1: đơn vị tính, danh mục, hàng hóa, kho, vị trí, nhà cung cấp, đơn mua, phiếu nhập theo lô, hóa đơn mua, công nợ |
| `postman/20-outbound-transfer-stocktake.postman_collection.json` | WMS-20.2: khách hàng, đơn bán, giữ chỗ, FEFO, phiếu xuất, điều chuyển, kiểm kê, sổ kho, truy vết lô |
| `postman/30-isolation-plan-expiry.postman_collection.json` | WMS-22.5: B thử đọc, sửa, xóa dữ liệu của A ở mọi nhóm; giới hạn gói FREE; hết hạn và thanh toán giả lập; API key |
| `postman/wms-qa.postman_environment.json` | Môi trường: `baseUrl` và `runId` (để trống, truyền khi chạy). Không chứa bí mật |
| `postman/files/hang-hoa-qa.csv` | File mẫu cho API trích xuất bằng AI |
| `docker-compose.qa.yml` | File compose bổ sung bật các công tắc cho stack kiểm thử |
| `run-all.mjs` | Chạy lần lượt các collection bằng Postman CLI với cùng một `runId`, tùy chọn chạy cả kịch bản tải |
| `load/concurrency.mjs` | WMS-19.2: kịch bản tải đồng thời và kiểm tra bất biến tồn kho, không cần thư viện ngoài |

## 2. Yêu cầu

- Docker Engine và Docker Compose v2, máy còn khoảng 8 GB RAM cho 13 container.
- Node.js 20 trở lên (có sẵn `fetch`).
- Postman CLI (`postman --version`; đã thử với bản 1.74). Chạy collection từ file, không cần đăng nhập Postman.
- Cổng 8080 trên máy chạy kiểm thử truy cập được tới api-gateway.

## 3. Dựng stack kiểm thử

Từ thư mục gốc repo:

```bash
cp .env.example .env    # đặt MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET (tối thiểu 32 ký tự)
docker compose -f docker-compose.yml -f qa/docker-compose.qa.yml up -d --build
docker compose -f docker-compose.yml -f qa/docker-compose.qa.yml ps    # chờ mọi service ở trạng thái healthy
curl -s http://localhost:8080/actuator/health                          # {"status":"UP",...}
```

File `qa/docker-compose.qa.yml` đặt các biến sau (ghi đè giá trị trong `.env`):

| Biến | Service | Giá trị | Vì sao |
| --- | --- | --- | --- |
| `GATEWAY_JWT_ENABLED` | api-gateway, notification-service | `true` | Gateway mới kiểm tra token và gắn `X-Company-Id`, `X-User-*`, `X-Plan`; tắt thì mọi kiểm tra quyền và cách ly đều vô nghĩa |
| `REGISTRATION_REQUIRE_ACTIVATION` | auth-service | `false` | Đăng ký xong doanh nghiệp ở trạng thái ACTIVE. Postman không đọc được liên kết kích hoạt mà notification-service ghi ra log; nếu biến này chưa tắt, `00-setup` dừng ngay ở TC-SET-02.1 với thông báo hướng dẫn |
| `APP_DEMO_TOOLS` | auth-service | `true` | Bật `POST /api/auth/subscription/demo/expire` cho phần hết hạn (TC-EXP-01). `docker-compose.yml` không truyền biến này vào container nên phải dùng file bổ sung |
| `PAYOS_MOCK` | auth-service | `true` | Thanh toán giả lập: `POST /api/auth/payments/{orderCode}/mock-confirm` chạy đúng đường xử lý của webhook hợp lệ |
| `AI_MOCK` | ai-service | `true` | Không gọi Claude; câu trả lời giả lập vẫn bị tính lượt (`AI_MOCK_COUNTS_USAGE` mặc định `true`) nên thử được giới hạn 20 lượt của gói FREE |
| `RATE_LIMIT_ENABLED` | api-gateway | `false` (đổi bằng `QA_RATE_LIMIT_ENABLED`) | Giới hạn chung 20 request/giây, bùng nổ 40 theo người dùng: Postman CLI gửi liên tiếp nhanh hơn mức này và kịch bản tải bắn 40 request/giây bằng một tài khoản. Giới hạn riêng của API key (`API_KEY_RATE_LIMIT_PER_SECOND`, 20/giây) vẫn bật nên ca 429 của API key vẫn kiểm tra được |

Các biến khác giữ như `.env` (`MAIL_HOST` để trống là được). Không cần dữ liệu mẫu: mọi doanh nghiệp đều do bộ kiểm thử
đăng ký mới.

## 4. Chạy

### 4.1. Một lệnh

```bash
node qa/run-all.mjs                      # sinh runId mới, chạy 00-setup, 10-inbound, 20-outbound-transfer-stocktake, 30-isolation-plan-expiry
node qa/run-all.mjs --base-url http://<máy chủ>:8080 --output qa/results
node qa/run-all.mjs --load              # chạy thêm qa/load/concurrency.mjs (cả hai chế độ) sau các collection
```

Script in `runId` ở đầu, dừng nếu `00-setup` thất bại, chạy tiếp các collection còn lại dù có test lỗi, cuối cùng in
bảng tổng hợp và thoát mã 1 nếu có collection lỗi. Tùy chọn: `--run-id <chuỗi chữ thường và số>`, `--only 00,10`,
`--output <thư mục>` (báo cáo YAML của Postman CLI), `--poll-max`, `--poll-delay-ms`.

### 4.2. Từng collection

Mọi collection phải dùng cùng một `runId` với lượt chạy `00-setup` (tên đăng nhập, email, mật khẩu kiểm thử đều suy ra
từ `runId`). Chạy từ thư mục gốc repo:

```bash
RUN_ID=qa$(date +%s)
for c in 00-setup 10-inbound 20-outbound-transfer-stocktake 30-isolation-plan-expiry; do
  postman collection run qa/postman/$c.postman_collection.json \
    -e qa/postman/wms-qa.postman_environment.json --env-var runId=$RUN_ID \
    --working-dir qa/postman --timeout-request 180000 || echo "$c có test lỗi"
done
```

PowerShell:

```powershell
$runId = "qa" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
foreach ($c in @("00-setup", "10-inbound", "20-outbound-transfer-stocktake", "30-isolation-plan-expiry")) {
  postman collection run "qa/postman/$c.postman_collection.json" -e qa/postman/wms-qa.postman_environment.json `
    --env-var "runId=$runId" --working-dir qa/postman --timeout-request 180000
}
```

Thứ tự: `00-setup` luôn chạy trước. `20` tự tạo kho, hàng hóa và tồn riêng, chỉ ca TC-RPT-02 (truy vết lô của `10`) cần
`10` chạy trước. Phần TC-ISO của `30` lấy ID dữ liệu của A bằng API danh sách nên cần `10` và `20` chạy trước; phần TC-PLAN, TC-EXP, TC-KEY tự đăng ký doanh nghiệp C và chỉ cần `00-setup`.
Mỗi collection tự đăng nhập lại ở thư mục đầu tiên nên chạy riêng được sau `00-setup` (token sống 15 phút, mỗi collection
chạy xong trong vài phút).

`--working-dir qa/postman` cần cho request tải file CSV (`src` của form-data là `files/hang-hoa-qa.csv`). Biến tùy chọn
truyền bằng `--env-var`: `baseUrl` (mặc định `http://localhost:8080`), `pollMax` (số lần hỏi lại khi chờ sự kiện
RabbitMQ, mặc định 20), `pollDelayMs` (mặc định 1500).

### 4.3. Kịch bản tải đồng thời (WMS-19.2)

```bash
node qa/load/concurrency.mjs                                  # cả hai chế độ, 40 request/giây trong 30 giây
node qa/load/concurrency.mjs --mode load --rps 50 --duration 60 --initial-stock 500
node qa/load/concurrency.mjs --mode idempotency
node qa/load/concurrency.mjs --base-url http://<máy chủ>:8080 --max-p95-ms 500
```

Script tự đăng ký một doanh nghiệp mới (cần `REGISTRATION_REQUIRE_ACTIVATION=false`), chuẩn bị kho, một sản phẩm và tồn
đã biết, rồi:

- Chế độ `load`: bắn đều `--rps` request mỗi giây trong `--duration` giây, trộn lập đơn bán nháp, xác nhận đơn (cùng giữ
  một tồn), lập phiếu xuất cho đơn đã xác nhận, đọc tồn, danh sách đơn bán, lô, phiếu xuất. Hết giờ thì chờ sự kiện xử lý
  xong rồi kiểm tra bất biến qua API: `I1` tồn và giữ chỗ không âm; `I2` giữ chỗ không vượt tồn (tổng và từng lô);
  `I3` tổng đã xuất không vượt tồn đã nhập; `I4` tồn = đã nhập - đã xuất (mỗi phiếu xuất trừ đúng một lần); `I5` giữ
  chỗ bằng tổng số lượng các đơn đang CONFIRMED; `I6` tổng cam kết không vượt tồn; `I7` sổ kho khớp tồn; `I8` không có
  5xx, lỗi mạng; `I9` không có phản hồi nhắc tới deadlock.
- Chế độ `idempotency`: `G1` hai phiếu nhập giống nhau nhận đủ phần còn lại gửi cùng lúc; `G2` hai phiếu nhập từng phần
  giống nhau gửi cùng lúc; `G3` hai lần xác nhận cùng một đơn bán (giữ chỗ một lần) và `G3b` (chỉ một lần trả 200); `G4` hai phiếu xuất đủ số lượng gửi cùng lúc; `G5`
  bấm xác nhận xuất hai lần cùng lúc cho một phiếu nháp. Mỗi trường hợp kiểm tra tồn chỉ thay đổi đúng một lần.

Kết quả: bảng độ trễ p50, p95, p99, lớn nhất theo API (cột NFR ghi "vượt" khi p95 lớn hơn 500 ms theo SRS), bảng lỗi
theo API, mã HTTP và `code`, danh sách bất biến ĐẠT hoặc VI PHẠM. Mã thoát: 0 khi mọi bất biến đạt, 1 khi có vi phạm,
2 khi bước chuẩn bị lỗi. `--json <file>` ghi toàn bộ kết quả ra file. Sau khi chạy nên xem thêm
`docker compose logs inventory-service order-service | grep -i -E "deadlock|lock wait"`.

## 5. Đọc kết quả

- Postman CLI in từng request với các test `Pass` hoặc lỗi, cuối cùng là bảng tổng hợp và danh sách lỗi chi tiết (kèm 500
  ký tự đầu của phản hồi). Mã thoát khác 0 khi có test lỗi. `--output <thư mục>` ghi thêm báo cáo YAML.
- Tên test là hành vi cần có, ví dụ "Nhân viên kho duyệt đơn mua: HTTP 403, mã FORBIDDEN". Mọi request còn có test chung
  "Không có lỗi máy chủ (HTTP 5xx)".
- "Thiếu dữ liệu tiền đề (...), bỏ qua request": một bước trước đó không tạo được ID cần dùng nên request bị bỏ qua. Sửa
  lỗi đầu tiên trong chuỗi trước, các lỗi sau thường tự hết.
- Các request chờ sự kiện RabbitMQ (tồn sau phiếu nhập, giữ chỗ, trừ tồn sau phiếu xuất) tự hỏi lại tối đa `pollMax` lần;
  chỉ lần cuối mới ghi kết quả test, nên các lần chờ không bị tính là lỗi.
- Ca dự kiến không đạt do mã nguồn chưa đáp ứng yêu cầu được liệt kê ở mục 2 của `docs/TEST_PLAN.md`.
- Ghi "đạt" hoặc "không đạt" vào cột "Kết quả" của `docs/TEST_PLAN.md`.

## 6. Làm sạch giữa các lượt chạy

Không cần xóa dữ liệu hay khởi động lại stack giữa các lượt chạy, vì mỗi lượt dùng `runId` mới:

- `00-setup`: doanh nghiệp A, B mới; tên đăng nhập, email (duy nhất toàn hệ thống) chứa `runId`; mã số thuế ngẫu nhiên;
  mã doanh nghiệp do hệ thống sinh.
- `10`, `20`: mã SKU, mã kho, mã đối tác, số lô viết cố định nhưng chỉ cần duy nhất trong doanh nghiệp, và A là doanh
  nghiệp mới của lượt chạy; số chứng từ do hệ thống cấp theo doanh nghiệp.
- `30`: doanh nghiệp C mới theo `runId`; B tạo dữ liệu trùng mã với A trong chính B; giao dịch thanh toán, API key do hệ
  thống sinh. Key và giao dịch chờ của A được thu hồi, hủy ở cuối nhóm tương ứng.
- Kịch bản tải: tự đăng ký doanh nghiệp riêng với hậu tố ngẫu nhiên mỗi lần chạy.

Chạy lại một collection với `runId` cũ sẽ lỗi trùng mã (409) hoặc trùng tên đăng nhập: muốn chạy lại thì chạy từ
`00-setup` với `runId` mới. Dữ liệu các lượt cũ chỉ làm cơ sở dữ liệu lớn dần; muốn xóa hết thì
`docker compose -f docker-compose.yml -f qa/docker-compose.qa.yml down -v` (mất toàn bộ dữ liệu của stack).

Bộ kiểm thử không gửi mật khẩu sai nên không chạm khóa đăng nhập theo IP (5 lần sai trong 30 phút).

## 7. Giới hạn đã biết

- Bộ kiểm thử được viết khi chưa có stack chạy: đã kiểm tra JSON, cú pháp mọi script, tên request duy nhất, mọi biến
  `{{...}}` đều có bước tạo, và chạy thử bằng Postman CLI với một máy chủ giả để bắt lỗi script; chưa chạy với hệ thống thật.
  Mọi request được đối chiếu với controller, DTO và service hiện có (đường dẫn, tham số, tên trường, enum, mã lỗi).
- PayOS và Claude chạy giả lập; luồng thật, email thật, giao diện nằm ở mục kiểm tra thủ công của `docs/TEST_PLAN.md`.
- Ngày (hạn dùng, ngày nhận) tính theo giờ Việt Nam phía Postman, khớp `TZ=Asia/Ho_Chi_Minh` của container; chạy sát nửa
  đêm có thể lệch một ngày.
- Thứ tự lô trong danh sách lấy hàng được kiểm tra khi các lô ở cùng một vị trí (inventory-service sắp theo khu, kệ, ô rồi
  mới tới hạn dùng).
- Số liệu ở `10` và `20` giả định doanh nghiệp A chỉ có dữ liệu do chính lượt chạy tạo ra (đúng khi chạy theo thứ tự với
  `runId` mới).
