# Đường ống CI/CD (GitHub Actions)

Tệp cấu hình: `.github/workflows/ci.yml`. Mỗi lần push lên bất kỳ nhánh nào hoặc mở Pull Request, GitHub Actions chạy các bước sau.

## 1. Các job

| Job | Khi nào chạy | Làm gì |
|---|---|---|
| Backend | mọi push và PR | Cài JDK 17, `./gradlew build` cho toàn bộ multi-project (biên dịch và chạy unit test, integration test), tải báo cáo test lên artifact |
| Frontend | mọi push và PR | Cài Node 20, `npm ci`, `npm run lint` (ESLint), `npm run build` (Vite) |
| Docker (api-gateway, inventory-service, frontend) | mọi push, sau khi Backend và Frontend xanh | Build thử 3 image đại diện để bắt lỗi Dockerfile sớm, không đẩy lên registry |
| Publish (9 service) | chỉ khi push vào `develop` hoặc `main` (tức là sau khi merge PR) | Build và đẩy image của 8 service backend và frontend lên GitHub Container Registry (GHCR) |

Hai job Backend và Frontend là kiểm tra bắt buộc của nhánh `develop` và `main`: PR phải xanh cả hai và có 1 người duyệt mới merge được.

## 2. Image trên GHCR

Tên image: `ghcr.io/letoan0902/wms-supply-chain/<service>` với `<service>` là một trong: `config-server`, `discovery-server`, `api-gateway`, `auth-service`, `product-service`, `inventory-service`, `order-service`, `notification-service`, `frontend`.

Thẻ (tag):

- `develop`: bản mới nhất của nhánh develop (môi trường kiểm thử nội bộ).
- `latest`: bản mới nhất của nhánh main (bản phát hành).
- `sha-<7 ký tự commit>`: luôn được gắn thêm để truy vết chính xác commit đã build.

Đăng nhập GHCR bằng `GITHUB_TOKEN` của chính workflow (quyền `packages: write` khai báo trong job), không cần tạo secret. Lần đầu publish, các package sẽ ở chế độ riêng tư; vào trang Packages của repo đổi sang Public nếu muốn máy chủ kéo image không cần đăng nhập.

Kéo image về máy hoặc máy chủ:

```bash
docker pull ghcr.io/letoan0902/wms-supply-chain/api-gateway:develop
```

Máy chủ triển khai (WMS-21.3) sẽ dùng các image này thay cho bước build tại chỗ: khai báo `image:` trong tệp compose của môi trường production và chạy `docker compose pull && docker compose up -d`.

## 3. Cache và thời gian chạy

Build image dùng cache của GitHub Actions theo từng service (`type=gha,scope=<service>`), nên lần chạy sau chỉ build lại lớp thay đổi. Job Publish chạy song song 9 service, mỗi service giới hạn 40 phút.

## 4. Việc nối tiếp

- Thêm bước phân tích tĩnh (Checkstyle, SpotBugs) vào job Backend khi nhóm thống nhất bộ quy tắc.
- Job triển khai tự động lên máy chủ sau khi publish image từ `main` (WMS-21.3).
