# Kế hoạch mở rộng WMS Supply Chain thành nền tảng SaaS cho nhiều doanh nghiệp

Phiên bản chốt ngày 03/11/2026 (Toàn duyệt), bổ sung vào Master Plan v2 thành v3. Nguyên tắc: hạn chót 23/11/2026 giữ nguyên, không làm thứ 7 và chủ nhật, mỗi ngày làm việc mỗi người có ít nhất một commit, chỉ giao việc mới từ ngày 04/11 trở đi (không sửa các việc đã qua), thanh toán qua PayOS, phần giao diện sẽ do Toàn rà soát và chỉnh lại ở tuần cuối theo góp ý của chính mình.

## 1. Mục tiêu và câu chuyện demo

Từ phần mềm nội bộ cho một doanh nghiệp, hệ thống trở thành nền tảng mà nhiều doanh nghiệp tự đăng ký và dùng chung, dữ liệu tách biệt hoàn toàn, có gói dùng thử 30 ngày và gói trả phí, có AI giúp doanh nghiệp đưa dữ liệu cũ vào hệ thống nhanh và có trợ lý hướng dẫn sử dụng.

Kịch bản demo với thầy (10 phút):

1. Vào trang giới thiệu, đăng ký "Công ty A", nhận 30 ngày dùng thử, đăng nhập bằng tài khoản admin vừa tạo. Dashboard trống.
2. Mở "Nhập dữ liệu bằng AI", tải file Excel danh mục hàng hóa cũ của công ty (hoặc ảnh báo giá PDF). AI trích xuất thành bảng, người dùng xem trước, sửa vài ô, bấm nhập. 50 SKU vào danh mục trong một phút.
3. Hỏi trợ lý AI "làm sao tạo kho và vị trí kệ", làm theo hướng dẫn, rồi lập đơn mua, nhận hàng, xem tồn kho cập nhật thời gian thực (phần lõi đã có).
4. Đăng nhập "Công ty B" ở tab khác: không thấy bất kỳ dữ liệu nào của Công ty A.
5. Đặt ngày hết hạn dùng thử về quá khứ bằng công cụ demo: hệ thống chuyển sang chỉ đọc, hiện màn hình gia hạn, chọn gói Pro, thanh toán qua PayOS (quét mã QR chuyển khoản), PayOS gọi webhook về máy chủ, gói được mở lại ngay.
6. Tạo API key trong Cài đặt, gọi API tồn kho bằng curl từ "hệ thống kế toán bên ngoài".

## 2. Phạm vi chốt

| Làm | Không làm (ghi rõ để không lan phạm vi) |
| --- | --- |
| Tách dữ liệu theo doanh nghiệp (company_id) ở cả 5 service | Cơ sở dữ liệu riêng từng công ty, tên miền riêng từng công ty |
| Đăng ký doanh nghiệp tự phục vụ (tạo công ty và admin đầu tiên, xác nhận email, quên mật khẩu), dùng thử 30 ngày, gói Free / Trial / Pro với giới hạn số user, số kho, số SKU, số lượt AI mỗi tháng; gia hạn bằng thanh toán PayOS thật (key do Toàn cung cấp, đặt trong .env) | Nhiều cổng thanh toán (VNPay, Momo, Stripe), trả góp, hoàn tiền |
| Bỏ gợi ý tài khoản mặc định trên trang đăng nhập và tài khoản demo trong dữ liệu mẫu; mỗi công ty tự tạo tài khoản khi đăng ký | Tài khoản dùng chung cho mọi công ty |
| Hết hạn thì chỉ đọc, banner "còn N ngày dùng thử", trang gói dịch vụ | Xuất hóa đơn, khuyến mãi, mã giảm giá |
| AI trích xuất tài liệu (Excel, CSV, PDF, ảnh) thành hàng hóa, nhà cung cấp, khách hàng; xem trước rồi nhập hàng loạt | AI tự động nhập không qua xem trước; AI sửa dữ liệu đang có |
| Trợ lý AI trong giao diện: hướng dẫn sử dụng (từ tài liệu hệ thống) và tra cứu tồn kho, đơn hàng của chính công ty | Trợ lý giọng nói, AI dự báo nhu cầu |
| API key theo doanh nghiệp để hệ thống khác gọi API, kèm Swagger công khai và hướng dẫn tích hợp | SDK, webhook ra ngoài |
| Trang giới thiệu (landing) và bảng giá | Blog, SEO, đa ngôn ngữ |

## 3. Thiết kế kỹ thuật tóm tắt

- **Tách dữ liệu**: mỗi công ty có dữ liệu riêng hoàn toàn về mặt logic: mọi dòng đều gắn `company_id`, mọi truy vấn tự động lọc theo công ty đang đăng nhập, công ty A không bao giờ đọc hay ghi được dòng của công ty B. Về mặt vật lý các công ty dùng chung một schema MySQL (cách "cột phân biệt", cũng là cách phần lớn SaaS nhỏ và vừa đang dùng). Phương án "mỗi công ty một schema riêng" cách ly mạnh hơn nhưng phải tạo 5 schema mỗi khi có công ty đăng ký, chạy Flyway cho từng schema, định tuyến kết nối động ở cả 5 service, tốn thêm 4 đến 6 ngày của Toàn và dễ lỗi khi demo; để ở "giai đoạn sau". Mỗi bảng nghiệp vụ thêm cột `company_id BIGINT NOT NULL`. Dùng `@TenantId` của Hibernate 6 (có sẵn trong Spring Boot 3.3): mọi truy vấn JPA tự thêm điều kiện `company_id = ?`, insert tự gán. Module `common` cung cấp `TenantContext`, `CurrentTenantIdentifierResolver`, filter đọc header `X-Company-Id` và lớp cha `TenantEntity`; mỗi service chỉ việc kế thừa lớp cha, thêm cột bằng Flyway và sửa ràng buộc duy nhất thành `(company_id, mã)`.
- **Danh tính**: auth-service thêm bảng `companies` (tên, mã, ngày tạo) và `users.company_id`; JWT thêm claim `companyId` và `plan`; gateway kiểm tra JWT rồi chuyển tiếp `X-Company-Id`, `X-Plan` cho service (giống `X-User-Name` hiện nay). Request không có công ty bị chặn ở gateway. Sự kiện RabbitMQ mang `companyId` trong message để consumer đặt lại ngữ cảnh.
- **Gói dịch vụ**: bảng `subscriptions` (company_id, plan, trial_ends_at, expires_at, status). Giới hạn gói đặt trong một bảng cấu hình (Free: 3 user, 1 kho, 200 SKU, 20 lượt AI; Trial: như Pro trong 30 ngày; Pro: 50 user, 20 kho, không giới hạn SKU, 500 lượt AI). Module `common` có `PlanLimitChecker` đọc gói từ header và đếm hiện tại qua Redis, trả mã lỗi `PLAN_LIMIT_EXCEEDED` hoặc `SUBSCRIPTION_EXPIRED` (HTTP 403) để frontend hiện trang gia hạn.
- **Thanh toán PayOS**: auth-service có bảng `payments` (company_id, gói, số tiền, orderCode, trạng thái, thời điểm). Luồng: người dùng chọn gói -> backend gọi API PayOS tạo link thanh toán (`POST /v2/payment-requests`, header `x-client-id`, `x-api-key`, chữ ký HMAC SHA256 trên amount, cancelUrl, description, orderCode, returnUrl bằng checksum key) -> frontend chuyển hướng sang trang thanh toán PayOS (quét QR chuyển khoản) -> PayOS gọi webhook `POST /api/auth/payments/payos/webhook` (nguồn tin cậy: kiểm tra chữ ký bằng checksum key, số tiền và orderCode chưa dùng rồi mới kích hoạt gói) và chuyển người dùng về `returnUrl` (trang kết quả). Biến môi trường `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` đặt trong `.env`; webhook được đăng ký với PayOS bằng API confirm-webhook khi triển khai. Khi chưa có key, code chạy với chế độ giả lập (bật `PAYOS_MOCK=true`) để nhóm phát triển và test không bị chặn. Webhook cần địa chỉ công khai, máy chủ wms.toanmarket.me đã có HTTPS nên đáp ứng.
- **AI**: service mới `ai-service` (cổng 8086) dùng Spring AI với Anthropic Claude (model claude-sonnet-5-5 cho trích xuất và chatbot, rẻ và nhanh). Key đặt trong `.env` trên máy chủ, không vào repo, xóa sau khi kết thúc môn. ai-service nhận file, gọi AI trả JSON theo schema cố định, trả về cho frontend xem trước; khi người dùng bấm nhập, ai-service gọi API nhập hàng loạt của product-service và order-service qua OpenFeign. Mỗi lượt gọi AI trừ hạn mức của gói. Tóm tắt báo cáo (WMS-18.1) và chatbot (WMS-18.2) chuyển sang chạy trong ai-service.
- **Tích hợp ngoài**: bảng `api_keys` (company_id, hash, tên, ngày thu hồi) ở auth-service; gateway chấp nhận header `X-Api-Key` thay cho JWT, quy đổi ra `X-Company-Id` và vai trò INTEGRATION chỉ đọc; rate limiter đã có sẽ tính theo key.

## 4. Task mới (mã WMS-22 đến WMS-25)

| Mã | Tên | Người | Ngày | Phụ thuộc | Kết quả nghiệm thu |
| --- | --- | --- | --- | --- | --- |
| WMS-22.1 | [Backend] Thiết kế đa doanh nghiệp và bộ lọc chung trong module common (TenantContext, @TenantId, TenantEntity, test), tài liệu docs/MULTI_TENANT.md | Toàn | 06/11 | WMS-02 xong | Test trong common chứng minh query tự lọc theo company_id |
| WMS-22.2 | [Backend] auth-service: bảng companies, users.company_id, JWT claim companyId, đăng nhập theo công ty, seed 2 công ty mẫu | Phương Anh | 06/11 | thiết kế 22.1 chốt sáng 06/11 | Đăng nhập trả JWT có companyId; user công ty A không đăng nhập được vào B |
| WMS-22.3 | [Backend] api-gateway chuyển tiếp X-Company-Id, chặn request thiếu công ty; áp mẫu vào inventory-service cùng Linh; test tích hợp 2 công ty | Toàn | 09/11 | 22.1, 22.2 | Gọi API kho bằng token công ty A chỉ thấy kho của A |
| WMS-22.4a | [Backend] inventory-service: company_id cho kho, vị trí, tồn kho, thẻ kho; ràng buộc duy nhất theo công ty; migration | Linh | 09/11 | 22.1 | Test cách ly pass |
| WMS-22.4b | [Backend] product-service và order-service: company_id cho SKU, đơn vị, nhà cung cấp, khách hàng, PO, SO, GRN, GDN; migration; API nhập hàng loạt POST /api/products/bulk, /api/orders/suppliers/bulk, /api/orders/customers/bulk | Việt | 10/11 đến 12/11 | 22.1 | Test cách ly pass; API bulk có validation và trả danh sách lỗi theo dòng |
| WMS-22.4c | [Backend] notification-service: company_id cho thông báo, báo cáo, KPI; sự kiện RabbitMQ mang companyId | Toàn | 16/11 | 22.1 | Báo cáo chỉ tổng hợp dữ liệu của công ty đang đăng nhập |
| WMS-23.0 | [Fullstack] Bỏ gợi ý tài khoản mặc định trên trang đăng nhập, bỏ tài khoản demo khỏi dữ liệu mẫu, đổi mật khẩu các tài khoản mẫu còn lại và cập nhật tài liệu | Toàn | 04/11 | không | Trang đăng nhập không còn gợi ý tài khoản; seed chỉ còn tài khoản của công ty mẫu |
| WMS-23.1 | [Backend] API đăng ký doanh nghiệp (tạo company, admin đầu tiên, Trial 30 ngày), bảng subscriptions và plans, API gia hạn giả lập, API xem gói hiện tại | Phương Anh | 09/11 | 22.2 | Đăng ký xong đăng nhập được ngay; gia hạn đổi được gói và ngày hết hạn |
| WMS-23.2 | [Backend] Thực thi giới hạn gói: PlanLimitChecker trong common, đếm user/kho/SKU/lượt AI qua Redis, chặn ghi khi hết hạn, mã lỗi chuẩn | Toàn | 10/11 | 23.1 | Vượt giới hạn hoặc hết hạn thì nhận 403 với mã lỗi đúng |
| WMS-23.5a | [Backend] Thanh toán PayOS: tạo link thanh toán có chữ ký, xử lý webhook và return, kiểm tra chữ ký và số tiền, kích hoạt gói, bảng payments, chế độ giả lập khi chưa có key, test chữ ký | Toàn | 10/11 | 23.1 | Thanh toán test thành công thì gói đổi sang Pro và ngày hết hạn tăng; webhook chữ ký sai bị từ chối |
| WMS-23.5b | [Frontend] Nút thanh toán trên trang gói dịch vụ, chuyển sang trang PayOS, trang kết quả thanh toán, lịch sử giao dịch | Linh | 16/11 | 23.5a | Chạy trọn bước 5 của kịch bản demo |
| WMS-23.3a | [Frontend] Trang giới thiệu và bảng giá (tĩnh, dữ liệu gói lấy từ file cấu hình) | Linh | 05/11 | không | Trang landing đẹp, responsive, nút Đăng ký và Đăng nhập |
| WMS-23.3b | [Frontend] Form đăng ký doanh nghiệp, đăng nhập theo công ty, nối API 23.1 | Phương Anh | 17/11 | 23.1 | Đăng ký và đăng nhập từ giao diện |
| WMS-23.6 | [Backend] Email xác nhận đăng ký (kích hoạt công ty qua liên kết), quên mật khẩu và đặt lại mật khẩu qua email, dùng notification-service gửi mail | Phương Anh | 18/11 | 23.1 | Đăng ký xong nhận email kích hoạt; đặt lại mật khẩu thành công |
| WMS-26.1 | [Frontend] Rà soát giao diện toàn hệ thống theo góp ý của Toàn (bố cục, luồng thao tác, chữ, responsive), sửa trực tiếp hoặc giao lại cho người phụ trách màn hình | Toàn | 18/11 đến 19/11 | mọi màn hình đã có | Danh sách góp ý đã xử lý, ảnh trước và sau |
| WMS-23.4 | [Frontend] Trang Gói dịch vụ và gia hạn, banner "còn N ngày dùng thử", màn hình hết hạn chỉ đọc, hiển thị mức dùng so với giới hạn | Linh | 16/11 | 23.1, 23.2 | Hết hạn thì mọi nút ghi bị khóa và dẫn tới trang gia hạn |
| WMS-24.1 | [Backend] ai-service: khung Spring AI + Claude, key qua .env, đếm lượt theo gói, Dockerfile, compose, CI, route gateway /api/ai/** | Toàn | 11/11 | 23.2 | /api/ai/health chạy; gọi thử trả lời được |
| WMS-24.2 | [Backend] API trích xuất tài liệu (Excel, CSV, PDF, ảnh) thành JSON hàng hóa, nhà cung cấp, khách hàng theo schema cố định, kèm độ tin cậy từng dòng; API nhập sau khi người dùng xác nhận (gọi bulk của 22.4b) | Toàn | 12/11 | 24.1, 22.4b | File Excel 50 dòng trích xuất đúng trên 90% ô; nhập xong thấy trong danh mục |
| WMS-24.3 | [Frontend] Màn hình "Nhập dữ liệu bằng AI": tải file, bảng xem trước có đánh dấu ô độ tin cậy thấp, sửa tay, chọn loại dữ liệu, bấm nhập, xem kết quả theo dòng | Việt | 16/11 đến 17/11 | 24.2 | Chạy trọn kịch bản demo bước 2 |
| WMS-24.4 | [Backend] Trợ lý AI: gộp chatbot tra cứu tồn kho, đơn hàng (18.2) với hướng dẫn sử dụng lấy từ docs của hệ thống; luôn giới hạn trong dữ liệu công ty đang đăng nhập | Toàn | 13/11 | 24.1 | Hỏi "tạo kho thế nào" nhận hướng dẫn đúng; hỏi tồn SKU nhận số đúng |
| WMS-25.1 | [Backend] API key theo doanh nghiệp (tạo, thu hồi trong trang Cài đặt), gateway xác thực X-Api-Key, vai trò INTEGRATION chỉ đọc, rate limit theo key | Toàn | 17/11 | 22.3 | curl với API key đọc được tồn kho của đúng công ty |
| WMS-25.2 | [Docs] Hướng dẫn tích hợp, Swagger công khai (gộp WMS-21.1) | Toàn | 20/11 | 25.1 | Tài liệu trong docs/INTEGRATION.md |
| WMS-22.5 | [QA] Kiểm thử cách ly dữ liệu 2 công ty, giới hạn gói, hết hạn (gộp vào WMS-20.3) | Phương Anh | 19/11 đến 20/11 | tất cả | Bảng test case có kết quả |

Việc chuẩn bị, không tính vào lịch của nhóm: Toàn cập nhật SRS (chương "Mô hình SaaS"), Master Plan v3 (file Excel), Jira và README ngày 04/11 ngay khi nhóm đồng ý.

## 5. Lịch theo người (chỉ ngày làm việc)

| Ngày | Toàn | Phương Anh | Việt | Linh |
| --- | --- | --- | --- | --- |
| 03/11 Thứ 3 | 02.1 RabbitMQ topology | 04.2 lọc theo kho | 08.2, 09.1, 11.1 | (trống) |
| 04/11 Thứ 4 | 02.1 (hoàn tất), 02.2, 23.0 bỏ tài khoản mặc định và demo | 04.3 trang quản lý user | 08.3, 09.3, 09.4, 11.2, 11.3, 11.4 | 09.2 incoming stock |
| 05/11 Thứ 5 | 02.3, 02.4 WebSocket | 04.4 hồ sơ cá nhân | 08.4, 10.1, 12.1 | 23.3a landing và bảng giá |
| 06/11 Thứ 6 | 22.1 thiết kế đa doanh nghiệp, bộ lọc chung | 22.2 companies, JWT companyId | 10.2, 10.3, 10.4, 12.3, 12.4 | 12.2 khóa bi quan |
| 09/11 Thứ 2 | 22.3 gateway, mẫu inventory, test 2 công ty | 23.1 đăng ký, gói, dùng thử | 10.4, 10.5, 12.4 | 22.4a inventory company_id |
| 10/11 Thứ 3 | 23.2 giới hạn gói, 23.5a thanh toán PayOS | 16.1 | 22.4b product, order (1/3) | 13.1 |
| 11/11 Thứ 4 | 24.1 ai-service | 16.1, 16.2, 16.3, 16.4 | 22.4b (2/3) | 13.1, 13.2, 13.3, 13.4 |
| 12/11 Thứ 5 | 24.2 AI trích xuất, 18.1 AI summary | 16.4, 15.1, 17.1 | 22.4b (3/3), API bulk | 14.1, 14.2 |
| 13/11 Thứ 6 | 24.4 trợ lý AI (gộp 18.2), 18.3 | 15.1, 17.1, 15.2, 17.2, 17.4, 15.4, 17.5 | 17.3 Excel báo cáo | 14.2, 14.3, 14.4, 15.3 |
| 16/11 Thứ 2 | 18.4 popup chatbot, 22.4c notification company_id | 15.4, 17.5 | 24.3 màn hình nhập bằng AI (1/2) | 23.4 gói dịch vụ, gia hạn, 23.5b thanh toán PayOS |
| 17/11 Thứ 3 | 25.1 API key | 23.3b đăng ký, đăng nhập theo công ty | 24.3 (2/2) | 19.1 Redis cache |
| 18/11 Thứ 4 | 19.2 JMeter, 26.1 rà soát giao diện (1/2) | 23.6 email xác nhận, quên mật khẩu | 20.1 E2E mua nhập | 19.1, 19.3, 20.2 |
| 19/11 Thứ 5 | 20.4 bugfix, 26.1 rà soát giao diện (2/2) | 20.3 bảo mật, 22.5 cách ly dữ liệu | 20.1, 20.4 | 20.2, 20.4 |
| 20/11 Thứ 6 | 21.1 Swagger, 25.2 tích hợp, 21.2 tài liệu | 20.3, 22.5, 20.4 | 20.4 | 20.4 |
| 23/11 Thứ 2 | 21.2, 21.3 deploy v1.0.0, 21.4 | 21.4 | 21.4 | 21.4 |

Khối lượng thêm: Toàn 11 ngày (có trợ lý AI nên nhận nhiều hơn), Phương Anh 5 ngày, Việt 5 ngày, Linh 3,5 ngày. Các ngày 10/11 đến 13/11 là nặng nhất với mọi người, cần commit đều và báo sớm nếu kẹt.

## 6. Thứ tự bắt buộc và rủi ro

- Chuỗi phụ thuộc chính: 22.1 (06/11) -> 22.2 (06/11) -> 22.3 và 22.4a (09/11) -> 22.4b (10 đến 12/11) -> 24.2 (12/11) -> 24.3 (16/11). Nếu 22.4b trễ, dời 24.3 sang 18/11 và Việt lùi 20.1 sang 19/11.
- Rủi ro lớn nhất: lọt dữ liệu giữa hai công ty khi demo. Giảm rủi ro bằng bộ lọc tự động của Hibernate (không trông vào từng câu query), test cách ly bắt buộc ở từng service trước khi merge, và một bộ dữ liệu mẫu hai công ty để test đi test lại.
- Rủi ro thứ hai: ràng buộc duy nhất cũ (mã SKU, username, mã kho) làm hai công ty không thể dùng trùng mã. Mọi unique index phải đổi thành (company_id, mã) trong migration; username vẫn duy nhất toàn hệ thống để đăng nhập đơn giản (email cũng vậy).
- Chi phí AI: trích xuất một file 50 dòng khoảng 0,02 USD; cả đợt test dưới 5 USD. Giới hạn lượt theo gói để không ai gọi vô hạn. Key chỉ nằm trong .env trên máy chủ và máy của Toàn.
- Dữ liệu cũ trên máy chủ: migration gán toàn bộ dữ liệu hiện có vào "Công ty mẫu" (id 1) để không mất dữ liệu demo đã nhập.
- Không mở rộng thêm sau 06/11. Ý tưởng mới ghi vào mục "Giai đoạn sau" của SRS.

## 7. Việc cần quyết ngay

1. Anh xác nhận với nhóm phân công ở mục 5 (ai cũng phải sửa entity của service mình trong ngày 09 đến 12/11).
2. Chốt tên gói, giá tiền và giới hạn (mục 3) để Linh làm bảng giá ngay 05/11.
3. PayOS: code làm trước với chế độ giả lập; khi Toàn có key thì tự điền PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY vào file .env trên máy chủ (không gửi qua chat, không đưa vào repo) rồi tắt PAYOS_MOCK.
4. Jira (epic "EPIC 10: THƯƠNG MẠI HÓA SAAS & AI", task WMS-22 đến WMS-26), Master Plan v3, SRS và README được cập nhật ngày 03/11 theo bản này; 22.1 bắt đầu 06/11 như lịch.

## 8. Mã Jira tương ứng (tạo ngày 03/11/2026)

| Mã Master Plan | Jira | Mã Master Plan | Jira | Mã Master Plan | Jira |
| --- | --- | --- | --- | --- | --- |
| EPIC 10 | TASK-131 | WMS-22 | TASK-132 | WMS-22.1 | TASK-133 |
| WMS-22.2 | TASK-134 | WMS-22.3 | TASK-135 | WMS-22.4a | TASK-136 |
| WMS-22.4b | TASK-137 | WMS-22.4c | TASK-138 | WMS-22.5 | TASK-139 |
| WMS-23 | TASK-140 | WMS-23.0 | TASK-141 | WMS-23.1 | TASK-142 |
| WMS-23.2 | TASK-143 | WMS-23.3a | TASK-144 | WMS-23.3b | TASK-145 |
| WMS-23.4 | TASK-146 | WMS-23.5a | TASK-147 | WMS-23.5b | TASK-148 |
| WMS-23.6 | TASK-149 | WMS-24 | TASK-150 | WMS-24.1 | TASK-151 |
| WMS-24.2 | TASK-152 | WMS-24.3 | TASK-153 | WMS-24.4 | TASK-154 |
| WMS-25 | TASK-155 | WMS-25.1 | TASK-156 | WMS-25.2 | TASK-157 |
| WMS-26 | TASK-158 | WMS-26.1 | TASK-159 |  |  |

Jira: https://plinhle26.atlassian.net/jira/software/projects/TASK/list (lọc nhãn SaaS).
