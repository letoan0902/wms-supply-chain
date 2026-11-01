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
