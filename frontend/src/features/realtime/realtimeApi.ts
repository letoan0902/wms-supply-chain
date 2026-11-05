import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { KpiSnapshot } from './types'

/** Gọi GET /api/notifications/realtime/kpi: số liệu KPI hiện tại, dùng ngay khi vừa nối WebSocket. */
export async function fetchKpiSnapshot(): Promise<KpiSnapshot> {
  const response = await api.get<ApiResponse<KpiSnapshot>>('/notifications/realtime/kpi')
  return response.data.data
}
