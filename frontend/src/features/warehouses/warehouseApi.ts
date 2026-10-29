import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type {
  BinBarcodeData,
  BinLocation,
  BulkCreateBinLocationsPayload,
  CreateBinLocationPayload,
  CreateWarehousePayload,
  UpdateBinLocationPayload,
  UpdateWarehousePayload,
  Warehouse,
  WarehouseSummary,
} from './types'

/**
 * Các hàm gọi REST API quản lý kho và vị trí ô/kệ (WMS-05, WMS-06).
 */

export async function fetchWarehouses(params?: { keyword?: string; active?: boolean }): Promise<Warehouse[]> {
  const res = await api.get<ApiResponse<Warehouse[]>>('/inventory/warehouses', { params })
  return res.data.data
}

export async function fetchWarehouseSummary(): Promise<WarehouseSummary> {
  const res = await api.get<ApiResponse<WarehouseSummary>>('/inventory/warehouses/summary')
  return res.data.data
}

export async function fetchWarehouseById(id: number): Promise<Warehouse> {
  const res = await api.get<ApiResponse<Warehouse>>(`/inventory/warehouses/${id}`)
  return res.data.data
}

export async function createWarehouse(payload: CreateWarehousePayload): Promise<Warehouse> {
  const res = await api.post<ApiResponse<Warehouse>>('/inventory/warehouses', payload)
  return res.data.data
}

export async function updateWarehouse(id: number, payload: UpdateWarehousePayload): Promise<Warehouse> {
  const res = await api.put<ApiResponse<Warehouse>>(`/inventory/warehouses/${id}`, payload)
  return res.data.data
}

export async function toggleWarehouseStatus(id: number, active: boolean): Promise<Warehouse> {
  const res = await api.patch<ApiResponse<Warehouse>>(`/inventory/warehouses/${id}/status`, { active })
  return res.data.data
}

export async function deleteWarehouse(id: number): Promise<void> {
  await api.delete(`/inventory/warehouses/${id}`)
}

// --- Bin Location APIs (WMS-06) ---

export async function fetchLocations(params?: {
  warehouseId?: number
  zone?: string
  rack?: string
  status?: string
  active?: boolean
}): Promise<BinLocation[]> {
  const res = await api.get<ApiResponse<BinLocation[]>>('/inventory/locations', { params })
  return res.data.data
}

export async function fetchZones(warehouseId: number): Promise<string[]> {
  const res = await api.get<ApiResponse<string[]>>('/inventory/locations/zones', {
    params: { warehouseId },
  })
  return res.data.data
}

export async function createLocation(payload: CreateBinLocationPayload): Promise<BinLocation> {
  const res = await api.post<ApiResponse<BinLocation>>('/inventory/locations', payload)
  return res.data.data
}

export async function bulkCreateLocations(payload: BulkCreateBinLocationsPayload): Promise<BinLocation[]> {
  const res = await api.post<ApiResponse<BinLocation[]>>('/inventory/locations/bulk', payload)
  return res.data.data
}

export async function updateLocation(id: number, payload: UpdateBinLocationPayload): Promise<BinLocation> {
  const res = await api.put<ApiResponse<BinLocation>>(`/inventory/locations/${id}`, payload)
  return res.data.data
}

export async function deleteLocation(id: number): Promise<void> {
  await api.delete(`/inventory/locations/${id}`)
}

export async function fetchLocationBarcode(id: number): Promise<BinBarcodeData> {
  const res = await api.get<ApiResponse<BinBarcodeData>>(`/inventory/locations/${id}/barcode/preview`)
  return res.data.data
}

