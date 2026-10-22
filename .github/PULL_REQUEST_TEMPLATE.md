<!--
Tiêu đề PR theo Conventional Commits, ví dụ: feat(inventory): thêm API tạo phiếu điều chuyển kho
PR phải tạo vào nhánh develop (riêng PR phát hành thì từ develop vào main).
Xem chi tiết tại CONTRIBUTING.md.
-->

## Mô tả

<!-- Thay đổi này làm gì và vì sao cần thiết? -->

## Loại thay đổi

- [ ] feat: tính năng mới
- [ ] fix: sửa lỗi
- [ ] refactor: tái cấu trúc, không đổi hành vi
- [ ] docs: tài liệu
- [ ] test: bổ sung hoặc sửa test
- [ ] ci, chore: CI, cấu hình, công việc phụ trợ

## Task liên quan

<!-- Mã task trên Trello/Jira và liên kết thẻ, ví dụ: WMS-12 -->

- Task:
- Mã yêu cầu chức năng (FRxx), nếu có:

## Phạm vi ảnh hưởng

- [ ] Backend (ghi rõ service):
- [ ] Frontend
- [ ] docker-compose, hạ tầng, CI
- [ ] Tài liệu
- [ ] Có thay đổi schema CSDL (đã kèm script migration)
- [ ] Có thay đổi API (đã cập nhật tài liệu hoặc Swagger)

## Cách kiểm thử

<!-- Các bước để reviewer kiểm tra lại, hoặc lệnh test đã chạy -->

1.
2.

## Ảnh chụp màn hình hoặc kết quả

<!-- Với thay đổi giao diện hoặc API, đính kèm ảnh hoặc ví dụ request/response. Bỏ trống nếu không áp dụng. -->

## Checklist trước khi yêu cầu review

- [ ] Nhánh đặt tên đúng quy ước và đã cập nhật từ develop mới nhất
- [ ] Commit theo Conventional Commits
- [ ] Đã tự review diff của mình
- [ ] Đã build và chạy test ở máy cục bộ (`./gradlew build` và/hoặc `npm run lint` và `npm run build`)
- [ ] CI xanh
- [ ] Đã thêm hoặc cập nhật test cho phần thay đổi
- [ ] Đã cập nhật tài liệu liên quan (README, docs/)
- [ ] Không commit bí mật (mật khẩu, token, file .env)
- [ ] Chữ hiển thị trên giao diện bằng tiếng Việt có dấu, định danh trong code bằng tiếng Anh

## Ghi chú cho reviewer

<!-- Điểm cần lưu ý, rủi ro, việc làm tiếp theo -->
