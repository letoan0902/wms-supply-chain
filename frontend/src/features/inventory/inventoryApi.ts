import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  AlertQuery,
  AtpItem,
  BalanceQuery,
  BalanceReport,
  LotQuery,
  LotRow,
  ProductBrief,
  StockAlert,
  StockAlertScanResult,
  StockAlertSummary,
} from './types'

/**
 * REST API của màn hình Tồn kho. Mọi đường dẫn đi qua gateway (`/api` là baseURL của `api`).
 */

/** Dòng tồn theo lô và ô kệ: GET /api/inventory/lots. */
export async function fetchLots(query: LotQuery): Promise<PageResponse<LotRow>> {
  const res = await api.get<ApiResponse<PageResponse<LotRow>>>('/inventory/lots', { params: query })
  return res.data.data
}

/**
 * Tồn theo hàng hóa và kho: báo cáo Xuất - Nhập - Tồn của đúng một ngày (hôm nay), gom theo sản phẩm và kho.
 * Tồn cuối kỳ của hôm nay là tồn thực tế hiện tại tính từ sổ kho; backend tự lọc theo phạm vi kho của người dùng.
 */
export async function fetchStockBalance(query: BalanceQuery, today: string): Promise<BalanceReport> {
  const res = await api.get<ApiResponse<BalanceReport>>('/inventory/reports/in-out-balance', {
    params: { ...query, fromDate: today, toDate: today, groupBy: 'PRODUCT_WAREHOUSE' },
  })
  return res.data.data
}

/** Tồn dùng được, đã giữ chỗ và khả dụng (ATP) của nhiều sản phẩm tại một kho: POST /api/inventory/stock/atp/batch. */
export async function fetchBatchAtp(warehouseId: number, productIds: number[]): Promise<AtpItem[]> {
  const res = await api.post<ApiResponse<{ warehouseId: number | null; items: AtpItem[] }>>(
    '/inventory/stock/atp/batch',
    { warehouseId, productIds },
  )
  return res.data.data?.items ?? []
}

/** Một trang cảnh báo: GET /api/inventory/alerts. */
export async function fetchAlerts(query: AlertQuery): Promise<PageResponse<StockAlert>> {
  const res = await api.get<ApiResponse<PageResponse<StockAlert>>>('/inventory/alerts', { params: query })
  return res.data.data
}

/** Số trang tối đa đọc khi lấy cảnh báo để gắn thẻ (mỗi trang 100 cảnh báo, giới hạn của backend). */
export const TAG_ALERT_PAGE_SIZE = 100
export const TAG_ALERT_MAX_PAGES = 5

/**
 * Cảnh báo để gắn thẻ cho bảng đang xem, theo đúng bộ lọc kho (và sản phẩm, từ khóa nếu có): đọc lần lượt các trang
 * 100 dòng tới khi `last = true`, tối đa {@link TAG_ALERT_MAX_PAGES} trang. Số request không phụ thuộc số dòng của bảng.
 */
export async function fetchAlertsForTags(
  filter: Pick<AlertQuery, 'warehouseId' | 'productId' | 'keyword'>,
): Promise<{ alerts: StockAlert[]; truncated: boolean }> {
  const alerts: StockAlert[] = []
  for (let page = 0; page < TAG_ALERT_MAX_PAGES; page += 1) {
    const data = await fetchAlerts({ ...filter, page, size: TAG_ALERT_PAGE_SIZE })
    alerts.push(...(data.content ?? []))
    if (data.last || (data.content ?? []).length === 0) {
      return { alerts, truncated: false }
    }
  }
  return { alerts, truncated: true }
}

/** Số cảnh báo theo loại và mức: GET /api/inventory/alerts/summary. */
export async function fetchAlertSummary(warehouseId?: number): Promise<StockAlertSummary> {
  const res = await api.get<ApiResponse<StockAlertSummary>>('/inventory/alerts/summary', {
    params: { warehouseId },
  })
  return res.data.data
}

/** Chạy ngay tác vụ quét cảnh báo của doanh nghiệp (chỉ ADMIN): POST /api/inventory/alerts/scan. */
export async function scanAlerts(): Promise<StockAlertScanResult> {
  const res = await api.post<ApiResponse<StockAlertScanResult>>('/inventory/alerts/scan')
  return res.data.data
}

// ===================== Thông tin sản phẩm (có bộ nhớ đệm) =====================

/** Thời gian giữ thông tin sản phẩm trong bộ nhớ đệm: tên, đơn vị, ngưỡng tồn ít khi đổi. */
const PRODUCT_CACHE_TTL_MS = 5 * 60 * 1000

interface CacheEntry {
  value: ProductBrief | null
  loadedAt: number
}

const productCache = new Map<number, CacheEntry>()
const inflight = new Map<number, Promise<ProductBrief | null>>()

async function loadProduct(id: number): Promise<ProductBrief | null> {
  try {
    const res = await api.get<ApiResponse<{
      id: number
      sku: string
      name: string
      uomCode?: string | null
      uomName?: string | null
      categoryName?: string | null
      safetyStock?: number | null
      reorderPoint?: number | null
    }>>(`/products/${id}`)
    const p = res.data.data
    const value: ProductBrief = {
      id: p.id,
      sku: p.sku,
      name: p.name,
      uomCode: p.uomCode ?? null,
      uomName: p.uomName ?? null,
      categoryName: p.categoryName ?? null,
      safetyStock: p.safetyStock ?? null,
      reorderPoint: p.reorderPoint ?? null,
    }
    productCache.set(id, { value, loadedAt: Date.now() })
    return value
  } catch {
    // Sản phẩm đã xóa hoặc product-service lỗi: bảng hiện mã sản phẩm thay cho tên, không làm hỏng cả trang
    productCache.set(id, { value: null, loadedAt: Date.now() })
    return null
  } finally {
    inflight.delete(id)
  }
}

/**
 * Thông tin sản phẩm cho các dòng đang hiển thị (GET /api/products/{id}, product-service không có API lấy theo
 * danh sách ID). Mỗi sản phẩm chỉ gọi một lần trong {@link PRODUCT_CACHE_TTL_MS}, nên làm mới thời gian thực không
 * gọi lại.
 */
export async function fetchProductBriefs(ids: readonly number[]): Promise<Map<number, ProductBrief>> {
  const now = Date.now()
  const unique = [...new Set(ids.filter((id) => Number.isInteger(id) && id > 0))]
  await Promise.all(
    unique.map((id) => {
      const cached = productCache.get(id)
      if (cached && now - cached.loadedAt < PRODUCT_CACHE_TTL_MS) return Promise.resolve(cached.value)
      let pending = inflight.get(id)
      if (!pending) {
        pending = loadProduct(id)
        inflight.set(id, pending)
      }
      return pending
    }),
  )
  const result = new Map<number, ProductBrief>()
  unique.forEach((id) => {
    const value = productCache.get(id)?.value
    if (value) result.set(id, value)
  })
  return result
}
