import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { CreatePaymentRequest, PaymentInfo } from './types'

/**
 * POST /api/auth/payments (ADMIN): tạo giao dịch PENDING và liên kết thanh toán (PayOS thật, hoặc trang giả lập
 * `/payment/mock` khi máy chủ đặt PAYOS_MOCK=true). Không gửi số tiền: máy chủ lấy giá từ bảng gói.
 */
export async function createPayment(payload: CreatePaymentRequest): Promise<PaymentInfo> {
  const response = await api.post<ApiResponse<PaymentInfo>>('/auth/payments', payload)
  return response.data.data
}

/** GET /api/auth/payments/{orderCode} (ADMIN): trạng thái một giao dịch; đơn của doanh nghiệp khác trả 404. */
export async function fetchPayment(orderCode: number): Promise<PaymentInfo> {
  const response = await api.get<ApiResponse<PaymentInfo>>(`/auth/payments/${orderCode}`)
  return response.data.data
}

/** POST /api/auth/payments/{orderCode}/cancel (ADMIN): PENDING thành CANCELLED; trạng thái khác trả 409. */
export async function cancelPayment(orderCode: number): Promise<PaymentInfo> {
  const response = await api.post<ApiResponse<PaymentInfo>>(`/auth/payments/${orderCode}/cancel`)
  return response.data.data
}

/**
 * POST /api/auth/payments/{orderCode}/mock-confirm (ADMIN, chỉ khi PAYOS_MOCK=true): xác nhận thanh toán giả lập,
 * máy chủ xử lý như một webhook hợp lệ. Tắt giả lập thì trả 404.
 */
export async function mockConfirmPayment(orderCode: number): Promise<PaymentInfo> {
  const response = await api.post<ApiResponse<PaymentInfo>>(`/auth/payments/${orderCode}/mock-confirm`)
  return response.data.data
}

/** GET /api/auth/payments?page=&size= (ADMIN): lịch sử giao dịch, mới nhất trước, `page` tính từ 0. */
export async function fetchPayments(page: number, size: number): Promise<PageResponse<PaymentInfo>> {
  const response = await api.get<ApiResponse<PageResponse<PaymentInfo>>>('/auth/payments', {
    params: { page, size },
  })
  return response.data.data
}
