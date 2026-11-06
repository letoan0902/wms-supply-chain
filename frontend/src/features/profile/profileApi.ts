import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'

export interface UserProfile {
  id: number
  username: string
  email: string
  fullName: string
  enabled: boolean
  roles: string[]
  warehouseIds: number[]
  createdAt?: string
  updatedAt?: string
}

export interface UpdateProfilePayload {
  fullName: string
  email: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/**
 * Lấy hồ sơ người dùng đang đăng nhập (self-service).
 */
export async function fetchProfile(): Promise<UserProfile> {
  const res = await api.get<ApiResponse<UserProfile>>('/auth/users/profile')
  return res.data.data
}

/**
 * Cập nhật họ tên và email của người dùng đang đăng nhập.
 */
export async function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  const res = await api.put<ApiResponse<UserProfile>>('/auth/users/profile', payload)
  return res.data.data
}

/**
 * Đổi mật khẩu tài khoản người dùng đang đăng nhập.
 */
export async function changePassword(payload: ChangePasswordPayload): Promise<string> {
  const res = await api.post<ApiResponse<string>>('/auth/users/change-password', payload)
  return res.data.data
}
