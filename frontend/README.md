# WMS Supply Chain - Frontend

Giao diện web của hệ thống quản lý kho và chuỗi cung ứng WMS Supply Chain.

## Công nghệ

| Thành phần | Phiên bản / thư viện |
| --- | --- |
| Framework | React 18, TypeScript, Vite |
| Giao diện | Ant Design 5, Tailwind CSS 4 |
| Biểu đồ | Recharts |
| Gọi API | Axios |
| Thời gian thực | SockJS + STOMP (`sockjs-client`, `@stomp/stompjs`) |
| Quản lý state | Zustand |
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
| `VITE_WS_URL` | `/ws-wms` | Endpoint SockJS/STOMP nhận thông báo thời gian thực. Giữ mặc định để dùng proxy như trên. |

## Cấu trúc thư mục

```text
src/
  app/                 Khung ứng dụng: App, router, MainLayout (sidebar), danh sách menu
  pages/               Các trang: Dashboard, trang giữ chỗ, 404
  features/
    auth/              Đăng nhập (LoginPage), bảo vệ route, gọi API xác thực
    realtime/          Thông báo thời gian thực: store Zustand, hook useWebSocket, chuông thông báo
  services/api.ts      Axios instance: gắn Bearer token, xử lý 401
  types/               Kiểu dữ liệu dùng chung (ApiResponse, PageResponse, Auth...)
  utils/               Hàm tiện ích (định dạng số/tiền, thao tác localStorage)
```

Khi phát triển một chức năng mới: tạo thư mục trong `src/features/<ten-chuc-nang>/`, thay `PlaceholderPage`
tương ứng trong `src/app/router.tsx` bằng trang thật và xóa trường `sprint` ở `src/app/navigation.tsx`.

## Thông báo thời gian thực

Sau khi đăng nhập, `MainLayout` gọi hook `useWebSocket` để nối SockJS/STOMP tới `/ws-wms` (Vite proxy ở dev,
Nginx ở Docker, cả hai chuyển tới API Gateway). Frame CONNECT gửi header `Authorization: Bearer <accessToken>`,
token được đọc lại trước mỗi lần nối nên dùng được token vừa làm mới. Client đăng ký các topic:

| Topic | Loại tin | Xử lý trên giao diện |
| --- | --- | --- |
| `/topic/alerts` | `LOW_STOCK` | Toast cảnh báo (hiện 10 giây) và thêm vào chuông thông báo |
| `/topic/orders` | `ORDER_CREATED`, `ORDER_DISPATCHED` | Toast và thêm vào chuông thông báo |
| `/topic/inventory` | `INVENTORY_UPDATED` | Chỉ cập nhật store (`lastInventoryUpdate`) |
| `/topic/kpi` | `KPI_UPDATED` | Chỉ cập nhật store (`kpi`); lúc vừa nối lấy số hiện tại qua `GET /api/notifications/realtime/kpi` |

```text
src/features/realtime/
  types.ts                 Kiểu phong bì tin (union theo `type`), payload, trạng thái kết nối
  realtimeMessage.ts       Danh sách topic, đọc và kiểm tra JSON (tin hỏng bị bỏ qua)
  realtimeConnection.ts    Một kết nối STOMP dùng chung: nối lại tăng dần 5-60 giây, heartbeat 10 giây,
                           dừng hẳn khi máy chủ từ chối token (frame ERROR) thay vì nối lại vô hạn
  realtimeStore.ts         Store Zustand: trạng thái kết nối, 50 thông báo gần nhất, số chưa đọc, KPI
  useWebSocket.ts          Hook gắn ở MainLayout: giữ kết nối và hiện toast bằng notification của Ant Design
  components/NotificationBell.tsx   Chuông trên Header: số chưa đọc, danh sách, chấm trạng thái kết nối
```

Cách thử: chạy backend (api-gateway và notification-service) cùng `npm run dev`, đăng nhập rồi tạo tình huống
tồn kho dưới ngưỡng; toast "Cảnh báo tồn kho thấp" hiện ở góc phải và chuông tăng số chưa đọc. Rê chuột vào chấm
cạnh chuông để xem trạng thái kết nối (xanh: đang nhận, vàng: đang nối lại, đỏ: đã ngắt, có nút "Kết nối lại").

## Chạy bằng Docker

```bash
docker build -t wms-web:dev .
docker run --rm -p 8088:80 wms-web:dev
```

Image gồm hai giai đoạn: `node:20-alpine` để build, `nginx:alpine` để phục vụ file tĩnh. Nginx có cấu hình
fallback cho SPA, chuyển tiếp `/api/` tới `http://api-gateway:8080/api/` và `/ws-wms` (WebSocket) tới
`http://api-gateway:8080/ws-wms`, vì vậy cần chạy cùng mạng với
api-gateway (dùng `docker compose up -d` ở thư mục gốc của repository).

## Quy ước

- Định danh trong code bằng tiếng Anh, chữ hiển thị trên giao diện bằng tiếng Việt có dấu.
- Gọi API thông qua `services/api.ts`, không dùng `fetch` hoặc URL cứng trong component.
- Ưu tiên component của Ant Design; dùng Tailwind cho bố cục và khoảng cách.
- Chạy `npm run lint` và `npm run build` trước khi mở Pull Request (CI cũng chạy hai lệnh này).
