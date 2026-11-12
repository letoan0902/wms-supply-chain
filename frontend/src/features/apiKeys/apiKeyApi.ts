import axios from 'axios'
import { api } from '../../services/api'
import type { ApiErrorResponse, ApiResponse } from '../../types/api'
import type { ApiKeyItem, CreateApiKeyPayload, CreatedApiKey } from './types'

/**
 * REST API quản lý API key cho hệ thống ngoài (auth-service, /api/auth/api-keys). Chỉ ADMIN gọi được.
 */

export async function fetchApiKeys(): Promise<ApiKeyItem[]> {
  const res = await api.get<ApiResponse<ApiKeyItem[]>>('/auth/api-keys')
  return res.data.data ?? []
}

export async function createApiKey(payload: CreateApiKeyPayload): Promise<CreatedApiKey> {
  const res = await api.post<ApiResponse<CreatedApiKey>>('/auth/api-keys', payload)
  return res.data.data
}

export async function revokeApiKey(id: number): Promise<ApiKeyItem> {
  const res = await api.delete<ApiResponse<ApiKeyItem>>(`/auth/api-keys/${id}`)
  return res.data.data
}

/** Mã lỗi (`code`) trong phản hồi lỗi của backend, ví dụ PLAN_LIMIT_EXCEEDED. */
export function getApiErrorCode(error: unknown): string | undefined {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.code
  }
  return undefined
}
