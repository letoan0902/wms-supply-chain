import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { Customer, CustomerCreditCheckResponse, CustomerQueryParams } from './types'

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
