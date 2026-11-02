/**
 * Kiểu dữ liệu cho phân hệ Quản lý Danh mục SKU & Quy đổi Đơn vị tính (WMS-07).
 */

export interface Product {
  id: number
  sku: string
  barcode?: string
  name: string
  description?: string
  categoryId?: number
  categoryName?: string
  uomId: number
  uomCode: string
  uomName?: string
  purchasePrice?: number
  sellingPrice?: number
  vatRate?: number
  imageUrl?: string
  manageByLot: boolean
  manageByExpiration: boolean
  safetyStock: number
  reorderPoint: number
  maxStock: number
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateProductPayload {
  sku: string
  barcode?: string
  name: string
  description?: string
  categoryId?: number
  uomId: number
  purchasePrice?: number
  sellingPrice?: number
  vatRate?: number
  imageUrl?: string
  manageByLot?: boolean
  manageByExpiration?: boolean
  safetyStock?: number
  reorderPoint?: number
  maxStock?: number
}

export interface UpdateProductPayload {
  sku: string
  barcode?: string
  name: string
  description?: string
  categoryId?: number
  uomId: number
  purchasePrice?: number
  sellingPrice?: number
  vatRate?: number
  imageUrl?: string
  manageByLot?: boolean
  manageByExpiration?: boolean
  safetyStock?: number
  reorderPoint?: number
  maxStock?: number
  active?: boolean
}

export interface Category {
  id: number
  code: string
  name: string
  description?: string
}

export interface Uom {
  id: number
  code: string
  name: string
}

export interface UnitConversion {
  id: number
  productId: number
  productSku: string
  productName: string
  fromUomId: number
  fromUomCode: string
  fromUomName: string
  toUomId: number
  toUomCode: string
  toUomName: string
  conversionFactor: number
  factorToBase?: number
  barcode?: string
  description?: string
}

export interface CreateUnitConversionPayload {
  fromUomId: number
  toUomId: number
  conversionFactor: number
  barcode?: string
  description?: string
}

export interface UpdateUnitConversionPayload {
  fromUomId: number
  toUomId: number
  conversionFactor: number
  barcode?: string
  description?: string
}

export interface ConvertToBaseResult {
  productId: number
  productSku: string
  inputUomId: number
  inputUomCode: string
  inputUomName: string
  inputQuantity: number
  baseUomId: number
  baseUomCode: string
  baseUomName: string
  baseQuantity: number
  conversionRate: number
}

export interface BreakdownItem {
  uomId: number
  uomCode: string
  uomName: string
  quantity: number
  factorToBase: number
}

export interface BreakdownResult {
  productId: number
  productSku: string
  totalBaseQuantity: number
  baseUomName: string
  breakdown: BreakdownItem[]
  summary: string
}

export type BarcodeType = 'CODE_128' | 'EAN_13' | 'QR_CODE'

export interface BarcodeData {
  content: string
  type: BarcodeType
  width: number
  height: number
  base64Image: string
}

export interface BatchBarcodePayload {
  batchNumber: string
  expirationDate: string
  type?: BarcodeType
  width?: number
  height?: number
}

export interface BatchBarcodeResult {
  productId: number
  sku: string
  productName: string
  batchNumber: string
  expirationDate: string
  encodedContent: string
  type: BarcodeType
  base64Image: string
}
