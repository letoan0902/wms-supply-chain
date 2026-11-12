const notificationTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Asia/Ho_Chi_Minh',
})

/**
 * Định dạng thời điểm của thông báo (ISO-8601 UTC) theo giờ Việt Nam dạng dd/MM HH:mm, ví dụ: 05/11 15:00.
 * Trả về chuỗi rỗng nếu giá trị không hợp lệ.
 */
export function formatNotificationTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  // Ghép từ từng phần vì thứ tự mặc định của vi-VN là "HH:mm dd-MM"
  const parts = Object.fromEntries(
    notificationTimeFormatter.formatToParts(date).map((part) => [part.type, part.value]),
  )
  return `${parts.day}/${parts.month} ${parts.hour}:${parts.minute}`
}

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

/**
 * Khoảng thời gian từ `value` (ISO-8601 hoặc Date) tới `now` (mili giây) theo kiểu "vừa xong", "5 phút trước",
 * "3 giờ trước", "2 ngày trước"; từ 7 ngày trở lên thì trả ngày giờ dạng dd/MM HH:mm.
 * Trả về chuỗi rỗng nếu giá trị không hợp lệ.
 */
export function formatRelativeTime(value: string | Date, now: number): string {
  const date = typeof value === 'string' ? new Date(value) : value
  const time = date.getTime()
  if (Number.isNaN(time)) return ''
  const diff = Math.max(0, now - time)
  if (diff < MINUTE_MS) return 'vừa xong'
  if (diff < HOUR_MS) return `${Math.floor(diff / MINUTE_MS)} phút trước`
  if (diff < DAY_MS) return `${Math.floor(diff / HOUR_MS)} giờ trước`
  if (diff < 7 * DAY_MS) return `${Math.floor(diff / DAY_MS)} ngày trước`
  return formatNotificationTime(date.toISOString())
}
