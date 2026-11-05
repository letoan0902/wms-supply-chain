import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  Customer,
  CustomerCreditCheckResponse,
  CustomerQueryParams,
  CustomerTransaction,
  RecordCustomerTransactionPayload,
} from './types'

/**
 * Gọi API danh sách khách hàng có phân trang & tìm kiếm.
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
