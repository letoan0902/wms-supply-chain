import axios from 'axios'
import type { ApiErrorResponse } from '../types/api'
import { clearAuth, getToken } from '../utils/storage'

const LOGIN_PATH = '/login'

/**
 * Axios instance dùng chung cho toàn bộ ứng dụng.
 * baseURL mặc định là '/api': Vite proxy (dev) hoặc Nginx (Docker) chuyển tiếp tới API Gateway.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Gắn Bearer token (nếu có) vào mọi request
api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Xử lý 401: token hết hạn hoặc không hợp lệ -> xóa phiên và quay về trang đăng nhập.
// Riêng request đăng nhập thì bỏ qua để trang Login tự hiển thị lỗi sai thông tin.
// TODO (FR01, Sprint 2): khi auth-service có Refresh Token (HttpOnly cookie), thử làm mới Access Token
// và gửi lại request trước khi đăng xuất.
api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const requestUrl = error.config?.url ?? ''
      if (!requestUrl.includes('/auth/login')) {
        clearAuth()
        if (window.location.pathname !== LOGIN_PATH) {
          window.location.assign(LOGIN_PATH)
        }
      }
    }
    return Promise.reject(error)
  },
)

/** Mã trạng thái HTTP của lỗi (undefined nếu không có phản hồi từ máy chủ). */
export function getErrorStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined
}

/** Lấy thông báo lỗi thân thiện để hiển thị cho người dùng. */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    if (!error.response) {
      return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng hoặc thử lại sau.'
    }
    return error.response.data?.message || fallback
  }
  return fallback
}
