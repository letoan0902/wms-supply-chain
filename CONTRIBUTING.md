# Hướng dẫn đóng góp

Tài liệu này quy định cách nhóm 4 người cùng làm việc trên repository WMS Supply Chain: Git flow, quy ước commit,
quy trình Pull Request, quản lý công việc trên board và tiêu chuẩn hoàn thành (Definition of Done).

## 1. Quy trình tổng quan

1. Nhận thẻ công việc trên board (Trello hoặc Jira), chuyển sang In Progress.
2. Tạo nhánh từ `develop` theo quy ước đặt tên (mục 2).
3. Code, commit theo Conventional Commits (mục 3), chạy build và test ở máy cục bộ.
4. Đẩy nhánh lên GitHub, mở Pull Request vào `develop` theo template, chuyển thẻ sang Review (mục 4).
5. Sửa theo góp ý, chờ CI xanh và có ít nhất 1 approve.
6. Squash merge vào `develop`, xóa nhánh, chuyển thẻ sang Done khi đạt Definition of Done (mục 7).

## 2. Git flow và đặt tên nhánh

| Nhánh | Mục đích | Quy tắc |
| --- | --- | --- |
| `main` | Bản phát hành ổn định | Luôn chạy được. Chỉ nhận merge từ `develop` qua PR khi kết thúc sprint hoặc phát hành, gắn tag phiên bản (ví dụ `v0.1.0`). Không push trực tiếp. |
| `develop` | Nhánh tích hợp | Mọi tính năng được merge vào đây, phải luôn build xanh. Không push trực tiếp. |
| `feat/<mã-task>-<mô-tả>` | Tính năng mới | Tách từ `develop`, merge lại vào `develop`. |
| `fix/<mã-task>-<mô-tả>` | Sửa lỗi | Tách từ `develop`, merge lại vào `develop`. |
| `chore/<mã-task>-<mô-tả>` | Việc phụ trợ: CI, cấu hình, nâng dependency, tài liệu | Tách từ `develop`, merge lại vào `develop`. |

Cách đặt tên:

- `<mã-task>` là mã thẻ trên board, ví dụ `WMS-12`.
- `<mô-tả>` viết thường, không dấu, ngăn cách bằng dấu gạch ngang, ngắn gọn (tối đa khoảng 5 từ).
- Ví dụ: `feat/WMS-12-tao-phieu-nhap-kho`, `fix/WMS-31-sai-so-luong-ton`, `chore/WMS-5-nang-cap-spring-boot`.

Một nhánh chỉ làm một công việc. Nên cập nhật nhánh từ `develop` thường xuyên (rebase hoặc merge) để tránh xung đột lớn.
Lỗi khẩn cấp trên bản phát hành được sửa bằng nhánh `fix/...` và phải đưa về cả `main` lẫn `develop`.

## 3. Quy ước commit (Conventional Commits)

Định dạng:

```text
<type>(<scope>): <mô tả ngắn>

<nội dung chi tiết, giải thích lý do thay đổi (tùy chọn)>

Refs: WMS-12
```

| Type | Dùng khi |
| --- | --- |
| `feat` | Thêm tính năng mới |
| `fix` | Sửa lỗi |
| `docs` | Chỉ thay đổi tài liệu |
| `refactor` | Cấu trúc lại code, không đổi hành vi |
| `chore` | Việc phụ trợ: cấu hình, nâng dependency, dọn dẹp |
| `test` | Thêm hoặc sửa test |
| `ci` | Thay đổi pipeline CI, Dockerfile, docker-compose |

Quy tắc:

- `scope` (tùy chọn) là tên phân hệ: `auth`, `product`, `inventory`, `order`, `notification`, `gateway`, `config`,
  `discovery`, `frontend`, `infra`, `docs`.
- Mô tả bằng tiếng Việt có dấu hoặc tiếng Anh, viết ở dạng mệnh lệnh, tối đa 72 ký tự, không có dấu chấm cuối.
- Thay đổi phá vỡ tương thích: thêm `!` sau scope (ví dụ `feat(auth)!: đổi cấu trúc token`) và mô tả trong phần nội dung.
- Mỗi commit là một thay đổi có ý nghĩa, không commit file tạm, file build, bí mật hay file `.env`.

Ví dụ:

```text
feat(inventory): thêm API tạo phiếu điều chuyển kho
fix(auth): trả về 401 khi access token hết hạn
docs: cập nhật hướng dẫn chạy docker compose
ci: thêm job build thử image frontend
```

## 4. Quy tắc Pull Request

1. Mọi thay đổi đều đi qua Pull Request, không push trực tiếp lên `main` và `develop`.
2. PR phải tạo vào `develop`. Riêng PR phát hành đi từ `develop` vào `main` do TechLead tạo.
3. Tiêu đề PR theo Conventional Commits, mô tả theo template `.github/PULL_REQUEST_TEMPLATE.md`, gắn mã task.
4. Mỗi PR chỉ có một mục đích và nhỏ gọn để dễ review (gợi ý dưới khoảng 400 dòng thay đổi).
5. Cần ít nhất 1 thành viên khác review và approve; người tạo PR không tự approve. TechLead (Lê Phú Toàn) review mọi PR vào
   `develop`; PR của TechLead do một thành viên khác review. Thay đổi trong thư mục có chủ sở hữu trong `.github/CODEOWNERS`
   cần chủ sở hữu đó review.
6. CI phải xanh (job Backend và Frontend) trước khi merge. Job Docker chỉ chạy khi push nhánh.
7. Mọi góp ý phải được trả lời hoặc đánh dấu đã giải quyết trước khi merge.
8. Merge bằng Squash and merge, tiêu đề commit gộp theo Conventional Commits. Xóa nhánh sau khi merge.
9. Sprint chỉ kéo dài 3 ngày nên cần review sớm: không để PR chờ qua ngày làm việc.

Reviewer kiểm tra: đúng yêu cầu của thẻ, logic và xử lý lỗi, test đi kèm, bảo mật (không lộ bí mật, kiểm tra quyền),
tuân thủ `docs/CONVENTIONS.md`, tài liệu và migration CSDL nếu có thay đổi.

Cấu hình bảo vệ nhánh `main` và `develop` trên GitHub (do người quản trị repository thiết lập): bắt buộc PR, tối thiểu
1 approve, bắt buộc status check `Backend` và `Frontend`, yêu cầu nhánh được cập nhật mới nhất, cấm force push và xóa nhánh.

## 5. Quy trình trên board (Trello hoặc Jira)

Công việc đi qua các cột theo thứ tự: Backlog -> To Do -> In Progress -> Review -> Done.

| Cột | Ý nghĩa | Điều kiện chuyển sang cột này |
| --- | --- | --- |
| Backlog | Công việc chưa xếp vào sprint hiện tại | Thẻ mới tạo, đã có tiêu đề và mô tả sơ bộ |
| To Do | Đã chọn cho sprint hiện tại | Có người phụ trách, tiêu chí chấp nhận và ước lượng rõ ràng |
| In Progress | Đang thực hiện | Đã nhận việc và tạo nhánh. Mỗi người nên giữ tối đa 1 đến 2 thẻ cùng lúc |
| Review | Chờ review và kiểm tra | Đã mở PR, CI xanh, thẻ gắn liên kết PR |
| Done | Hoàn thành | PR đã merge vào `develop` và đạt Definition of Done |

Mỗi thẻ cần có: mã task (ví dụ `WMS-12`), mô tả, tiêu chí chấp nhận, người phụ trách và sprint. Mã task được ghi
vào tên nhánh, tiêu đề hoặc phần `Refs` của commit và phần mô tả của PR để truy vết hai chiều.

Việc phát sinh trong sprint được thêm vào Backlog trước, chỉ đưa vào To Do khi cả nhóm đồng ý.

## 6. Daily Standup

- Thời gian: 20:30 mỗi ngày, họp qua Google Meet, tối đa 15 phút. Leader và Scrum Master (Phạm Phương Anh) chủ trì,
  quản lý board và theo dõi tiến độ; cuối mỗi sprint cả nhóm demo và làm retrospective ngắn.
- Mỗi người trả lời ngắn gọn ba câu hỏi: hôm qua đã làm gì, hôm nay sẽ làm gì, đang vướng mắc gì.
- Cập nhật trạng thái thẻ trên board trước giờ họp để cuộc họp tập trung vào vướng mắc.
- Vấn đề cần thảo luận sâu để lại xử lý riêng sau buổi họp giữa những người liên quan.
- Nếu không tham dự được, nhắn tiến độ và vướng mắc vào kênh chat của nhóm trước giờ họp.

## 7. Definition of Done

Một công việc chỉ được chuyển sang Done khi thỏa mãn tất cả:

- [ ] Đáp ứng đầy đủ tiêu chí chấp nhận ghi trên thẻ.
- [ ] Code tuân thủ `docs/CONVENTIONS.md`, không còn code thử nghiệm hay log gỡ lỗi.
- [ ] Đã viết hoặc cập nhật test và toàn bộ test đang xanh (`./gradlew build` cho backend, `npm run lint` và `npm run build` cho frontend).
- [ ] CI xanh trên PR.
- [ ] PR được ít nhất 1 người review và approve, mọi góp ý đã được giải quyết.
- [ ] API mới hoặc thay đổi API đã được ghi vào tài liệu hoặc Swagger; thay đổi CSDL có script migration.
- [ ] Giao diện dùng tiếng Việt có dấu và hiển thị đúng trên trình duyệt phổ biến.
- [ ] Không có bí mật (mật khẩu, token, file `.env`) trong commit.
- [ ] Chạy được bằng `docker compose up -d` nếu thay đổi ảnh hưởng tới đóng gói hoặc cấu hình.
- [ ] Đã merge (squash) vào `develop` và thẻ trên board được chuyển sang Done.

## 8. Thiết lập môi trường

Xem mục "Chạy dự án" trong [README.md](README.md). Các tài liệu kỹ thuật nằm trong thư mục [docs/](docs/README.md).
