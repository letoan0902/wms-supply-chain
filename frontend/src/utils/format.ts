const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

const numberFormatter = new Intl.NumberFormat('vi-VN')

/** Định dạng tiền Việt Nam đồng, ví dụ: 248.500.000 ₫ */
export function formatCurrency(value: number): string {
  return currencyFormatter.format(value)
}

/** Định dạng số theo kiểu Việt Nam, ví dụ: 1.280 */
export function formatNumber(value: number): string {
  return numberFormatter.format(value)
}

/** Định dạng ngày giờ theo kiểu Việt Nam, ví dụ: 24/10/2026 14:30 */
export function formatDateTime(isoString?: string | null): string {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    if (isNaN(d.getTime())) return '—'
    return new Intl.DateTimeFormat('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  } catch {
    return '—'
  }
}

