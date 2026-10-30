import { api } from '../../services/api'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  BarcodeData,
  BarcodeType,
  BatchBarcodePayload,
  BatchBarcodeResult,
  BreakdownResult,
  Category,
  CreateProductPayload,
  CreateUnitConversionPayload,
  Product,
  UnitConversion,
  Uom,
  UpdateProductPayload,
  UpdateUnitConversionPayload,
} from './types'

/**
 * REST API client cho phân hệ Danh mục Hàng hóa, SKU & Quy đổi Đơn vị tính (WMS-07).
 */

export async function fetchProducts(params?: {
  keyword?: string
  categoryId?: number
  active?: boolean
  page?: number
  size?: number
  sort?: string
}): Promise<PageResponse<Product>> {
  const res = await api.get<ApiResponse<PageResponse<Product>>>('/products', { params })
  return res.data.data
}

export async function fetchProductById(id: number): Promise<Product> {
  const res = await api.get<ApiResponse<Product>>(`/products/${id}`)
  return res.data.data
}

export async function fetchProductBySku(sku: string): Promise<Product> {
  const res = await api.get<ApiResponse<Product>>(`/products/sku/${sku}`)
  return res.data.data
}

export async function createProduct(payload: CreateProductPayload): Promise<Product> {
  const res = await api.post<ApiResponse<Product>>('/products', payload)
  return res.data.data
}

export async function updateProduct(id: number, payload: UpdateProductPayload): Promise<Product> {
  const res = await api.put<ApiResponse<Product>>(`/products/${id}`, payload)
  return res.data.data
}

export async function deleteProduct(id: number): Promise<void> {
  await api.delete(`/products/${id}`)
}

// --- Categories & UOMs ---

export async function fetchCategories(): Promise<Category[]> {
  try {
    const res = await api.get<ApiResponse<Category[]>>('/products/categories')
    return res.data.data || []
  } catch {
    // Fallback nếu chưa có category nào trong DB
    return []
  }
}

export async function createCategory(payload: { code: string; name: string; description?: string }): Promise<Category> {
  const res = await api.post<ApiResponse<Category>>('/products/categories', payload)
  return res.data.data
}

export async function fetchUoms(): Promise<Uom[]> {
  try {
    const res = await api.get<ApiResponse<Uom[]>>('/products/uoms')
    return res.data.data || []
  } catch {
    return []
  }
}

export async function createUom(payload: { code: string; name: string }): Promise<Uom> {
  const res = await api.post<ApiResponse<Uom>>('/products/uoms', payload)
  return res.data.data
}

// --- Unit Conversions (WMS-07.2) ---

export async function fetchUnitConversions(productId: number): Promise<UnitConversion[]> {
  const res = await api.get<ApiResponse<UnitConversion[]>>(`/products/${productId}/conversions`)
  return res.data.data || []
}

export async function createUnitConversion(
  productId: number,
  payload: CreateUnitConversionPayload
): Promise<UnitConversion> {
  const res = await api.post<ApiResponse<UnitConversion>>(`/products/${productId}/conversions`, payload)
  return res.data.data
}

export async function updateUnitConversion(
  productId: number,
  id: number,
  payload: UpdateUnitConversionPayload
): Promise<UnitConversion> {
  const res = await api.put<ApiResponse<UnitConversion>>(`/products/${productId}/conversions/${id}`, payload)
  return res.data.data
}

export async function deleteUnitConversion(productId: number, id: number): Promise<void> {
  await api.delete(`/products/${productId}/conversions/${id}`)
}

export async function convertToBase(
  productId: number,
  payload: { uomId: number; quantity: number }
) {
  const res = await api.post<ApiResponse<{ baseQuantity: number; conversionRate: number }>>(
    `/products/${productId}/conversions/convert-to-base`,
    payload
  )
  return res.data.data
}

export async function calculateConversion(
  productId: number,
  payload: { fromUomId: number; toUomId: number; quantity: number }
) {
  const res = await api.post<ApiResponse<{ convertedQuantity: number; conversionRate: number }>>(
    `/products/${productId}/conversions/calculate`,
    payload
  )
  return res.data.data
}

export async function fetchBreakdown(productId: number, baseQuantity: number): Promise<BreakdownResult> {
  const res = await api.get<ApiResponse<BreakdownResult>>(`/products/${productId}/conversions/breakdown`, {
    params: { baseQuantity },
  })
  return res.data.data
}

// --- Barcodes & QR (WMS-07.3) ---

export async function fetchProductBarcode(
  productId: number,
  params?: { type?: BarcodeType; width?: number; height?: number; includeLabel?: boolean }
): Promise<BarcodeData> {
  const res = await api.get<ApiResponse<BarcodeData>>(`/products/${productId}/barcode`, { params })
  return res.data.data
}

export async function generateBatchBarcode(
  productId: number,
  payload: BatchBarcodePayload
): Promise<BatchBarcodeResult> {
  const res = await api.post<ApiResponse<BatchBarcodeResult>>(`/products/${productId}/barcode/batch`, payload)
  return res.data.data
}

export async function generateGenericBarcode(
  text: string,
  params?: { type?: BarcodeType; width?: number; height?: number; includeLabel?: boolean }
): Promise<BarcodeData> {
  const res = await api.get<ApiResponse<BarcodeData>>('/products/barcodes/generate', {
    params: { text, ...params },
  })
  return res.data.data
}
