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
