# Tách dữ liệu theo doanh nghiệp (multi-tenant)

Tài liệu kỹ thuật của WMS-22.1: cơ chế trong module `common` giúp mỗi doanh nghiệp chỉ đọc và ghi được dữ liệu của mình, và các bước áp dụng cơ chế đó vào một service. Phần nghiệp vụ (vì sao cần, quy tắc chung, phân công từng task) nằm ở [SAAS_SPEC.md](SAAS_SPEC.md) và [SAAS_PLAN.md](SAAS_PLAN.md).

## 1. Tổng quan

Mọi bảng nghiệp vụ có cột `company_id`. ID doanh nghiệp không bao giờ lấy từ dữ liệu client gửi lên mà đi theo một đường duy nhất:

```
JWT (claim companyId)
   |
api-gateway --- gắn header X-Company-Id ---> service
                                               |
                                 TenantFilter đọc header
                                               |
                                 TenantContext (theo từng luồng xử lý)
                                   |                |                    |
                 Hibernate @TenantId        WmsEventPublisher      OpenFeign
                 tự gán khi thêm,           gắn header             chuyển tiếp
                 tự lọc khi truy vấn        x-wms-company-id       X-Company-Id
                                                    |                    |
                                         service nhận sự kiện      service được gọi
                                         đặt lại TenantContext     lọc đúng doanh nghiệp
```

Service không phải viết điều kiện `company_id = ?` trong từng câu truy vấn. Chỉ cần cho entity kế thừa `TenantEntity`.

## 2. Thành phần trong module common

Tất cả nằm trong gói `vn.wms.common.tenant`, được `WmsTenantAutoConfiguration` tự đăng ký cho mọi service dùng `common`.

| Lớp | Vai trò |
| --- | --- |
| `TenantContext` | Giữ ID doanh nghiệp của luồng đang xử lý. `getCompanyId()`, `requireCompanyId()` (thiếu thì ném `COMPANY_REQUIRED`), `runAs(companyId, ...)`, `callAs(companyId, ...)` cho tác vụ nền và test |
| `TenantFilter` | Bộ lọc HTTP: đọc header `X-Company-Id` vào `TenantContext`, xóa khi xử lý xong. Khi bật `app.tenant.required`, request thiếu doanh nghiệp nhận `403 COMPANY_REQUIRED` |
| `TenantEntity` | Lớp cha của entity nghiệp vụ (kế thừa `AuditableEntity`), có cột `company_id` đánh dấu `@TenantId` |
| `TenantIdentifierResolver` | Cho Hibernate biết doanh nghiệp hiện tại mỗi khi mở phiên làm việc |
| `TenantMessagePostProcessor` | Chạy trước mỗi phương thức `@RabbitListener`: đặt `TenantContext` theo header `x-wms-company-id` của message |
| `TenantFeignRequestInterceptor` | Gắn `X-Company-Id` và các header `X-User-*` vào lời gọi OpenFeign sang service khác |
| `TenantProperties` | Cấu hình `app.tenant.*` |

`WmsEventPublisher` tự gắn header `x-wms-company-id` vào mọi sự kiện phát ra khi luồng đang có doanh nghiệp. `ErrorCode` có thêm sáu mã của mô hình SaaS: `COMPANY_REQUIRED`, `COMPANY_INACTIVE`, `SUBSCRIPTION_EXPIRED`, `PLAN_LIMIT_EXCEEDED`, `PAYMENT_INVALID`, `TOKEN_INVALID` (bảng đầy đủ ở [CONVENTIONS.md](CONVENTIONS.md), mục 4).

### 2.1. API Gateway (WMS-22.3)

`JwtAuthenticationGlobalFilter` của api-gateway là nơi duy nhất quyết định request thuộc doanh nghiệp nào:

- Luôn gỡ các header `X-Company-Id`, `X-Plan`, `X-User-*` do client tự gửi, kể cả với đường dẫn công khai và khi tắt kiểm tra JWT.
- Token hợp lệ: gắn `X-Company-Id` bằng claim `companyId`. Client gửi kèm `X-Company-Id` khác cũng không có tác dụng.
- Token hợp lệ nhưng không có `companyId` (token cấp trước khi auth-service có khái niệm doanh nghiệp): trả `401` để giao diện đưa người dùng về trang đăng nhập. Công tắc `gateway.jwt.require-company` (biến `GATEWAY_COMPANY_REQUIRED`, mặc định `true`) chỉ tắt tạm khi auth-service chưa cấp claim này.
- Đường dẫn công khai (`gateway.jwt.public-paths`) đã có sẵn các API không cần đăng nhập của phần SaaS: đăng ký và kích hoạt doanh nghiệp, quên và đặt lại mật khẩu, danh sách gói, webhook PayOS.

Thứ tự triển khai lên máy chủ: auth-service cấp `companyId` trong token (WMS-22.2) và gateway bản này phải lên cùng lúc với service đầu tiên bật `app.tenant.required`. Người dùng đang đăng nhập sẽ phải đăng nhập lại một lần.

### 2.2. notification-service (WMS-22.4c)

| Phần | Cách tách theo doanh nghiệp |
| --- | --- |
| Bảng `notification_logs` | Bảng duy nhất của service, kế thừa `TenantEntity` (migration V2: dữ liệu cũ gán doanh nghiệp 1, `company_id` NOT NULL, index `(company_id, status)`, `(company_id, created_at)`). `app.tenant.required: true` |
| Listener RabbitMQ | Ba listener đọc doanh nghiệp từ header `x-wms-company-id`, không có thì trường `companyId` của JSON, rồi xử lý trong `TenantContext.runAs`. Sự kiện không có doanh nghiệp: một dòng log cảnh báo rồi bỏ qua (không ném lỗi, không thử lại, không gửi email) |
| KPI thời gian thực trên Redis | `wms:notification:kpi:{companyId}:{yyyy-MM-dd}` và khóa chống đếm trùng `wms:notification:kpi:{companyId}:processed:{messageId}` |
| Cache KPI dashboard (WMS-16.1) | `wms:kpi:company:{companyId}:summary:{warehouseId hoặc all}:{dateRange}`; lời gọi sang order-service, inventory-service chuyển tiếp `X-Company-Id` |
| WebSocket `/ws-wms` | Kênh `/topic/company/{companyId}/alerts`, `/orders`, `/inventory`, `/kpi`. Doanh nghiệp của phiên lấy từ claim `companyId` của JWT đã kiểm tra chữ ký ở frame CONNECT; SUBSCRIBE kênh của doanh nghiệp khác bị từ chối. `/ws-wms/**` nằm trong `app.tenant.public-paths` vì handshake không mang `X-Company-Id` |
| Email cảnh báo | Gửi và ghi nhật ký trong ngữ cảnh doanh nghiệp của sự kiện; người nhận do service phát chọn trong doanh nghiệp của nó, notification-service không gọi auth-service tra người nhận. Cảnh báo tồn kho (WMS-15.2): inventory-service tra người nhận qua `GET /internal/auth/users/alert-recipients` của auth-service, lọc tường minh theo `X-Company-Id` được OpenFeign chuyển tiếp; khóa chống gửi trùng `(company_id, dedup_key)` trong `notification_logs` (V3) |

`/api/notifications/health-info` cũng nằm trong `public-paths` vì chỉ trả cấu hình RabbitMQ và SMTP, không có dữ liệu của doanh nghiệp nào. Chi tiết hợp đồng tin nhắn ở [MESSAGING.md](MESSAGING.md) mục 8.

## 3. Hành vi cần nắm trước khi dùng

1. **Thêm mới**: `company_id` được gán bằng doanh nghiệp trong `TenantContext`. Entity không có setter cho `companyId`. Lưu khi chưa xác định doanh nghiệp bị từ chối với `COMPANY_REQUIRED`.
2. **Truy vấn**: mọi truy vấn JPQL, Criteria, phương thức đặt tên của repository (`findByCode`, `existsByCode`, `count`, `findAll`, phân trang) và cả `UPDATE`, `DELETE` hàng loạt viết bằng JPQL đều tự có điều kiện `company_id`. Kiểm tra trùng mã bằng `existsByCode` vì vậy tự động chỉ xét trong doanh nghiệp hiện tại.
3. **Tìm theo ID**: Hibernate không thêm điều kiện doanh nghiệp khi tải theo khóa chính. `TenantEntity` chặn lại: `findById`, `getReferenceById`, `deleteById`, `save` một đối tượng mang ID của doanh nghiệp khác đều ném `ApiException` mã `NOT_FOUND`, API trả 404. Lưu ý là ném lỗi chứ không trả `Optional.empty()`. Cần kiểm tra tồn tại mà không muốn lỗi thì dùng `existsById` hoặc `findAllById`.
4. **SQL gốc**: câu `@Query(nativeQuery = true)` và `JdbcTemplate` không được lọc. Phải tự thêm `WHERE company_id = :companyId` với giá trị lấy từ `TenantContext.requireCompanyId()`.
5. **Chưa xác định doanh nghiệp**: truy vấn không trả dòng nào (đóng theo mặc định), không phải trả tất cả.
6. **Thời điểm đặt ngữ cảnh**: Hibernate đọc doanh nghiệp lúc mở phiên làm việc. Phải đặt `TenantContext` trước khi transaction bắt đầu. Gọi `TenantContext.runAs` bên trong một phương thức `@Transactional` đang chạy không đổi được doanh nghiệp của transaction đó.
7. **Luồng khác**: `TenantContext` không tự truyền sang luồng mới (`@Async`, `CompletableFuture`, `@Scheduled`). Lấy `companyId` ở luồng gốc rồi bọc phần việc trong `TenantContext.runAs(companyId, ...)`.

## 4. Các bước chuyển một service

Làm lần lượt, mỗi service một Pull Request. inventory-service là mẫu (WMS-22.3, WMS-22.4a).

Tình trạng chuyển đổi:

| Service | Trạng thái | Migration | Bảng |
| --- | --- | --- | --- |
| inventory-service | Đã chuyển (WMS-22.4a) | `V6` | `warehouses`, `bin_locations`, `stock_items`, `stock_reservations`, `incoming_stocks`, `inventory_lots`; các bảng tạo sau có `company_id` ngay từ đầu |
| product-service | Đã chuyển (WMS-22.4b) | `V4` | `categories`, `uoms`, `products`, `unit_conversions`. Đơn vị tính mẫu do `V3` thêm thuộc doanh nghiệp 1; doanh nghiệp mới bắt đầu với danh mục rỗng |
| order-service, notification-service | Chưa chuyển | | |

### Bước 1: migration

Xem số phiên bản Flyway mới nhất của service trên nhánh `develop` và báo trong nhóm số định dùng. Với mỗi bảng nghiệp vụ:

```sql
-- 1. Thêm cột cho phép null để dữ liệu cũ không lỗi
ALTER TABLE warehouses ADD COLUMN company_id BIGINT NULL;

-- 2. Dữ liệu đang có thuộc Công ty mẫu A (id = 1)
UPDATE warehouses SET company_id = 1 WHERE company_id IS NULL;

-- 3. Bắt buộc từ đây về sau
ALTER TABLE warehouses MODIFY company_id BIGINT NOT NULL;

-- 4. Mã duy nhất trong một doanh nghiệp thay cho duy nhất toàn hệ thống
ALTER TABLE warehouses DROP INDEX uk_warehouses_code;
ALTER TABLE warehouses ADD CONSTRAINT uk_warehouses_company_code UNIQUE (company_id, code);

-- 5. Index cho các cột hay lọc nếu ràng buộc ở bước 4 chưa bao phủ
CREATE INDEX idx_warehouses_company ON warehouses (company_id);
```

Bảng con (dòng chi tiết đơn hàng, giao dịch công nợ) cũng thêm `company_id`, lấy giá trị từ bảng cha khi cập nhật dữ liệu cũ. Không tạo khóa ngoại tới bảng `companies` vì bảng đó nằm ở cơ sở dữ liệu của auth-service.

### Bước 2: entity

Đổi lớp cha từ `AuditableEntity` sang `TenantEntity`, sửa ràng buộc duy nhất trong annotation cho khớp migration:

```java
@Entity
@Table(name = "warehouses", uniqueConstraints = @UniqueConstraint(
        name = "uk_warehouses_company_code", columnNames = {"company_id", "code"}))
public class Warehouse extends TenantEntity {
    // các trường giữ nguyên, bỏ unique = true ở cột code
}
```

### Bước 3: rà lại repository và service

- Tìm các câu `nativeQuery = true` và thêm điều kiện `company_id`.
- Chỗ nào dùng `findById(id).isPresent()` hoặc `ifPresent` để kiểm tra tồn tại thì đổi sang `existsById`.
- Bộ sinh số chứng từ (PO, SO) đếm theo doanh nghiệp: nếu đang đếm bằng JPQL thì đã tự đúng, nếu đếm bằng SQL gốc thì thêm điều kiện. Bộ đếm giữ trong bộ nhớ (map theo ngày) dùng chung cho mọi doanh nghiệp là sai: doanh nghiệp sau không bao giờ nhận số `0001`; xem cách order-service dùng bảng `document_sequences` ở cuối mục này.
- Tác vụ `@Scheduled` chạy cho mọi doanh nghiệp: lấy danh sách `company_id` bằng một câu SQL gốc `SELECT DISTINCT company_id ...`, rồi với từng giá trị gọi `TenantContext.runAs(companyId, () -> service.xuLy())`.

### Bước 4: bật kiểm tra bắt buộc

Trong `application.yml` của service:

```yaml
app:
  tenant:
    required: true
    default-company-id: ${TENANT_DEFAULT_COMPANY_ID:}
```

Trong `docker-compose.yml` (môi trường phát triển) thêm `TENANT_DEFAULT_COMPANY_ID: ${TENANT_DEFAULT_COMPANY_ID:-1}` cho service đó, để khi gateway tắt kiểm tra JWT hoặc gọi thẳng vào service vẫn có doanh nghiệp 1. `deploy/docker-compose.prod.yml` là lớp phủ lên file gốc nên phải đặt lại `TENANT_DEFAULT_COMPANY_ID: ""` cho chính service đó: trên máy chủ, request không có doanh nghiệp phải bị từ chối chứ không được rơi vào doanh nghiệp 1.

Các service nghiệp vụ (inventory-service, product-service, order-service, notification-service) đều đã chuyển đổi và bật `required`. Ở máy phát triển chúng nhận doanh nghiệp mặc định 1 nên sự kiện phát ra và lời gọi OpenFeign giữa các service vẫn mang doanh nghiệp, kể cả khi chạy không qua gateway. Chạy service bằng IDE thì đặt biến môi trường `TENANT_DEFAULT_COMPANY_ID=1`.

Service có API không cần doanh nghiệp (ví dụ webhook) khai báo thêm `app.tenant.public-paths`.

### Bước 5: sự kiện và gọi service khác

Không phải làm gì nếu service phát sự kiện bằng `WmsEventPublisher`, nhận bằng `@RabbitListener` với listener container mặc định và gọi service khác bằng OpenFeign: doanh nghiệp tự đi theo. Hai trường hợp phải tự xử lý:

- Service tự khai báo bean `ContainerCustomizer` hoặc listener container factory riêng: gọi thêm `container.addAfterReceivePostProcessors(tenantMessagePostProcessor)`.
- Listener nhận message không có header doanh nghiệp (message cũ còn trong queue lúc nâng cấp): `TenantContext` rỗng, việc lưu sẽ bị từ chối và message vào dead-letter sau số lần thử lại. Listener nên kiểm tra `TenantContext.isSet()` ở đầu, thiếu thì ghi log cảnh báo và bỏ qua.

### Bước 6: test cách ly

Mỗi service có ít nhất một lớp test theo mẫu ở mục 5 và phải xanh trên CI trước khi merge.

### order-service đã chuyển đổi (WMS-22.4b)

- Migration `V9__multi_tenant_company_id.sql` thêm `company_id` cho toàn bộ 14 bảng nghiệp vụ: `suppliers`, `customers`, `supplier_transactions`, `customer_transactions`, `purchase_orders`, `po_items`, `sales_orders`, `so_items`, `goods_receipt_notes`, `goods_receipt_note_items`, `purchase_bills`, `purchase_bill_items`, `goods_delivery_notes`, `goods_delivery_note_items`. Dữ liệu cũ thuộc doanh nghiệp 1, bảng con lấy theo bảng cha. Ràng buộc duy nhất: `(company_id, code)` cho nhà cung cấp và khách hàng, `(company_id, po_number)`, `(company_id, so_number)`, `(company_id, grn_number)`, `(company_id, bill_number)`, `(company_id, gdn_number)`. Mã số thuế tra theo index `(company_id, tax_code)`. `uk_purchase_bills_grn (grn_id)` giữ nguyên vì `grn_id` là khóa chính toàn hệ thống.
- Số chứng từ (PO, SO, GRN, GDN, PB) cấp từ bảng `document_sequences` (một dòng cho mỗi doanh nghiệp và mỗi tiền tố như `PO-20261110-`), qua `DocumentNumberService`: transaction riêng có khóa dòng, commit ngay, nên hai yêu cầu đồng thời không nhận trùng số, hai doanh nghiệp đều bắt đầu từ `...-0001`. Lần đầu gặp một tiền tố, bộ đếm khởi tạo từ số lớn nhất đã có của doanh nghiệp. Bộ đếm trong bộ nhớ dùng chung cho mọi doanh nghiệp trước đây đã bỏ.
- order-service không có SQL gốc, không có `@RabbitListener`, không có `@Scheduled`. Sự kiện `order.created`, `order.received`, `order.dispatched` phát qua `WmsEventPublisher` nên tự mang `x-wms-company-id`; sự kiện duyệt PO phát sau commit ghi sẵn `companyId` trong sự kiện Spring nội bộ rồi phát dưới `TenantContext.callAs`. Lời gọi OpenFeign sang inventory-service và product-service đi qua `TenantFeignRequestInterceptor` (đăng ký tự động cho mọi Feign client).
- Endpoint nội bộ `/internal/orders/kpi-metrics` chỉ dùng JPQL nên chỉ tính số liệu của doanh nghiệp trong header `X-Company-Id`.
- Test: `OrderTenantIsolationTest`, `PartnerBulkImportIntegrationTest`. Test `@Transactional` có sẵn nhận doanh nghiệp 1 từ `TenantTestExecutionListener` (đăng ký trong `src/test/resources/META-INF/spring.factories`), chạy trước khi Spring mở transaction của test; test tự tạo luồng mới thì bọc phần việc trong `TenantContext.runAs`.

## 5. Mẫu test cách ly

Lớp test không đặt `@Transactional`, để mỗi lời gọi mở một phiên làm việc riêng như request thật. Bản đầy đủ: `backend/common/src/test/java/vn/wms/common/tenant/TenantIsolationTest.java`.

```java
@SpringBootTest
@AutoConfigureMockMvc
class WarehouseIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired WarehouseRepository repository;
    @Autowired MockMvc mockMvc;

    Long warehouseOfB;

    @BeforeEach
    void setUp() {
        TenantContext.runAs(COMPANY_A, () -> repository.save(new Warehouse("WH01", "Kho của A")));
        warehouseOfB = TenantContext.callAs(COMPANY_B,
                () -> repository.save(new Warehouse("WH01", "Kho của B")).getId());
    }

    @Test
    void companyACannotSeeOrTouchWarehouseOfB() throws Exception {
        // Danh sách chỉ có kho của A
        mockMvc.perform(get("/api/inventory/warehouses").header("X-Company-Id", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        // Xem, sửa, xóa theo ID của B đều là 404
        mockMvc.perform(get("/api/inventory/warehouses/" + warehouseOfB).header("X-Company-Id", "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/inventory/warehouses/" + warehouseOfB).header("X-Company-Id", "1"))
                .andExpect(status().isNotFound());
        // Dữ liệu của B còn nguyên
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.existsById(warehouseOfB))).isTrue();
    }
}
```

Test đang có của service sau khi chuyển đổi cần đặt doanh nghiệp: thêm header `X-Company-Id` vào request của MockMvc, hoặc đặt `app.tenant.default-company-id=1` trong `application-test.yml`, hoặc gọi `TenantContext.setCompanyId(1L)` trong `@BeforeEach` và `TenantContext.clear()` trong `@AfterEach` với test gọi thẳng service.

Lớp test đặt `@Transactional` mở transaction (và phiên Hibernate) trước `@BeforeEach`, nên đặt doanh nghiệp trong `@BeforeEach` là quá muộn: phiên đã mở dưới "không doanh nghiệp". product-service dùng một `TestExecutionListener` có thứ tự nhỏ hơn `TransactionalTestExecutionListener` để đặt doanh nghiệp trước khi transaction mở (`DefaultCompanyTestExecutionListener`, khai báo bằng `@TestExecutionListeners(listeners = ..., mergeMode = MERGE_WITH_DEFAULTS)`).

## 6. Cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `app.tenant.required` | `false` | `true`: API nghiệp vụ không có doanh nghiệp trả `403 COMPANY_REQUIRED`. Bật khi service đã chuyển xong |
| `app.tenant.default-company-id` | rỗng | Doanh nghiệp dùng khi request không có `X-Company-Id`. Chỉ đặt ở môi trường phát triển |
| `app.tenant.public-paths` | rỗng | Đường dẫn bổ sung không cần doanh nghiệp (mẫu Ant). Luôn được bỏ qua: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error` và request `OPTIONS` |

Mặc định `required = false` nên việc thêm cơ chế này vào `common` không làm đổi hành vi của service nào chưa chuyển đổi.

## 7. auth-service là ngoại lệ

Lúc đăng nhập hệ thống chưa biết người dùng thuộc doanh nghiệp nào, nên các bảng `users`, `companies`, `subscriptions`, `payments`, `api_keys` của auth-service không dùng `TenantEntity`. auth-service lọc tường minh bằng `companyId` của người đang đăng nhập (xem WMS-22.2 trong [SAAS_SPEC.md](SAAS_SPEC.md)).

## 8. Lỗi thường gặp

| Hiện tượng | Nguyên nhân và cách xử lý |
| --- | --- |
| Test hoặc API trả danh sách rỗng dù cơ sở dữ liệu có dữ liệu | Chưa có doanh nghiệp trong ngữ cảnh. Thêm header `X-Company-Id` hoặc đặt `TenantContext` |
| Lưu bị lỗi `COMPANY_REQUIRED` | Như trên, xảy ra ở tác vụ nền, listener nhận message thiếu header, hoặc test gọi thẳng repository |
| `findById` ném `NOT_FOUND` thay vì trả rỗng | Bản ghi thuộc doanh nghiệp khác. Đây là hành vi chủ đích, xem mục 3 |
| Đổi doanh nghiệp bằng `runAs` nhưng vẫn ra dữ liệu doanh nghiệp cũ | Đang gọi bên trong một transaction đã mở. Đưa `runAs` ra ngoài phương thức `@Transactional` |
| Hai doanh nghiệp không tạo được cùng một mã | Ràng buộc duy nhất cũ chưa đổi thành `(company_id, code)` trong migration |
| Số liệu báo cáo gộp cả doanh nghiệp khác | Câu SQL gốc chưa có điều kiện `company_id` |
| Sau khi nâng cấp, mọi API trả 401 dù đã đăng nhập | Token cũ không có claim `companyId`. Đăng nhập lại để nhận token mới |
| Listener không xử lý sự kiện, message nằm ở `wms.dead-letter` | Bên phát không có doanh nghiệp trong ngữ cảnh nên sự kiện thiếu header `x-wms-company-id`. Kiểm tra service phát có nhận `X-Company-Id` hoặc có `TENANT_DEFAULT_COMPANY_ID` ở máy phát triển |
