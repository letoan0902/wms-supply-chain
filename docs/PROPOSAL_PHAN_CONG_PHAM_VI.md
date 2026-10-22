# Phân chia nhân sự và phạm vi tính năng

Tài liệu do Lê Phú Toàn (TechLead) biên soạn cho Project Proposal, bám theo Tài liệu đặc tả dự án của nhóm. Mã FR tham chiếu phần Yêu cầu chức năng.

## 1. Phân chia nhân sự và vai trò trách nhiệm

Quy mô nhóm: 4 thành viên, Agile Scrum, Sprint 3 ngày. Mỗi thành viên sở hữu trọn vẹn một nhóm microservice (backend, frontend, kiểm thử).

| STT | Họ và tên (MSV) | Vai trò chính | Phạm vi trách nhiệm |
|---|---|---|---|
| 1 | Phạm Phương Anh (Leader / Scrum Master) (MSV: B24DTCN...) | DevOps & Platform Lead (Thành viên 1) | Dựng hạ tầng microservice: Config Server, Eureka Service Discovery, API Gateway (định tuyến, kiểm tra JWT tập trung, rate limiting). Đóng gói Docker đa giai đoạn cho mọi service; Docker Compose với mạng wms-network, named volume, healthcheck và khởi động tuần tự. Thiết lập CI/CD GitHub Actions: build, kiểm thử tự động, đóng gói và đẩy Docker image; quản lý biến môi trường và bí mật (.env). Logging hai lớp (audit log nghiệp vụ, error log kỹ thuật) và backup MySQL định kỳ kèm kiểm tra khôi phục. QA và kiểm thử tích hợp toàn hệ thống; tích hợp AI Assistant tóm tắt báo cáo (nâng cao). Vai trò quản trị: điều phối tiến độ, quản lý board Trello/Jira, chủ trì Daily Standup và họp Sprint. |
| 2 | Lê Phú Toàn (TechLead) (MSV: B24DTCN385) | Core Inventory & Warehouse Lead (Thành viên 2) | Vai trò TechLead: thiết kế kiến trúc tổng thể, chuẩn code và quy ước API, khởi tạo repository và quy ước Git flow, review và merge Pull Request. Inventory & Warehouse Service (service cốt lõi): thiết kế CSDL và REST API đa kho (Multi-warehouse), vị trí Zone/Rack/Bin, thẻ kho và biến động tồn thực tế. Điều chuyển nội bộ (Stock Transfer) với trạng thái DRAFT, IN_TRANSIT, COMPLETED; kiểm kê (Stocktake) và điều chỉnh kho (Adjustment) có cấp duyệt. Kỹ thuật lõi: khóa bi quan (PESSIMISTIC_WRITE) chống âm kho khi thao tác đồng thời, Redis cache cho dữ liệu tồn kho, phát cảnh báo tồn kho an toàn. Frontend: danh sách kho, sơ đồ vị trí Bin, bảng tồn kho thời gian thực với bộ lọc đa điều kiện và tag màu cảnh báo, màn hình lập lệnh điều chuyển, giao diện kiểm đếm khi kiểm kê. |
| 3 | Lê Phương Linh  (MSV: B24DTCN...) | Core Supply Chain & Master Data Lead (Thành viên 3) | Product Service: danh mục hàng hóa và SKU, nhóm hàng, đơn vị tính quy đổi đa cấp, giá nhập/bán, VAT, sinh Barcode/QR (ZXing), import/export Excel. Order Service: nhà cung cấp và khách hàng kèm công nợ; luồng Đơn mua (PO) sang Phiếu nhập (GRN) với nhập từng phần, số lô và hạn sử dụng; luồng Đơn bán (SO) với kiểm tra tồn khả dụng (ATP), Picking và Phiếu xuất (GDN); hóa đơn mua/bán; xuất kho theo FIFO/FEFO. Frontend: quản lý SKU và in tem nhãn Barcode, danh sách đối tác, màn hình PO/SO, form lập phiếu nhập/xuất kho thông minh tự động focus ô quét mã. Chủ trì tài liệu Yêu cầu chức năng (FR01 đến FR25) và cập nhật khi phạm vi thay đổi. |
| 4 | Trần Đăng Việt  (MSV: B24DTCN...) | Auth, Analytics & QA Lead (Thành viên 4) | Auth Service: đăng nhập với Access Token và Refresh Token (JWT), băm mật khẩu BCrypt, khóa tài khoản khi đăng nhập sai nhiều lần, phân quyền RBAC 6 vai trò, gán người dùng theo kho. Notification & Report Service: thẻ KPI, báo cáo Xuất-Nhập-Tồn, doanh thu, công nợ, xuất Excel (Apache POI); Spring Mail cảnh báo tồn kho an toàn và hàng cận hạn; job hàng ngày Near-expiry và Dead stock. Frontend: đăng nhập, quên mật khẩu, quản lý người dùng và cấp quyền theo kho; Executive Dashboard (hàng thẻ KPI, biểu đồ cột Mua vào vs Bán ra, biểu đồ donut Trending Items bằng Recharts, bảng kê giao dịch gần nhất). Chủ trì tài liệu Yêu cầu phi chức năng (NFR01 đến NFR11) và kế hoạch kiểm thử hiệu năng, bảo mật. |

### Ma trận trách nhiệm (C = chủ trì, H = hỗ trợ, R = review)

| Phân hệ | P. Phương Anh | L. Phú Toàn | L. Phương Linh | T. Đăng Việt |
|---|---|---|---|---|
| Hạ tầng microservice, Docker, CI/CD, logging và backup | C | R | H | H |
| Xác thực và phân quyền (Auth & RBAC) | H | R |  | C |
| Danh mục hàng hóa, SKU, đơn vị tính, Barcode |  | R | C | H |
| Đa kho, vị trí Bin, tồn kho, điều chuyển nội bộ | H | C | H |  |
| Mua hàng và nhập kho (NCC, PO, GRN, lô và hạn dùng) |  | R | C | H |
| Bán hàng và xuất kho (KH, SO, ATP, Picking, GDN) |  | R | C | H |
| Kiểm kê và điều chỉnh kho, cảnh báo tồn kho | H | C |  | H |
| Báo cáo, Dashboard, thông báo email | H | R | H | C |
| Kiểm thử tích hợp, tài liệu và quản trị tiến độ | C | H | H | H |

### Cơ chế phối hợp

- Mỗi thành viên sở hữu trọn vẹn một nhóm service theo mô hình full-stack: thiết kế bảng dữ liệu, REST API, giao diện và kiểm thử cho phân hệ của mình; nhờ đó giảm phụ thuộc chéo trong một Sprint ngắn 3 ngày.
- TechLead chịu trách nhiệm thống nhất kiến trúc, hợp đồng API giữa các service (OpenAPI), chuẩn code và review mọi Pull Request vào nhánh develop; Leader chịu trách nhiệm kế hoạch, board công việc và theo dõi tiến độ.
- Giao tiếp giữa các phân hệ qua API Gateway (REST) và RabbitMQ (sự kiện), ví dụ: Order Service phát sự kiện xác nhận xuất kho, Inventory Service trừ tồn, Notification Service gửi cảnh báo.
- Mỗi Pull Request phải có ít nhất một thành viên khác review, CI xanh (build, test, lint) trước khi merge; task trên board đi qua các cột Backlog, To Do, In Progress, Review, Done.
- Daily Standup 15 phút lúc 20:30 hằng ngày qua Google Meet; cuối mỗi Sprint có buổi demo và retrospective ngắn.

## 2. Phạm vi và tính năng chính

### 2.1. Phân hệ Xác thực & Phân quyền (Authentication & RBAC)

Phụ trách: Trần Đăng Việt.

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR01 | Đăng nhập bằng tài khoản hoặc email và mật khẩu; mật khẩu băm BCrypt; Access Token 15 phút và Refresh Token trong HttpOnly cookie; khóa tài khoản 30 phút khi sai quá 5 lần. | MVP | Sprint 2 |
| FR02 | Sáu vai trò ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, PURCHASING, SALES, ACCOUNTANT; gán người dùng cho một hoặc nhiều kho, dữ liệu lọc theo kho được cấp quyền; xem và sửa hồ sơ, đổi mật khẩu. | MVP | Sprint 2 |

### 2.2. Phân hệ Quản lý Danh mục & Đa kho bãi (Warehouse & Master Data)

Phụ trách: Lê Phú Toàn (kho, vị trí, điều chuyển) và Lê Phương Linh (SKU, đơn vị tính, Barcode).

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR03 | Quản lý đa kho: thêm, sửa, khóa kho độc lập; thông tin mã, tên, địa chỉ, trưởng kho, sức chứa; thẻ tóm tắt số mặt hàng, tổng tồn, số vị trí đang dùng; không cho xóa kho còn tồn. | MVP | Sprint 2 |
| FR04 | Vị trí lưu trữ theo cấu trúc Warehouse, Zone, Rack, Bin với mã duy nhất (ví dụ WH1-ZA-R01-B02), trạng thái Empty, Occupied, Full; in tem mã vạch vị trí. | MVP | Sprint 2 |
| FR05 | Danh mục hàng hóa và SKU: mã, tên, nhóm hàng, giá nhập/bán, VAT, ảnh; cấu hình Safety Stock, Max Stock, Reorder Point; cờ quản lý theo lô và hạn dùng; import/export Excel theo mẫu. | MVP | Sprint 2 |
| FR06 | Đơn vị tính và quy đổi đa cấp (ví dụ 1 Thùng = 4 Lốc = 24 Chai); hệ thống tự quy đổi về đơn vị cơ sở khi tính tồn. | MVP | Sprint 2 |
| FR07 | Sinh Barcode EAN-13, Code 128 hoặc QR chứa SKU và lô; in tem 35x22 mm hoặc 50x30 mm; quét mã cập nhật số lượng tức thời. | MVP (sinh và quét mã); in tem máy in nhiệt: Nâng cao | Sprint 2-3 |
| FR08 | Điều chuyển nội bộ giữa hai kho với trạng thái DRAFT, IN_TRANSIT, COMPLETED hoặc PARTIAL_RECEIVED; duyệt thì trừ tồn kho nguồn và ghi nhận hàng đang đi đường; nhận hàng thì quét, xác nhận, ghi biên bản thiếu hỏng. | MVP | Sprint 3 |

### 2.3. Phân hệ Quản lý Mua hàng & Nhập kho (Inbound Logistics & Suppliers)

Phụ trách: Lê Phương Linh.

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR09 | Hồ sơ nhà cung cấp: tên, mã số thuế, liên hệ, email, địa chỉ, tài khoản ngân hàng; điều khoản thanh toán, hạn mức nợ, lịch sử giao dịch. | MVP | Sprint 2 |
| FR10 | Đơn đặt mua hàng (PO) tự sinh mã, chọn nhà cung cấp, kho, SKU, số lượng, giá, chiết khấu, VAT; tự tính dòng, thuế, tổng; phê duyệt Pending, APPROVED, REJECTED; duyệt thì tăng Incoming Stock. | MVP | Sprint 2 |
| FR11 | Phiếu nhập kho (GRN) theo PO đã duyệt, cho phép nhập từng phần; quét mã, ghi nhận Accepted và Rejected; cập nhật tồn thực tế, giảm Incoming, đóng PO khi nhập đủ. | MVP | Sprint 3 |
| FR12 | Quản lý lô hàng: số lô, ngày sản xuất, hạn dùng; tồn theo lô và vị trí; xuất ưu tiên FEFO hoặc FIFO; tự khóa lô hết hạn. | MVP (lô, hạn dùng, FIFO); FEFO tự động: Nâng cao | Sprint 3 |
| FR13 | Hóa đơn mua liên kết GRN; tăng công nợ phải trả; ghi nhận thanh toán và chuyển trạng thái PAID khi trả đủ. | Nâng cao | Sprint 4 |

### 2.4. Phân hệ Quản lý Bán hàng & Xuất kho (Outbound Logistics & Sales)

Phụ trách: Lê Phương Linh.

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR14 | Hồ sơ khách hàng bán sỉ và lẻ; hạn mức công nợ và điều khoản thanh toán; cảnh báo hoặc khóa khi vượt hạn mức, nợ quá hạn. | MVP | Sprint 2 |
| FR15 | Đơn bán hàng (SO) kiểm tra tồn khả dụng ATP = OnHand trừ Reserved; đủ hàng thì xác nhận, thiếu thì báo thiếu hoặc Backorder; tự tính thành tiền, chiết khấu, VAT, tổng. | MVP | Sprint 3 |
| FR16 | Giữ hàng mềm (Soft Reservation): xác nhận SO thì tăng Reserved, giảm ATP, trạng thái CONFIRMED; cập nhật KPI đơn chờ xử lý. | MVP | Sprint 3 |
| FR17 | Xuất kho thông minh: tự tạo Picking List, gợi ý vị trí lấy hàng theo FEFO/FIFO, in phiếu giao hàng; xác nhận xuất thì trừ OnHand, giải phóng Reserved. | MVP (picking list, phiếu giao); tối ưu lộ trình Zone-Rack-Bin: Nâng cao | Sprint 3 |
| FR18 | Hóa đơn bán hiển thị trong Recent Invoices với Balance và trạng thái PAID/UNPAID; bán nợ tăng công nợ phải thu, thanh toán giảm công nợ. | Nâng cao | Sprint 4 |

### 2.5. Phân hệ Kiểm kê & Cảnh báo thông minh (Auditing & Alerts)

Phụ trách: Lê Phú Toàn (kiểm kê, cảnh báo tồn) và Trần Đăng Việt (job cận hạn, thông báo).

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR21 | Cảnh báo tồn kho an toàn: sau mỗi lần xuất, tồn nhỏ hơn hoặc bằng Reorder Point thì cảnh báo vàng và đề xuất PO; nhỏ hơn hoặc bằng Safety Stock thì cảnh báo đỏ; hiển thị nổi bật trên Dashboard và gửi thông báo. | MVP | Sprint 3 |
| FR19 | Kiểm kê định kỳ theo kho, khu vực hoặc nhóm hàng: đóng băng vị trí đang kiểm, chụp snapshot, đếm blind qua máy quét, tính chênh lệch và giá trị. | Nâng cao | Sprint 4 |
| FR20 | Điều chỉnh kho (Adjustment In/Out) từ báo cáo chênh lệch, phê duyệt bởi Quản lý kho và Kế toán; sau duyệt cập nhật tồn, mở freeze, lưu audit log. | Nâng cao | Sprint 4 |
| FR22 | Job hàng ngày lúc 00:00: gắn nhãn CẬN DATE cho lô có hạn dùng trong 30 ngày, ưu tiên FEFO; nhận diện Dead Stock không xuất quá 90 ngày và đề xuất thanh lý. | Nâng cao | Sprint 4 |

### 2.6. Phân hệ Báo cáo & Dashboard phân tích (Analytics & Reports)

Phụ trách: Trần Đăng Việt.

| Mã | Tính năng | Ưu tiên | Sprint |
|---|---|---|---|
| FR23 | Dashboard với 8 thẻ KPI: Pending và Completed Sale Orders, Payment Receivables, Payment Payables, Pending và Completed Purchase Orders, Total Expense, Total Customers; bấm thẻ mở danh sách chi tiết. | MVP | Sprint 3 |
| FR24 | Biểu đồ cột đôi Sale vs Purchase theo thời gian, biểu đồ donut Trending Items, bảng tổng quan All Warehouses (số mặt hàng, tổng tồn, số vị trí, tỷ lệ lấp đầy). | MVP | Sprint 3 |
| FR25 | Bảng kê Recent Invoices; báo cáo Xuất-Nhập-Tồn (FIFO hoặc bình quân gia quyền); thẻ kho chi tiết theo SKU; truy vết theo lô từ PO, nhà cung cấp, vị trí đến lô xuất; xuất Excel. | MVP (Recent Invoices, Xuất-Nhập-Tồn); thẻ kho chi tiết và truy vết lô: Nâng cao | Sprint 3-4 |

### 2.7. Tính năng nâng cao bổ sung

- Notification & AI Report Service: AI tóm tắt tình hình kho hằng tuần và gợi ý mặt hàng cần nhập thêm (Spring AI với Gemini hoặc OpenAI).
- Đẩy thông báo thời gian thực tới giao diện qua WebSocket (KPI, cảnh báo tồn kho).
- Gửi email xác nhận PO/SO kèm tệp PDF cho đối tác.
- In tem nhãn trực tiếp ra máy in nhiệt từ trình duyệt.

### 2.8. Ngoài phạm vi

- Kế toán tổng hợp (sổ cái, báo cáo tài chính theo chuẩn kế toán); hệ thống chỉ theo dõi công nợ phải thu, phải trả ở mức nghiệp vụ kho.
- Thanh toán trực tuyến, hóa đơn điện tử và tích hợp với cơ quan thuế.
- Quản lý vận tải, đội xe và theo dõi hành trình giao hàng (TMS).
- Tích hợp sàn thương mại điện tử, phần mềm bán hàng POS bên ngoài.
- Ứng dụng di động native; giao diện web được thiết kế responsive cho điện thoại và máy tính bảng.
- Đa tiền tệ, đa ngôn ngữ; hệ thống dùng tiếng Việt và đồng Việt Nam.

### 2.9. Tiêu chí hoàn thành MVP

- Chạy trọn vẹn luồng chính với dữ liệu thật: tạo SKU và kho, lập PO, nhập kho GRN làm tăng tồn, lập SO kiểm tra ATP, xuất kho GDN làm giảm tồn, lịch sử giao dịch và thẻ kho ghi nhận đầy đủ.
- Cảnh báo tồn kho thấp xuất hiện trên Dashboard ngay sau giao dịch làm tồn chạm ngưỡng.
- Đăng nhập bằng JWT, phân quyền theo vai trò và theo kho hoạt động đúng với ít nhất ba vai trò.
- Dashboard hiển thị thẻ KPI, hai biểu đồ và bảng giao dịch gần nhất từ dữ liệu thật.
- Toàn bộ hệ thống khởi động bằng một lệnh docker compose up -d; pipeline CI xanh trên nhánh develop và main.
