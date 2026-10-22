# Quy ước kỹ thuật

Áp dụng cho toàn bộ backend và frontend. Quy ước Git, commit và Pull Request nằm ở [CONTRIBUTING.md](../CONTRIBUTING.md).

## 1. Nguyên tắc chung

- Định danh trong code (tên lớp, biến, bảng, cột, API, mã lỗi) viết bằng tiếng Anh.
- Chữ hiển thị trên giao diện, thông báo lỗi trả về cho người dùng và tài liệu viết bằng tiếng Việt có dấu.
- File mã nguồn dùng UTF-8, kết thúc dòng LF, thụt lề bằng dấu cách (Java 4, TypeScript 2).
- Không commit bí mật (mật khẩu, token, khóa JWT, file `.env`).

## 2. Quy ước đặt tên API

- Mọi API có tiền tố `/api` và đi qua API Gateway; không có số phiên bản trong đường dẫn ở giai đoạn này.
- Mỗi service sở hữu một tiền tố gốc. API Gateway định tuyến theo tiền tố đó và giữ nguyên đường dẫn khi chuyển tiếp, nên
  controller của service khai báo đường dẫn đầy đủ, ví dụ `@RequestMapping("/api/inventory/warehouses")`.
- Dưới tiền tố của service, tên tài nguyên là danh từ số nhiều, chữ thường, ngăn cách bằng dấu gạch ngang:
  `/api/inventory/warehouses`, `/api/orders/purchase-orders`.
- Tài nguyên con thể hiện quan hệ sở hữu: `/api/inventory/warehouses/{warehouseId}/locations`.
- Hành động không thuộc CRUD dùng động từ ở cuối đường dẫn bằng phương thức `POST`:
  `POST /api/orders/purchase-orders/{id}/approve`, `POST /api/orders/goods-receipts/{id}/confirm`.
- Tham số truy vấn và trường JSON dùng `camelCase`; giá trị enum dùng `UPPER_SNAKE_CASE`.
- Mỗi service công bố tài liệu OpenAPI (springdoc) và là hợp đồng API giữa các service; TechLead thống nhất và review thay đổi.
- Phân trang, sắp xếp, tìm kiếm: `?page=0&size=20&sort=createdAt,desc&keyword=gao`. `page` bắt đầu từ 0, `size` tối đa 100.

Tiền tố và service tương ứng (khai báo trong `backend/api-gateway`):

| Tiền tố | Service | Nội dung |
| --- | --- | --- |
| `/api/auth/**` | auth-service | Đăng nhập, người dùng, vai trò |
| `/api/products/**` | product-service | Hàng hóa, SKU, đơn vị tính, barcode |
| `/api/inventory/**` | inventory-service | Kho, vị trí, tồn kho, điều chuyển, kiểm kê |
| `/api/orders/**` | order-service | Nhà cung cấp, khách hàng, PO, GRN, SO, GDN, hóa đơn |
| `/api/notifications/**` | notification-service | Thông báo, báo cáo, Dashboard |

Thêm tiền tố mới thì phải khai báo route tương ứng ở api-gateway trong cùng Pull Request.

### Phương thức HTTP và mã trạng thái

| Thao tác | Phương thức | Thành công | Lỗi thường gặp |
| --- | --- | --- | --- |
| Lấy danh sách, lấy chi tiết | GET | 200 | 404 khi không tìm thấy |
| Tạo mới | POST | 201 (kèm `Location`) | 400 dữ liệu sai, 409 trùng dữ liệu |
| Cập nhật toàn bộ | PUT | 200 | 400, 404, 409 |
| Cập nhật một phần | PATCH | 200 | 400, 404, 409 |
| Xóa | DELETE | 204 | 404, 409 khi đang được tham chiếu |

Mã chung: 401 chưa đăng nhập hoặc token hết hạn, 403 không đủ quyền, 500 lỗi hệ thống, 503 service phụ thuộc không khả dụng.

## 3. Định dạng response

Mọi response đều là JSON theo cấu trúc thống nhất, được hiện thực trong module `common` của backend (`ApiResponse`, `PageResponse`,
`GlobalExceptionHandler`); service chỉ cần thêm `implementation project(':common')`. Trường `data` bị bỏ khỏi JSON khi không có dữ liệu.
Kiểu dữ liệu tương ứng ở frontend nằm trong `frontend/src/types/api.ts`.

Thành công:

```json
{
  "success": true,
  "code": "OK",
  "message": "Thành công",
  "data": { "id": 12, "sku": "GAO-ST25-5KG", "name": "Gạo thơm túi 5kg" },
  "timestamp": "2026-10-23T08:30:00Z"
}
```

Danh sách có phân trang (`data` là đối tượng phân trang):

```json
{
  "success": true,
  "code": "OK",
  "message": "Thành công",
  "data": {
    "content": [ { "id": 12, "sku": "GAO-ST25-5KG" } ],
    "page": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7,
    "first": true,
    "last": false
  },
  "timestamp": "2026-10-23T08:30:00Z"
}
```

Lỗi (với lỗi validate, `data` chứa thông báo theo từng trường):

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "data": { "quantity": "Số lượng phải lớn hơn 0" },
  "timestamp": "2026-10-23T08:30:00Z"
}
```

Đăng nhập `POST /api/auth/login` nhận `{ "username": "...", "password": "..." }` và trả về trong `data`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "admin",
  "roles": ["ADMIN"]
}
```

Các request sau gửi kèm header `Authorization: Bearer <accessToken>`.

## 4. Mã lỗi

Mã lỗi là tên của enum `ErrorCode` trong module `common` của backend, viết hoa và ngăn cách bằng dấu gạch dưới. Mỗi mã gắn với
một HTTP status và thông báo mặc định bằng tiếng Việt. Frontend xử lý theo `code`, không phụ thuộc nội dung `message`.

| Mã lỗi | HTTP | Ý nghĩa |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | Yêu cầu không hợp lệ (sai định dạng, thiếu tham số) |
| `VALIDATION_ERROR` | 400 | Dữ liệu không qua kiểm tra, chi tiết từng trường nằm trong `data` |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập hoặc token không hợp lệ, hết hạn |
| `FORBIDDEN` | 403 | Không đủ quyền thực hiện thao tác |
| `NOT_FOUND` | 404 | Không tìm thấy dữ liệu |
| `METHOD_NOT_ALLOWED` | 405 | Phương thức HTTP không được hỗ trợ |
| `CONFLICT` | 409 | Dữ liệu bị xung đột hoặc đã tồn tại (ví dụ trùng mã SKU) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Định dạng nội dung không được hỗ trợ |
| `INTERNAL_ERROR` | 500 | Lỗi hệ thống không xác định |

Quy tắc:

- Lỗi nghiệp vụ ném `ApiException` (ví dụ `ApiException.conflict("Mã SKU đã tồn tại")`); `GlobalExceptionHandler` chuyển thành response chuẩn.
- `message` viết bằng tiếng Việt có dấu, không lộ chi tiết nội bộ (stack trace, câu lệnh SQL).
- Khi cần mã nghiệp vụ riêng mà frontend phải phân biệt (ví dụ tồn kho không đủ), thêm giá trị mới vào enum `ErrorCode` kèm HTTP status
  và thông báo mặc định, đồng thời cập nhật bảng này trong cùng Pull Request.

## 5. Quy ước dữ liệu

- Thời gian: lưu và truyền theo UTC, định dạng ISO-8601 (`2026-10-23T08:30:00Z`); giao diện đổi sang múi giờ `Asia/Ho_Chi_Minh`.
- Tiền tệ: VNĐ, dùng `BigDecimal` ở Java và `DECIMAL(18,2)` ở MySQL, không dùng số thực dấu phẩy động.
- Số lượng: `DECIMAL(18,3)` để hỗ trợ hàng bán theo cân. Tồn kho luôn lưu theo đơn vị cơ sở; việc quy đổi đơn vị tính
  (ví dụ 1 thùng = 24 chai) thực hiện ở tầng nghiệp vụ.
- Mã chứng từ: `<tiền tố>-<yyyyMMdd>-<số thứ tự 4 chữ số>`, tiền tố `PO`, `GRN`, `SO`, `GDN`; ví dụ `PO-20261023-0001`.
- Mã SKU: chữ in hoa, số và dấu gạch ngang, duy nhất toàn hệ thống.

## 6. Cơ sở dữ liệu

- Tên bảng và cột dùng `snake_case`; tên bảng là danh từ số nhiều (`purchase_orders`, `stock_movements`).
- Khóa chính `id BIGINT AUTO_INCREMENT`; khóa ngoại đặt tên `<bảng_đơn>_id`.
- Mỗi bảng nghiệp vụ có các cột `created_at`, `updated_at`, `created_by`, `updated_by`.
- Chỉ truy cập schema của chính service; không join hay truy vấn chéo schema.
- Mọi thay đổi schema phải có script migration Flyway trong `src/main/resources/db/migration` (tên `V<số>__<mô_tả>.sql`),
  không sửa tay trên môi trường dùng chung. Hibernate chỉ kiểm tra (`ddl-auto: validate`), không tự sinh schema.
- Cập nhật tồn kho dùng khóa bi quan (`PESSIMISTIC_WRITE`) để không bị âm kho khi nhiều giao dịch xảy ra đồng thời,
  và không bao giờ sửa ngoài luồng chứng từ.

## 7. Chuẩn code Java (backend)

- Java 17, Spring Boot 3.3, Spring Cloud 2023.0; build bằng Gradle multi-project, `./gradlew build` phải xanh trước khi mở Pull Request.
- Gói gốc là `vn.wms.<module>`, ví dụ `vn.wms.common`. Lombok được cấu hình sẵn để giảm code lặp (không dùng `@Data` cho entity JPA).
- Phân lớp: `controller` (chỉ xử lý HTTP và validate đầu vào) -> `service` (nghiệp vụ, `@Transactional`) -> `repository`
  (Spring Data). Các gói còn lại: `entity`, `dto`, `mapper`, `config`, `exception`.
- Không trả entity ra ngoài API; dùng DTO riêng cho request và response, đặt tên hậu tố `Request` và `Response`.
- Đặt tên: lớp `PascalCase`, phương thức và biến `camelCase`, hằng số `UPPER_SNAKE_CASE`, gói chữ thường.
- Dùng constructor injection với trường `final`, không dùng `@Autowired` trên trường.
- Lỗi nghiệp vụ ném `ApiException`; `GlobalExceptionHandler` (module `common`) chuyển mọi ngoại lệ thành response chuẩn ở mục 3.
  Mọi service dùng chung `ApiResponse` và `PageResponse` của module `common`, không tự định nghĩa lại.
- Validate đầu vào bằng Jakarta Validation (`@Valid`, `@NotNull`, `@Positive`...).
- Hai lớp log: audit log nghiệp vụ (ai làm gì trên chứng từ nào) và error log kỹ thuật. Log bằng SLF4J với tham số (`log.info("Confirmed GRN {}", grnNo)`), không dùng `System.out`, không log mật khẩu, token hay dữ liệu cá nhân.
- Cấu hình ngoài code (biến môi trường hoặc Config Server), không ghi cứng địa chỉ, mật khẩu, khóa.
- Định dạng: tối đa 120 ký tự mỗi dòng, không dùng import `*`, `Optional` chỉ dùng làm kiểu trả về.
- Test: JUnit 5 và Mockito, đặt trong `src/test`, tên lớp `XxxTest`, tên phương thức nêu rõ tình huống,
  ví dụ `confirmGrn_whenDuplicateEvent_doesNotIncreaseStockTwice`. Logic nghiệp vụ mới phải có test.

## 8. Chuẩn code TypeScript và React (frontend)

- TypeScript chế độ `strict`, không dùng `any`; dùng `import type` cho import chỉ chứa kiểu.
- `npm run lint` không còn lỗi và `npm run build` thành công trước khi mở Pull Request.
- Định dạng theo mẫu của Vite: thụt lề 2 dấu cách, dấu nháy đơn, không dùng dấu chấm phẩy.
- Chỉ dùng function component và hook; khai báo `interface` cho props.
- Đặt tên file: component `PascalCase.tsx`, hook `useXxx.ts`, service, tiện ích và kiểu dữ liệu `camelCase.ts`.
- Tổ chức theo chức năng: mỗi chức năng một thư mục trong `src/features/<ten-chuc-nang>/` (trang, component, hook, hàm gọi API);
  kiểu dùng chung ở `src/types/`, tiện ích ở `src/utils/`.
- Mọi lời gọi API đi qua `src/services/api.ts` (Axios instance gắn token và xử lý 401); không dùng `fetch` hay URL cứng.
  Địa chỉ API cấu hình bằng `VITE_API_URL`.
- Giao diện ưu tiên component Ant Design, dùng Tailwind cho bố cục và khoảng cách; không ghi cứng màu ngoài theme.
- Chữ hiển thị bằng tiếng Việt có dấu; số và tiền định dạng bằng `src/utils/format.ts`.
- Thông báo cho người dùng qua `App.useApp().message`; thông điệp lỗi lấy từ `message` của response lỗi chuẩn.
- Form validate bằng `rules` của Ant Design, thông điệp tiếng Việt.
