import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  CreateCustomerPayload,
  Customer,
  CustomerCreditCheckResponse,
  CustomerDebtSummary,
  CustomerQueryParams,
  CustomerTransaction,
  RecordCustomerTransactionPayload,
  UpdateCustomerPayload,
} from './types'

/**
 * Gọi API danh sách khách hàng có phân trang & tìm kiếm (FR14).
 */
export async function fetchCustomers(
  params: CustomerQueryParams = {}
): Promise<PageResponse<Customer>> {
  const res = await api.get<ApiResponse<PageResponse<Customer>>>('/orders/customers', {
    params: {
      keyword: params.keyword,
      type: params.type,
      active: params.active,
      debtLocked: params.debtLocked,
      overLimit: params.overLimit,
      page: params.page ?? 0,
      size: params.size ?? 50,
    },
  })
  return res.data.data
}

/**
 * Gọi API kiểm tra hạn mức công nợ (Credit Check) cho khách hàng (FR14).
 */
export async function checkCustomerCredit(
  customerId: number,
  orderAmount?: number
): Promise<CustomerCreditCheckResponse> {
  const res = await api.get<ApiResponse<CustomerCreditCheckResponse>>(
    `/orders/customers/${customerId}/credit-check`,
    {
      params: { orderAmount },
    }
  )
  return res.data.data
}

/**
 * Chi tiết một khách hàng theo ID.
 */
export async function fetchCustomerById(customerId: number): Promise<Customer> {
  const res = await api.get<ApiResponse<Customer>>(`/orders/customers/${customerId}`)
  return res.data.data
}

/**
 * Chi tiết một khách hàng theo Mã code duy nhất.
 */
export async function fetchCustomerByCode(code: string): Promise<Customer> {
  const res = await api.get<ApiResponse<Customer>>(`/orders/customers/code/${code}`)
  return res.data.data
}

/**
 * Thêm mới hồ sơ khách hàng / đại lý & cấu hình hạn mức nợ (FR14).
 */
export async function createCustomer(payload: CreateCustomerPayload): Promise<Customer> {
  const res = await api.post<ApiResponse<Customer>>('/orders/customers', payload)
  return res.data.data
}

/**
 * Cập nhật hồ sơ khách hàng / đại lý & hạn mức nợ (FR14).
 */
export async function updateCustomer(
  id: number,
  payload: UpdateCustomerPayload
): Promise<Customer> {
  const res = await api.put<ApiResponse<Customer>>(`/orders/customers/${id}`, payload)
  return res.data.data
}

/**
 * Xóa mềm khách hàng (chuyển sang ngưng hoạt động active = false).
 */
export async function deleteCustomer(id: number): Promise<void> {
  await api.delete<ApiResponse<void>>(`/orders/customers/${id}`)
}

/**
 * Lấy lịch sử giao dịch công nợ & Hóa đơn gần đây của khách hàng (FR14, FR18).
 */
export async function fetchCustomerTransactions(
  customerId: number,
  params: { page?: number; size?: number } = {}
): Promise<PageResponse<CustomerTransaction>> {
  const res = await api.get<ApiResponse<PageResponse<CustomerTransaction>>>(
    `/orders/customers/${customerId}/transactions`,
    {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 50,
      },
    }
  )
  return res.data.data
}

/**
 * Ghi nhận phát sinh giao dịch công nợ (Tạo Hóa đơn bán SALES_INVOICE, Thanh toán PAYMENT... - FR18).
 */
export async function recordCustomerTransaction(
  customerId: number,
  payload: RecordCustomerTransactionPayload
): Promise<CustomerTransaction> {
  const res = await api.post<ApiResponse<CustomerTransaction>>(
    `/orders/customers/${customerId}/transactions`,
    payload
  )
  return res.data.data
}

/**
 * Báo cáo tổng hợp tình hình công nợ toàn bộ khách hàng (FR14).
 */
export async function fetchCustomerSummary(): Promise<CustomerDebtSummary> {
  const res = await api.get<ApiResponse<CustomerDebtSummary>>('/orders/customers/summary')
  return res.data.data
}
