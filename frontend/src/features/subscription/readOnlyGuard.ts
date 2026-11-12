/**
 * Chế độ chỉ đọc khi thuê bao hết hạn: một cơ chế dùng chung cho mọi màn hình, không phải sửa từng màn hình.
 *
 * Khi bật, thân trang có lớp {@link READ_ONLY_BODY_CLASS}; CSS trong src/index.css làm mờ các nút ghi và
 * một bộ lắng nghe click ở pha capture trên `document` chặn chúng (kể cả nút trong Modal, Drawer, Popconfirm
 * vì các lớp phủ này cũng nằm trong `document`). Nút ghi được nhận diện bằng một trong các dấu hiệu:
 * - nút hoặc mục menu chứa biểu tượng thêm, sửa, xóa, lưu, xác nhận, hủy, từ chối, thu tiền, nhận hàng,
 *   phát hành hóa đơn của Ant Design (danh sách {@link WRITE_ICON_CLASSES});
 * - nút xác nhận của Popconfirm (mọi Popconfirm trong hệ thống đều dùng cho thao tác ghi);
 * - phần tử nằm trong vùng có thuộc tính `data-write-action` (màn hình mới có thể tự đánh dấu).
 * Vùng có lớp {@link READ_ONLY_ALLOWED_CLASS} hoặc thuộc tính `data-read-only-allowed` không bị chặn
 * (menu tài khoản, trang Gói dịch vụ và thanh toán vẫn dùng được khi hết hạn), cũng như nút xóa nội dung
 * của ô nhập, ô chọn, bộ lọc và phân trang.
 *
 * Những nút ghi không mang dấu hiệu trên vẫn được máy chủ chặn bằng 403 SUBSCRIPTION_EXPIRED và mở hộp thoại
 * dẫn tới trang Gói dịch vụ.
 */

export const READ_ONLY_BODY_CLASS = 'wms-read-only'
export const READ_ONLY_ALLOWED_CLASS = 'wms-read-only-allowed'

/** Lớp biểu tượng Ant Design đánh dấu thao tác ghi; phải trùng danh sách trong src/index.css. */
export const WRITE_ICON_CLASSES = [
  'anticon-plus',
  'anticon-edit',
  'anticon-delete',
  'anticon-save',
  'anticon-check-circle',
  'anticon-stop',
  'anticon-close-circle',
  'anticon-dollar',
  'anticon-dollar-circle',
  'anticon-inbox',
  'anticon-file-done',
]

const CLICKABLE_SELECTOR = 'button, a, [role="button"], [role="menuitem"], .ant-btn'
const WRITE_ICON_SELECTOR = WRITE_ICON_CLASSES.map((name) => `.${name}`).join(', ')
// Ô nhập, ô chọn, bộ lọc, phân trang: biểu tượng xóa nội dung (close-circle) của chúng không phải thao tác ghi
const ALLOWED_SELECTOR = [
  `.${READ_ONLY_ALLOWED_CLASS}`,
  '[data-read-only-allowed]',
  '.ant-input-affix-wrapper',
  '.ant-select',
  '.ant-picker',
  '.ant-input-number',
  '.ant-pagination',
  '.ant-table-filter-dropdown',
].join(', ')

/** Phần tử bấm được chứa `target` nếu đó là một thao tác ghi cần chặn ở chế độ chỉ đọc, ngược lại `null`. */
export function findBlockedWriteTarget(target: EventTarget | null): Element | null {
  if (!(target instanceof Element)) return null
  const clickable = target.closest(CLICKABLE_SELECTOR)
  if (!clickable) return null
  if (clickable.closest(ALLOWED_SELECTOR)) return null
  if (clickable.closest('[data-write-action]')) return clickable
  if (clickable.closest('.ant-popconfirm-buttons') && clickable.classList.contains('ant-btn-primary')) {
    return clickable
  }
  if (clickable.querySelector(WRITE_ICON_SELECTOR)) return clickable
  return null
}

/**
 * Bật chế độ chỉ đọc trên toàn trang. `onBlocked` được gọi khi người dùng bấm một nút ghi.
 * Trả hàm tắt chế độ chỉ đọc.
 */
export function enableReadOnlyGuard(onBlocked: () => void): () => void {
  const handleClick = (event: MouseEvent) => {
    if (!findBlockedWriteTarget(event.target)) return
    event.preventDefault()
    event.stopPropagation()
    onBlocked()
  }
  document.body.classList.add(READ_ONLY_BODY_CLASS)
  document.addEventListener('click', handleClick, true)
  return () => {
    document.body.classList.remove(READ_ONLY_BODY_CLASS)
    document.removeEventListener('click', handleClick, true)
  }
}
