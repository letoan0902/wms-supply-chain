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
