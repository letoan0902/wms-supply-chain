/**
 * Kiểu dữ liệu cho phân hệ Quản lý Đa Kho & Vị trí Ô/Kệ (WMS-05 & WMS-06).
 */

export interface Warehouse {
  id: number
  code: string
  name: string
  address?: string
  managerName?: string
  contactPhone?: string
  capacity: number
  active: boolean
  totalLocations: number
  occupiedLocations: number
  utilizationRate: number
  createdAt: string
  updatedAt: string
}

export interface WarehouseSummary {
  totalWarehouses: number
  activeWarehouses: number
  totalCapacity: number
  totalLocations: number
  occupiedLocations: number
  availableLocations: number
  overallUtilizationRate: number
  totalProducts: number
  totalStockQuantity: number
}

export interface CreateWarehousePayload {
  code: string
  name: string
  address?: string
  managerName?: string
  contactPhone?: string
  capacity: number
  active?: boolean
}

export interface UpdateWarehousePayload {
  name: string
  address?: string
  managerName?: string
  contactPhone?: string
  capacity: number
  active?: boolean
}

export type BinLocationStatus = 'EMPTY' | 'OCCUPIED' | 'MAINTENANCE'

export interface BinLocation {
  id: number
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  zone: string
  rack: string
  bin: string
  code: string
  maxWeight?: number
  status: BinLocationStatus | string
  active: boolean
  itemCount: number
  totalQuantity: number
  createdAt: string
}

export interface CreateBinLocationPayload {
  warehouseId: number
  zone: string
  rack: string
  bin: string
  maxWeight?: number
  active?: boolean
}

export interface BulkCreateBinLocationsPayload {
  warehouseId: number
  zone: string
  rackPrefix: string
  rackFrom: number
  rackTo: number
  binPrefix: string
  binFrom: number
  binTo: number
  maxWeight?: number
}

export interface UpdateBinLocationPayload {
  maxWeight?: number
  status?: string
  active?: boolean
}

export interface BinBarcodeData {
  locationId: number
  code: string
  warehouseCode: string
  warehouseName: string
  zone: string
  rack: string
  bin: string
  barcodeBase64: string
}

