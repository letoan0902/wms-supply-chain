import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  AdminUpdateUserPayload,
  AssignWarehousesPayload,
  CreateUserPayload,
  UserDetail,
  UserFilterParams,
} from './types'

/**
 * REST API client cho Phân hệ Quản trị Người dùng & Phân quyền (RBAC - FR01, FR02).
 * Đi qua API Gateway (/api/auth/users/**) tới auth-service.
 */

export async function fetchUsers(params?: UserFilterParams): Promise<PageResponse<UserDetail>> {
  const res = await api.get<ApiResponse<PageResponse<UserDetail>>>('/auth/users', { params })
  return res.data.data
}

export async function fetchUserById(id: number): Promise<UserDetail> {
  const res = await api.get<ApiResponse<UserDetail>>(`/auth/users/${id}`)
  return res.data.data
}

export async function createUser(payload: CreateUserPayload): Promise<UserDetail> {
  const res = await api.post<ApiResponse<UserDetail>>('/auth/users', payload)
  return res.data.data
}

export async function updateUser(id: number, payload: AdminUpdateUserPayload): Promise<UserDetail> {
  const res = await api.put<ApiResponse<UserDetail>>(`/auth/users/${id}`, payload)
  return res.data.data
}

export async function updateUserStatus(
  id: number,
  enabled: boolean,
  reason?: string
): Promise<UserDetail> {
  const res = await api.patch<ApiResponse<UserDetail>>(`/auth/users/${id}/status`, {
    enabled,
    reason,
    reasonChange: reason,
  })
  return res.data.data
}

export async function assignWarehouses(id: number, warehouseIds: number[]): Promise<UserDetail> {
  const payload: AssignWarehousesPayload = { warehouseIds }
  const res = await api.put<ApiResponse<UserDetail>>(`/auth/users/${id}/warehouses`, payload)
  return res.data.data
}
