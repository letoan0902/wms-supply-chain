import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  AtpCheckRequest,
  AtpCheckResponse,
  CreateSalesOrderPayload,
  SalesOrder,
  SalesOrderQueryParams,
  SalesOrderSummary,
  StockAtpResponse,
  UpdateSalesOrderPayload,
} from './types'

/**
 * Danh sách đơn bán hàng (SO) có phân trang, tìm kiếm và lọc đa tiêu chí.
 */
export async function fetchSalesOrders(
  params: SalesOrderQueryParams = {}
): Promise<PageResponse<SalesOrder>> {
  const res = await api.get<ApiResponse<PageResponse<SalesOrder>>>('/orders/sales', {
    params: {
      keyword: params.keyword,
      customerId: params.customerId,
      warehouseId: params.warehouseId,
      status: params.status,
      fromOrderDate: params.fromOrderDate,
      toOrderDate: params.toOrderDate,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  })
  return res.data.data
}

/**
 * Báo cáo tổng quan thống kê đơn bán hàng (SO Metrics Summary).
 */
export async function fetchSalesOrderSummary(): Promise<SalesOrderSummary> {
  const res = await api.get<ApiResponse<SalesOrderSummary>>('/orders/sales/summary')
  return res.data.data
}

/**
 * Chi tiết đơn bán hàng theo ID.
 */
export async function getSalesOrderById(id: number): Promise<SalesOrder> {
  const res = await api.get<ApiResponse<SalesOrder>>(`/orders/sales/${id}`)
  return res.data.data
}

/**
 * Chi tiết đơn bán hàng theo mã SO.
 */
export async function getSalesOrderByNumber(soNumber: string): Promise<SalesOrder> {
  const res = await api.get<ApiResponse<SalesOrder>>(`/orders/sales/number/${encodeURIComponent(soNumber)}`)
  return res.data.data
}

/**
 * Lập mới đơn bán hàng (SO).
 */
export async function createSalesOrder(payload: CreateSalesOrderPayload): Promise<SalesOrder> {
  const res = await api.post<ApiResponse<SalesOrder>>('/orders/sales', payload)
  return res.data.data
}

/**
 * Cập nhật đơn bán hàng (khi đang ở DRAFT hoặc BACKORDER).
 */
export async function updateSalesOrder(id: number, payload: UpdateSalesOrderPayload): Promise<SalesOrder> {
  const res = await api.put<ApiResponse<SalesOrder>>(`/orders/sales/${id}`, payload)
  return res.data.data
}

/**
 * Xác nhận đơn bán hàng (DRAFT/BACKORDER -> CONFIRMED).
 */
export async function confirmSalesOrder(id: number): Promise<SalesOrder> {
  const res = await api.post<ApiResponse<SalesOrder>>(`/orders/sales/${id}/confirm`)
  return res.data.data
}

/**
 * Hủy đơn bán hàng.
 */
export async function cancelSalesOrder(id: number): Promise<SalesOrder> {
  const res = await api.post<ApiResponse<SalesOrder>>(`/orders/sales/${id}/cancel`)
  return res.data.data
}

/**
 * Xóa đơn bán hàng nháp/đã hủy.
 */
export async function deleteSalesOrder(id: number): Promise<void> {
  await api.delete<ApiResponse<void>>(`/orders/sales/${id}`)
}

/**
 * Kiểm tra tồn khả dụng ATP trước khi tạo đơn (check-atp).
 */
export async function checkAtp(request: AtpCheckRequest): Promise<AtpCheckResponse> {
  const res = await api.post<ApiResponse<AtpCheckResponse>>('/orders/sales/check-atp', request)
  return res.data.data
}

/**
 * Tra cứu tồn khả dụng ATP của một sản phẩm từ inventory-service.
 */
export async function getStockAtp(productId: number, warehouseId?: number): Promise<StockAtpResponse> {
  const res = await api.get<ApiResponse<StockAtpResponse>>('/inventory/stock/atp', {
    params: { productId, warehouseId },
  })
  return res.data.data
}
