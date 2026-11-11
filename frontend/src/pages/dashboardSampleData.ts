/**
 * Dữ liệu mẫu cho Dashboard và Phân hệ Báo cáo & Thống kê (UC-10).
 * Phân cấp theo RBAC và Phạm vi kho (SRS Mục 2.5 & Mục 4.2).
 */

export interface KpiSample {
  key: string
  title: string
  value: number
  kind: 'count' | 'currency' | 'percent'
  note: string
}

/** 1. Chỉ số Tài chính & Toàn cục (Dành cho Quản trị viên & Kế toán - SRS 2.5.1 & 2.5.2) */
export const financialKpiSamples: KpiSample[] = [
  {
    key: 'receivables',
    title: 'Công nợ phải thu',
    value: 248_500_000,
    kind: 'currency',
    note: '12 khách hàng còn dư nợ đến hạn',
  },
  {
    key: 'payables',
    title: 'Công nợ phải trả',
    value: 132_800_000,
    kind: 'currency',
    note: '4 nhà cung cấp đến hạn trong tuần',
  },
  {
    key: 'inventory-val',
    title: 'Giá trị tồn kho',
    value: 1_420_000_000,
    kind: 'currency',
    note: 'Tính theo giá vốn bình quân',
  },
  {
    key: 'monthly-revenue',
    title: 'Doanh số xuất bán tháng',
    value: 655_000_000,
    kind: 'currency',
    note: 'Tăng 11.2% so với tháng trước',
  },
]

/** 2. Chỉ số Vận hành & Vật lý (Dành cho Quản lý kho & Nhân viên kho - SRS 2.5.2) */
export const operationsKpiSamples: KpiSample[] = [
  {
    key: 'physical-stock',
    title: 'Tồn kho khả dụng',
    value: 24_850,
    kind: 'count',
    note: 'Sản phẩm lưu tại các vị trí ô/kệ',
  },
  {
    key: 'pending-sales',
    title: 'Đơn bán chờ xuất kho (SO)',
    value: 18,
    kind: 'count',
    note: '5 đơn cần điều phối lấy hàng gấp',
  },
  {
    key: 'pending-purchases',
    title: 'Đơn mua chờ tiếp nhận (PO)',
    value: 7,
    kind: 'count',
    note: '3 lô hàng dự kiến đến trong hôm nay',
  },
  {
    key: 'utilization-rate',
    title: 'Tỷ lệ lấp đầy kho',
    value: 78.5,
    kind: 'percent',
    note: '184 / 234 vị trí ô kệ đang sử dụng',
  },
]

/** Danh sách cảnh báo kho thông minh (Theo Mục 4.2 của SRS: WARN-01, WARN-02, WARN-03, ERR-04) */
export interface StockAlertItem {
  id: string
  code: 'WARN-01' | 'WARN-02' | 'WARN-03' | 'ERR-04'
  title: string
  sku: string
  productName: string
  warehouseId: number
  warehouseName: string
  locationCode?: string
  batchCode?: string
  expiryDate?: string
  currentQty: number
  thresholdQty?: number
  level: 'warning' | 'error' | 'info'
  message: string
  actionNote: string
}

export const stockAlertSamples: StockAlertItem[] = [
  {
    id: 'alt-01',
    code: 'WARN-02',
    title: 'Khẩn cấp: Tồn chạm mức an toàn',
    sku: 'SKU-0012',
    productName: 'Nước tương thượng hạng 500ml',
    warehouseId: 1,
    warehouseName: 'Kho Tổng Miền Nam (Tân Bình)',
    locationCode: 'WH1-ZA-R01-B02',
    currentQty: 12,
    thresholdQty: 50,
    level: 'error',
    message: 'Tồn kho thực tế (12 chai) đã giảm dưới mức an toàn tối thiểu (50 chai)!',
    actionNote: 'Đề xuất lập ngay Đơn mua hàng (PO) bổ sung nguồn cung.',
  },
  {
    id: 'alt-02',
    code: 'WARN-03',
    title: 'Lô hàng cận hạn sử dụng (FEFO)',
    sku: 'SKU-0045',
    productName: 'Sữa tươi tiệt trùng nguyên kem 1L',
    warehouseId: 1,
    warehouseName: 'Kho Tổng Miền Nam (Tân Bình)',
    locationCode: 'WH1-ZB-R02-B01',
    batchCode: 'BAT-2026-088',
    expiryDate: '2026-11-25',
    currentQty: 180,
    level: 'warning',
    message: 'Lô hàng BAT-2026-088 chỉ còn 20 ngày trước hạn sử dụng (25/11/2026).',
    actionNote: 'Ưu tiên xuất bán trước theo quy tắc FEFO.',
  },
  {
    id: 'alt-03',
    code: 'WARN-01',
    title: 'Chạm ngưỡng đặt hàng lại',
    sku: 'SKU-0078',
    productName: 'Dầu ăn đậu nành chai 1 lít',
    warehouseId: 2,
    warehouseName: 'Kho Phân Phối Miền Bắc',
    locationCode: 'WH2-ZA-R03-B04',
    currentQty: 65,
    thresholdQty: 80,
    level: 'warning',
    message: 'Số lượng tồn khả dụng (65 chai) đạt ngưỡng đặt hàng lại (80 chai).',
    actionNote: 'Chuẩn bị kế hoạch nhập hàng theo hợp đồng nhà cung cấp.',
  },
  {
    id: 'alt-04',
    code: 'ERR-04',
    title: 'Vị trí kệ đóng băng kiểm kê',
    sku: 'SKU-0091',
    productName: 'Hạt nêm xương hầm gói 400g',
    warehouseId: 1,
    warehouseName: 'Kho Tổng Miền Nam (Tân Bình)',
    locationCode: 'WH1-ZC-R01-B03',
    currentQty: 320,
    level: 'info',
    message: 'Vị trí WH1-ZC-R01-B03 đang ở trạng thái FROZEN phục vụ phiên kiểm kê định kỳ.',
    actionNote: 'Tạm khóa xuất/nhập tại vị trí này cho tới khi hoàn tất kiểm đếm.',
  },
]

/** Biểu đồ Mua vào - Bán ra theo tháng (Giá trị tiền tệ - triệu đồng) */
export const monthlyTradeFinancial = [
  { month: 'T5', purchase: 420, sales: 510 },
  { month: 'T6', purchase: 385, sales: 470 },
  { month: 'T7', purchase: 510, sales: 560 },
  { month: 'T8', purchase: 465, sales: 610 },
  { month: 'T9', purchase: 540, sales: 590 },
  { month: 'T10', purchase: 480, sales: 655 },
]

/** Biểu đồ Vận hành Kho theo tháng (Số lượng sản phẩm nhập - xuất) */
export const monthlyTradeOperations = [
  { month: 'T5', purchase: 1420, sales: 1650 },
  { month: 'T6', purchase: 1280, sales: 1540 },
  { month: 'T7', purchase: 1800, sales: 1950 },
  { month: 'T8', purchase: 1620, sales: 2100 },
  { month: 'T9', purchase: 1910, sales: 2050 },
  { month: 'T10', purchase: 1750, sales: 2320 },
]

/** Trending Items: Mặt hàng bán chạy nhất */
export const trendingItems = [
  { name: 'Gạo thơm túi 5kg', value: 420, fill: '#1677ff' },
  { name: 'Dầu ăn chai 1 lít', value: 310, fill: '#52c41a' },
  { name: 'Nước mắm chai 500ml', value: 240, fill: '#faad14' },
  { name: 'Đường túi 1kg', value: 180, fill: '#eb2f96' },
  { name: 'Sữa tươi thùng 48 hộp', value: 130, fill: '#0284c7' },
]
