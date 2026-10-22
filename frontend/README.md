# WMS Supply Chain - Frontend

Giao diện web của hệ thống quản lý kho và chuỗi cung ứng WMS Supply Chain.

## Công nghệ

| Thành phần | Phiên bản / thư viện |
| --- | --- |
| Framework | React 18, TypeScript, Vite |
| Giao diện | Ant Design 5, Tailwind CSS 4 |
| Biểu đồ | Recharts |
| Gọi API | Axios |
| Điều hướng | React Router |
| Kiểm tra mã | ESLint (cấu hình sẵn của Vite) |

## Chạy trên máy dev

Yêu cầu Node.js 20.19 trở lên.

```bash
cd frontend              # từ thư mục gốc của repository
cp .env.example .env     # tùy chọn, mặc định VITE_API_URL=/api
npm install
npm run dev              # http://localhost:5173
```

Khi chạy `npm run dev`, Vite chuyển tiếp mọi request `/api` tới API Gateway tại `http://localhost:8080`
(xem `vite.config.ts`). Cần khởi động backend (hoặc ít nhất api-gateway và auth-service) để đăng nhập.

## Các lệnh

| Lệnh | Tác dụng |
| --- | --- |
| `npm run dev` | Chạy máy chủ phát triển có hot reload |
| `npm run lint` | Kiểm tra ESLint |
| `npm run build` | Kiểm tra kiểu TypeScript và build bản production vào `dist/` |
| `npm run preview` | Chạy thử bản build |

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `VITE_API_URL` | `/api` | URL gốc của API. Giữ mặc định để dùng proxy của Vite (dev) hoặc Nginx (Docker). |

## Cấu trúc thư mục

```text
src/
  app/                 Khung ứng dụng: App, router, MainLayout (sidebar), danh sách menu
  pages/               Các trang: Dashboard, trang giữ chỗ, 404
  features/
    auth/              Đăng nhập (LoginPage), bảo vệ route, gọi API xác thực
  services/api.ts      Axios instance: gắn Bearer token, xử lý 401
  types/               Kiểu dữ liệu dùng chung (ApiResponse, PageResponse, Auth...)
  utils/               Hàm tiện ích (định dạng số/tiền, thao tác localStorage)
```

Khi phát triển một chức năng mới: tạo thư mục trong `src/features/<ten-chuc-nang>/`, thay `PlaceholderPage`
tương ứng trong `src/app/router.tsx` bằng trang thật và xóa trường `sprint` ở `src/app/navigation.tsx`.

## Chạy bằng Docker

```bash
docker build -t wms-web:dev .
docker run --rm -p 8088:80 wms-web:dev
```

Image gồm hai giai đoạn: `node:20-alpine` để build, `nginx:alpine` để phục vụ file tĩnh. Nginx có cấu hình
fallback cho SPA và chuyển tiếp `/api/` tới `http://api-gateway:8080/api/`, vì vậy cần chạy cùng mạng với
api-gateway (dùng `docker compose up -d` ở thư mục gốc của repository).

## Quy ước

- Định danh trong code bằng tiếng Anh, chữ hiển thị trên giao diện bằng tiếng Việt có dấu.
- Gọi API thông qua `services/api.ts`, không dùng `fetch` hoặc URL cứng trong component.
- Ưu tiên component của Ant Design; dùng Tailwind cho bố cục và khoảng cách.
- Chạy `npm run lint` và `npm run build` trước khi mở Pull Request (CI cũng chạy hai lệnh này).
