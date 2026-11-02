# Smoke test sau triển khai

`deploy/smoke-test.sh` gọi nhanh các đường dẫn chính để xác nhận một bản triển khai hoạt động: giao diện web, đăng nhập, JWT và hai service nghiệp vụ.
Mỗi bước in `PASS`, `FAIL` hoặc `SKIP` kèm mã HTTP và thời gian phản hồi. Có bước `FAIL` thì mã thoát là 1 (thiếu công cụ hoặc tham số sai là 2),
nên có thể dùng script trong CI hoặc sau `deploy.sh`.

Yêu cầu: `bash`, `curl` và `python3` (Ubuntu có sẵn). Không có `python3` thì script dùng `jq` nếu đã cài; thiếu cả hai thì dừng và in lệnh cài đặt.

## 1. Mật khẩu dùng để kiểm tra

Script không chứa mật khẩu. Thứ tự lấy mật khẩu: tham số thứ ba, biến môi trường `SMOKE_PASSWORD`, hoặc hỏi (không hiện ký tự) khi chạy trong terminal.

Các tài khoản seed do Flyway tạo từ `backend/auth-service/src/main/resources/db/migration/V2__seed_default_accounts.sql`
(`admin`, `manager`, `staff`, `purchasing`, `sales`, `accountant`, `demo`) có chung mật khẩu mặc định `Demopassword`, ghi trong chú thích đầu file migration đó.
Nếu đã đổi mật khẩu theo mục 7 của [README.md](README.md) thì dùng mật khẩu mới.

## 2. Cách chạy

Trên máy chủ, trong thư mục mã nguồn:

```bash
cd /opt/wms-supply-chain
bash deploy/smoke-test.sh                          # qua tên miền https://wms.toanmarket.me, hỏi mật khẩu admin
bash deploy/smoke-test.sh http://127.0.0.1:8080    # nội bộ: gọi thẳng api-gateway, thêm bước /actuator/health
```

Chạy không tương tác (ví dụ ngay sau `deploy.sh`): nhập mật khẩu vào biến một lần, không lưu vào lịch sử lệnh.

```bash
read -rsp 'Mật khẩu admin: ' SMOKE_PASSWORD; echo; export SMOKE_PASSWORD
bash deploy/smoke-test.sh && bash deploy/smoke-test.sh http://127.0.0.1:8080
```

Từ máy cá nhân (Linux, macOS hoặc Git Bash trên Windows có `curl` và `python3`), trong thư mục repository:

```bash
bash deploy/smoke-test.sh                                    # tài khoản admin
bash deploy/smoke-test.sh https://wms.toanmarket.me manager  # tài khoản khác
```

Cổng 8080 của api-gateway chỉ mở trên `127.0.0.1` của máy chủ. Muốn kiểm tra nội bộ từ máy cá nhân, mở SSH tunnel ở một cửa sổ rồi chạy script ở cửa sổ khác
(nếu cổng 8080 trên máy cá nhân đang bận, đổi thành `-L 18080:127.0.0.1:8080` và dùng `http://127.0.0.1:18080`):

```bash
ssh -N -L 8080:127.0.0.1:8080 root@wms.toanmarket.me
bash deploy/smoke-test.sh http://127.0.0.1:8080
```

| Tham số hoặc biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| Tham số 1 hoặc `BASE_URL` | `https://wms.toanmarket.me` | `https://` đi đúng đường của người dùng (Caddy -> nginx -> api-gateway); `http://` là kiểm tra nội bộ thẳng tới api-gateway |
| Tham số 2 hoặc `SMOKE_USERNAME` | `admin` | Tài khoản đăng nhập. Không dùng tên biến `USERNAME` vì Windows tự đặt biến này |
| Tham số 3 hoặc `SMOKE_PASSWORD` | hỏi khi chạy | Nên dùng biến hoặc lời hỏi: mật khẩu truyền qua tham số lộ trong lịch sử lệnh và danh sách tiến trình |
| `SMOKE_TIMEOUT` | `10` | Số giây tối đa cho mỗi request |
| `SMOKE_INSECURE=1` | tắt | Bỏ kiểm tra chứng chỉ TLS, chỉ dùng khi Caddy đang lấy chứng chỉ staging của Let's Encrypt |

Quyền thực thi: file được tạo trên Windows nên chưa có bit thực thi, nhưng luôn chạy được bằng `bash deploy/smoke-test.sh`.
`chmod +x` trên máy chủ sẽ bị `deploy.sh` hoàn tác ở lần triển khai sau (`git reset --hard` đưa file về chế độ lưu trong Git),
nên hãy ghi bit thực thi vào Git khi commit từ Windows: `git add deploy/smoke-test.sh && git update-index --chmod=+x deploy/smoke-test.sh`.

## 3. Các bước kiểm tra

| Bước | Request | Đạt khi | Chạy khi |
| --- | --- | --- | --- |
| Trang chủ | `GET /` | 200 và nội dung có `<div id="root"` hoặc `<!doctype html>` | `BASE_URL` là `https://` |
| Đăng nhập đúng | `POST /api/auth/login`, thân `{"username": "...", "password": "..."}` | 200, `success` là `true`, có `data.accessToken` | luôn chạy |
| Đăng nhập sai | `POST /api/auth/login` với mật khẩu sai | 401 | bước đăng nhập đúng đạt |
| Người dùng hiện tại | `GET /api/auth/me` kèm `Authorization: Bearer <token>` | 200, `data.username` trùng tài khoản vừa đăng nhập | có token |
| Danh sách kho | `GET /api/inventory/warehouses` kèm token | 200, `success` là `true` (in số kho) | có token |
| Danh sách sản phẩm | `GET /api/products?page=0&size=1` kèm token | 200, `success` là `true` (in tổng số sản phẩm) | có token |
| Sức khỏe gateway | `GET /actuator/health` | 200 và `status` là `UP` | `BASE_URL` là `http://` |

- Các API trả về theo khung `ApiResponse` của module `common`: `{"success": true, "code": "OK", "message": "...", "data": {...}, "timestamp": "..."}`.
  Khi đăng nhập, `data` gồm `accessToken`, `tokenType`, `expiresIn`, `username`, `roles`.
- Ở chế độ `https://`, request đăng nhập gửi kèm `Origin: https://wms.toanmarket.me` giống trình duyệt, nên bước này phát hiện được `CORS_ALLOWED_ORIGINS` sai.
  Request GET không gửi `Origin`, cũng giống trình duyệt khi gọi API cùng origin.
- Chống dò mật khẩu: auth-service khóa tài khoản và IP 30 phút sau 5 lần đăng nhập sai. Script đăng nhập đúng trước; bước sai mật khẩu chỉ chạy khi bước đó thành công
  và chỉ gọi một lần, sau đó script đăng nhập đúng thêm một lần để auth-service xóa bộ đếm. Một lần chạy thành công không để lại lần sai nào;
  chạy với mật khẩu sai chỉ cộng một lần sai mỗi lần chạy.
- Token và mật khẩu không bao giờ được in ra; chúng được đưa cho `curl` qua stdin. Request mang User-Agent `wms-smoke-test` để dễ lọc trong log của Caddy.

## 4. Đọc kết quả và lỗi thường gặp

Mỗi dòng có dạng `TRẠNG THÁI  [bước/tổng] mô tả (HTTP mã, thời gian ms)`, các dòng thụt vào bên dưới là phản hồi của máy chủ và gợi ý xử lý. Ví dụ (số liệu minh họa):

```text
PASS  [2/6] Đăng nhập đúng bằng tài khoản admin: POST /api/auth/login (HTTP 200, 312 ms)
      Nhận accessToken của admin (vai trò ADMIN, hết hạn sau 900 giây).
FAIL  [5/6] Danh sách kho (inventory-service): GET /api/inventory/warehouses (HTTP 503, 21 ms)
      Phản hồi: Service Unavailable
      Gợi ý: inventory-service chưa đăng ký với Eureka (...)

Kết quả: 5 PASS, 1 FAIL, 0 SKIP (6 bước)
```

`SKIP` nghĩa là bước không chạy vì bước trước đã hỏng (không kết nối được, hoặc đăng nhập thất bại nên không có token): hãy xử lý dòng `FAIL` đầu tiên trước.

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `HTTP 000` kèm dòng `curl: (...)` | Không nhận được phản hồi: `(6)` không phân giải được tên miền, `(7)` dịch vụ chưa chạy hoặc sai cổng, `(28)` quá thời gian chờ, `(35)` hoặc `(60)` chứng chỉ TLS chưa hợp lệ. Các bước sau chuyển thành `SKIP` thay vì chờ hết timeout | Làm theo gợi ý ngay dưới dòng `FAIL`; trên máy chủ xem `docker compose ps` |
| `401` ở bước đăng nhập đúng | Sai mật khẩu; thông báo cho biết còn bao nhiêu lần thử | Sửa `SMOKE_PASSWORD` trước khi chạy lại, đừng chạy lặp |
| `401` ở các bước dùng token | Token bị từ chối. Thông báo `Token không hợp lệ hoặc đã hết hạn` hoặc `Thiếu token xác thực` đến từ api-gateway (đang bật `GATEWAY_JWT_ENABLED`): `JWT_SECRET` của api-gateway và auth-service khác nhau, thường do đổi `.env` nhưng chỉ tạo lại một container, hoặc đồng hồ máy chủ bị lệch. Thông báo `Chưa đăng nhập hoặc token không hợp lệ` đến từ auth-service. Token sống 15 phút nên không thể hết hạn giữa một lần chạy | `docker compose up -d api-gateway auth-service`; kiểm tra giờ bằng `timedatectl` |
| `503` | api-gateway không tìm thấy instance của service đích trong Eureka: container đang khởi động, đang khởi động lại hoặc đã dừng. Service vừa healthy cần thêm khoảng vài chục giây để đăng ký và để gateway làm mới danh sách (5 giây một lần) | Đợi 30 đến 60 giây rồi chạy lại; `docker compose ps`, `docker compose logs --tail 100 <service>`; xem Eureka qua SSH tunnel (mục 8.2 của README.md) |
| `503` ở bước `/actuator/health` | Bản thân api-gateway báo DOWN; script liệt kê thành phần chưa UP, ví dụ `redis=DOWN` | `docker compose logs --tail 100 api-gateway redis` |
| `429` | Giới hạn tốc độ của api-gateway (token bucket trên Redis, mặc định 20 request mỗi giây, bùng nổ 40, tính theo người dùng đã đăng nhập hoặc theo IP). Script chỉ gửi khoảng 8 request nên 429 thường do lưu lượng khác của cùng tài khoản hoặc IP (kiểm thử tải, nhiều người dùng chung tài khoản admin) | Đợi vài giây rồi chạy lại; nếu cần, tăng `RATE_LIMIT_REPLENISH_RATE` và `RATE_LIMIT_BURST_CAPACITY` trong `.env` rồi `docker compose up -d api-gateway` |
| `400` ở bước đăng nhập | Tài khoản hoặc IP đang bị khóa 30 phút sau 5 lần sai | `docker compose exec redis redis-cli --scan --pattern 'login:lock:*'` rồi `docker compose exec redis redis-cli DEL <khóa>` |
| `403` `Invalid CORS request` ở bước đăng nhập | `CORS_ALLOWED_ORIGINS` không chứa `https://<DOMAIN>` | Sửa `.env` rồi `docker compose up -d api-gateway` |
| `502` | Caddy không tới được frontend, hoặc nginx của frontend không tới được api-gateway | `docker compose ps`, `docker compose logs --tail 100 frontend api-gateway` |
| `500` | Lỗi bên trong service, thường do MySQL hoặc Redis | `docker compose logs --tail 100 <service>` |
