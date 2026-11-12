import axios from 'axios'
import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { LoginRequest, LoginResult, RefreshTokenResponse } from '../../types/auth'

/** Gọi POST /api/auth/login, trả về token và thông tin người dùng. */
export async function login(payload: LoginRequest): Promise<LoginResult> {
  const response = await api.post<ApiResponse<LoginResult>>('/auth/login', payload)
  return response.data.data
}

/** Gọi POST /api/auth/refresh để làm mới Access Token bằng Refresh Token (dùng axios độc lập). */
export async function refreshAccessToken(refreshToken: string): Promise<RefreshTokenResponse> {
  const response = await axios.post<ApiResponse<RefreshTokenResponse>>(
    `${api.defaults.baseURL || '/api'}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } }
  )
  return response.data.data
}

/** Gọi POST /api/auth/logout để vô hiệu hóa token và xóa session phía server. */
export async function logout(): Promise<void> {
  await api.post<ApiResponse<string>>('/auth/logout')
}

/** Kết quả POST /api/auth/companies/activate (vn.wms.auth.account.dto.ActivationResponse). */
export interface ActivationResult {
  companyCode: string
  companyName: string
  status: string
  activatedAt: string
  /** Tên đăng nhập của quản trị viên nhận liên kết, dùng điền sẵn ở trang đăng nhập */
  username?: string | null
}

/** Kích hoạt doanh nghiệp bằng mã trong liên kết email. Mã sai, đã dùng hoặc hết hạn: lỗi TOKEN_INVALID. */
export async function activateCompany(token: string): Promise<ActivationResult> {
  const response = await api.post<ApiResponse<ActivationResult>>('/auth/companies/activate', { token })
  return response.data.data
}

/** Gửi lại email kích hoạt. Luôn thành công với cùng một thông báo, trả về thông báo đó. */
export async function resendActivation(email: string): Promise<string> {
  const response = await api.post<ApiResponse>('/auth/companies/resend-activation', { email })
  return response.data.message
}

/** Quên mật khẩu: yêu cầu gửi liên kết đặt lại. Luôn thành công với cùng một thông báo, trả về thông báo đó. */
export async function forgotPassword(email: string): Promise<string> {
  const response = await api.post<ApiResponse>('/auth/password/forgot', { email })
  return response.data.message
}

/** Đặt mật khẩu mới bằng mã trong liên kết email. Mã không hợp lệ: lỗi TOKEN_INVALID. */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await api.post<ApiResponse>('/auth/password/reset', { token, newPassword })
}

/** Mã lỗi (`code`) trong phản hồi lỗi của backend, ví dụ TOKEN_INVALID, COMPANY_INACTIVE. */
export function getAuthErrorCode(error: unknown): string | undefined {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error.response?.data?.code
  }
  return undefined
}

/** Trạng thái doanh nghiệp kèm lỗi COMPANY_INACTIVE (`data.companyStatus`), ví dụ PENDING khi chưa kích hoạt. */
export function getInactiveCompanyStatus(error: unknown): string | undefined {
  if (axios.isAxiosError<ApiResponse<{ companyStatus?: string } | undefined>>(error)) {
    return error.response?.data?.data?.companyStatus
  }
  return undefined
}
