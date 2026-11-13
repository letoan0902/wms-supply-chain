import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  AdminCompanyItem,
  ApproveCompanyPayload,
  CompanyFilterParams,
  RejectCompanyPayload,
  UpdateCompanyStatusPayload,
} from './types'

/**
 * REST API client cho Phân hệ Quản trị Doanh nghiệp & Phê duyệt SaaS.
 * Đi qua API Gateway (/api/auth/companies/**) tới auth-service.
 */

export async function fetchCompanies(params?: CompanyFilterParams): Promise<PageResponse<AdminCompanyItem>> {
  const queryParams: Record<string, unknown> = {}
  if (params?.keyword) queryParams.keyword = params.keyword.trim()
  if (params?.status && params.status !== 'ALL') queryParams.status = params.status
  if (params?.page !== undefined) queryParams.page = params.page
  if (params?.size !== undefined) queryParams.size = params.size

  const res = await api.get<ApiResponse<PageResponse<AdminCompanyItem>>>('/auth/companies', { params: queryParams })
  return res.data.data
}

export async function fetchCompanyById(id: number): Promise<AdminCompanyItem> {
  const res = await api.get<ApiResponse<AdminCompanyItem>>(`/auth/companies/${id}`)
  return res.data.data
}

export async function approveCompany(id: number, payload: ApproveCompanyPayload): Promise<AdminCompanyItem> {
  const res = await api.post<ApiResponse<AdminCompanyItem>>(`/auth/companies/${id}/approve`, payload)
  return res.data.data
}

export async function rejectCompany(id: number, payload: RejectCompanyPayload): Promise<void> {
  await api.post<ApiResponse<void>>(`/auth/companies/${id}/reject`, payload)
}

export async function updateCompanyStatus(
  id: number,
  payload: UpdateCompanyStatusPayload
): Promise<AdminCompanyItem> {
  const res = await api.patch<ApiResponse<AdminCompanyItem>>(`/auth/companies/${id}/status`, payload)
  return res.data.data
}
