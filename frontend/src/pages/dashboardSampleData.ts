// Dữ liệu mẫu tĩnh cho Dashboard ở Sprint 1.
// Sẽ được thay bằng dữ liệu thật từ API báo cáo/dashboard ở Sprint 3 (FR23, FR24),
// khi đó mở rộng thành đủ các thẻ KPI theo đặc tả.

export interface KpiSample {
  key: string
  title: string
  value: number
  kind: 'count' | 'currency'
  note: string
}

export const kpiSamples: KpiSample[] = [
  {
    key: 'pending-sales',
    title: 'Đơn bán chờ xử lý',
    value: 18,
    kind: 'count',
    note: '5 đơn đã quá hạn giao',
  },
  {
    key: 'pending-purchases',
    title: 'Đơn mua chờ duyệt',
    value: 7,
    kind: 'count',
    note: '2 đơn có giá trị trên 100 triệu đồng',
  },
  {
    key: 'receivables',
    title: 'Công nợ phải thu',
    value: 248_500_000,
    kind: 'currency',
    note: '12 khách hàng còn dư nợ',
  },
  {
    key: 'payables',
    title: 'Công nợ phải trả',
    value: 132_800_000,
    kind: 'currency',
    note: '4 nhà cung cấp đến hạn trong tuần',
  },
]

/** Giá trị mua vào và bán ra theo tháng, đơn vị triệu đồng. */
export const monthlyTrade = [
  { month: 'T5', purchase: 420, sales: 510 },
  { month: 'T6', purchase: 385, sales: 470 },
  { month: 'T7', purchase: 510, sales: 560 },
  { month: 'T8', purchase: 465, sales: 610 },
  { month: 'T9', purchase: 540, sales: 590 },
  { month: 'T10', purchase: 480, sales: 655 },
]

/** Mặt hàng bán chạy: số lượng bán ra trong tháng. */
export const trendingItems = [
  { name: 'Gạo thơm túi 5kg', value: 420, fill: '#1677ff' },
  { name: 'Dầu ăn chai 1 lít', value: 310, fill: '#52c41a' },
  { name: 'Nước mắm chai 500ml', value: 240, fill: '#faad14' },
  { name: 'Đường túi 1kg', value: 180, fill: '#eb2f96' },
  { name: 'Sữa tươi thùng 48 hộp', value: 130, fill: '#722ed1' },
]
