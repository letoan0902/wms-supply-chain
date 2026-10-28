# Hướng dẫn sử dụng Jira cho dự án WMS (Nhóm 3)

Board: https://plinhle26.atlassian.net/jira/software/projects/TASK/list
Repo: https://github.com/letoan0902/wms-supply-chain
Master Plan: Template-Technical1.xlsx, sheet "3. Master Plan" (Phạm Phương Anh lập). Jira được giữ khớp với Master Plan theo bảng JIRA_MAPPING.md.

## 1. Cấu trúc công việc

- Epic (9): một Epic cho mỗi phân hệ, tương ứng mục I đến IX của Master Plan.
- Task / Story (21): tương ứng WMS-01 đến WMS-21.
- Subtask (86): tương ứng WMS-xx.y, là đơn vị nhỏ nhất, mỗi subtask có một người phụ trách và một hạn hoàn thành.
- Tiêu đề mỗi issue bắt đầu bằng mã Master Plan: "EPIC 1: ...", "WMS-01: ...", "WMS-01.1 [DevOps] ...". Tìm nhanh bằng ô Search với mã này.
- Mỗi issue có khối "Mã Master Plan" ở đầu phần mô tả: mã, tên theo Master Plan, nội dung, phụ trách / phối hợp, kế hoạch (ngày bắt đầu, kết thúc, số ngày) và giai đoạn.

## 2. Nhãn (Labels)

- Giai đoạn: Chuan-bi, Sprint-1, Sprint-2, Sprint-3, Sprint-4, UAT-Deploy.
- Vai trò: Backend, Frontend, DevOps, QA, Fullstack, Scrum.

Lọc theo nhãn trên màn hình List bằng nút Filter, hoặc JQL: project = TASK AND labels = "Sprint-2".

## 3. Bộ lọc dùng chung (menu Filters, nhóm "WMS - ...")

| Bộ lọc | Nội dung |
|---|---|
| WMS - Việc của Lê Phú Toàn | Toàn bộ issue gán cho Toàn, xếp theo hạn |
| WMS - Việc của Lê Phương Linh | Toàn bộ issue gán cho Linh, xếp theo hạn |
| WMS - Việc của Trần Đăng Việt | Toàn bộ issue gán cho Việt, xếp theo hạn |
| WMS - Việc của Phạm Phương Anh | Toàn bộ issue gán cho Phương Anh, xếp theo hạn |
| WMS - Quá hạn chưa xong | Hạn đã qua nhưng chưa Done |
| WMS - Đến hạn trong 7 ngày tới | Việc cần hoàn thành trong tuần |
| WMS - Đang thực hiện | Trạng thái In Progress hoặc In Review |
| WMS - Toàn bộ theo trạng thái | Tất cả issue, nhóm theo trạng thái |

Trên màn hình List cũng lọc nhanh được theo Assignee (biểu tượng avatar), Status và sắp xếp cột Due date.

## 4. Trạng thái công việc

To Do -> In Progress -> In Review -> Done (Pending dùng khi bị chặn, ghi rõ lý do trong comment).

- Bắt đầu làm: chuyển In Progress.
- Đã tạo Pull Request: chuyển In Review, dán link PR vào comment.
- PR đã merge vào develop: người review chuyển Done.
- Task cha chuyển Done khi mọi subtask đã Done. Epic do TechLead đóng sau khi nghiệm thu phân hệ.

## 5. Quy trình làm việc hằng ngày

1. Mở bộ lọc "WMS - Việc của <tên mình>" để xem việc theo hạn. Chọn subtask của ngày hôm đó, chuyển In Progress.
2. Cập nhật mã nguồn: git checkout develop, git pull origin develop.
3. Tạo nhánh theo key Jira: git checkout -b feat/TASK-31-api-login-jwt (dạng feat|fix|docs/<KEY>-<mo-ta-ngan>).
4. Commit theo Conventional Commits, có key Jira trong nội dung, ví dụ: feat(auth): TASK-31 cấp JWT access va refresh token.
5. Đẩy nhánh, tạo Pull Request vào develop. Tiêu đề PR bắt đầu bằng key Jira, mô tả PR ghi "Closes TASK-31". Chuyển issue sang In Review.
6. Một thành viên khác review (yêu cầu 1 approve và CI xanh), người review merge và chuyển issue sang Done. Người làm xóa nhánh sau khi merge.
7. Cuối ngày (standup 20:30): mỗi người rà lại issue của mình, cập nhật trạng thái, ghi blocker vào comment.

Người review theo phân hệ: Backend inventory và warehouse do Lê Phương Linh, product và order do Trần Đăng Việt, auth và analytics do Phạm Phương Anh, hạ tầng, CI/CD và các PR còn lại do Lê Phú Toàn (TechLead).

## 6. Khi Master Plan thay đổi

Báo cho Lê Phú Toàn (phụ trách Jira). Jira sẽ được cập nhật lại từ file Master Plan bằng script docs/project-management/build_jira_plan.py, giữ nguyên key Jira hiện có.
