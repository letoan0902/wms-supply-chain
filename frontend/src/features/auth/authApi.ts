import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { LoginRequest, LoginResult } from '../../types/auth'

/** Gọi POST /api/auth/login, trả về token và thông tin người dùng. */
export async function login(payload: LoginRequest): Promise<LoginResult> {
  const response = await api.post<ApiResponse<LoginResult>>('/auth/login', payload)
  return response.data.data
}
