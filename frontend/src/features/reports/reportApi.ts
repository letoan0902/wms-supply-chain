import axios from 'axios'
import { api } from '../../services/api'
import type { ApiResponse } from '../../types/api'
import type { InOutBalanceParams, InOutBalanceReport, StockCardParams, StockCardReport } from './types'

/**
 * Các hàm gọi REST API báo cáo kho của inventory-service (tiền tố /api/inventory/reports, xem docs/api/BAO_CAO_KHO.md).
 * Mọi hàm nhận AbortSignal để hủy request cũ khi bộ lọc thay đổi nhanh.
 */

const BASE = '/inventory/reports'

/** Thời gian chờ khi tải file Excel (lập báo cáo tới 20000 dòng lâu hơn request thường). */
const EXPORT_TIMEOUT_MS = 120_000

/** Bỏ các tham số rỗng để URL gọn và backend dùng giá trị mặc định. */
function cleanParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  ) as Partial<T>
}

export async function fetchInOutBalance(params: InOutBalanceParams, signal?: AbortSignal): Promise<InOutBalanceReport> {
  const res = await api.get<ApiResponse<InOutBalanceReport>>(`${BASE}/in-out-balance`, {
    params: cleanParams(params),
    signal,
  })
  return res.data.data
}

export async function fetchStockCard(params: StockCardParams, signal?: AbortSignal): Promise<StockCardReport> {
  const res = await api.get<ApiResponse<StockCardReport>>(`${BASE}/stock-card`, {
    params: cleanParams(params),
    signal,
  })
  return res.data.data
}

export interface DownloadedFile {
  blob: Blob
  /** Tên file theo header Content-Disposition, null nếu máy chủ không gửi */
  fileName: string | null
}

/** Lấy tên file từ header Content-Disposition (hỗ trợ cả filename*=UTF-8''...). */
export function fileNameFromDisposition(header: string | null | undefined): string | null {
  if (!header) return null
  const star = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(header)
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // Rơi xuống cách đọc filename thường
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(header)
  return plain ? plain[1].trim() : null
}

/**
 * Lỗi của request responseType 'blob' có body là Blob: đọc thành JSON ApiResponse và gắn lại vào response
 * để getApiErrorMessage hiển thị đúng thông báo của backend.
 */
async function normalizeBlobError(error: unknown): Promise<unknown> {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      const text = await error.response.data.text()
      error.response.data = JSON.parse(text)
    } catch {
      error.response.data = undefined
    }
  }
  return error
}

async function downloadExport(path: string, params: object, signal?: AbortSignal): Promise<DownloadedFile> {
  try {
    const res = await api.get<Blob>(`${BASE}/${path}/export`, {
      params: cleanParams(params),
      responseType: 'blob',
      timeout: EXPORT_TIMEOUT_MS,
      signal,
    })
    const disposition = res.headers['content-disposition'] as string | undefined
    return { blob: res.data, fileName: fileNameFromDisposition(disposition) }
  } catch (error) {
    throw await normalizeBlobError(error)
  }
}

/** Bản sao tham số không có page, size (file Excel luôn gồm mọi dòng). */
function withoutPaging<T extends { page?: number; size?: number }>(params: T): Omit<T, 'page' | 'size'> {
  const copy = { ...params }
  delete copy.page
  delete copy.size
  return copy
}

/** GET /inventory/reports/in-out-balance/export: file xlsx do backend lập (cùng tham số với API xem, bỏ page/size). */
export function downloadInOutBalanceExport(params: InOutBalanceParams, signal?: AbortSignal): Promise<DownloadedFile> {
  return downloadExport('in-out-balance', withoutPaging(params), signal)
}

/** GET /inventory/reports/stock-card/export: file xlsx thẻ kho do backend lập. */
export function downloadStockCardExport(params: StockCardParams, signal?: AbortSignal): Promise<DownloadedFile> {
  return downloadExport('stock-card', withoutPaging(params), signal)
}
