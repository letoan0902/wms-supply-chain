import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { ApiErrorResponse, ApiResponse } from '../types/api'
import type { RefreshTokenResponse } from '../types/auth'
import {
  clearAuth,
  getRefreshToken,
  getToken,
  saveRefreshToken,
  saveToken,
} from '../utils/storage'

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

// Hàng đợi lưu các request đến sau trong lúc một request đang làm mới Access Token (tránh Race Condition)
interface QueueItem {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

let isRefreshing = false
let failedQueue: QueueItem[] = []

/**
 * Giải phóng hàng đợi:
 * - Khi refresh thành công: thông báo Access Token mới để tất cả request trong hàng đợi tiếp tục chạy.
 * - Khi refresh thất bại: từ chối (reject) toàn bộ các request đang chờ.
 */
function processQueue(error: unknown, token: string | null = null): void {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else if (token) {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

// Xử lý 401: Tự động dùng Refresh Token để gia hạn Access Token và thử lại request bị lỗi.
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
      const requestUrl = originalRequest?.url ?? ''

      // Nếu lỗi từ chính API đăng nhập (/auth/login) thì bỏ qua để trang Login tự hiển thị thông báo lỗi sai tài khoản/mật khẩu
      if (requestUrl.includes('/auth/login')) {
        return Promise.reject(error)
      }

      // Bẫy lặp vô hạn: nếu chính API refresh bị 401 hoặc request này đã được retry một lần rồi mà vẫn 401
      if (!originalRequest || requestUrl.includes('/auth/refresh') || originalRequest._retry) {
        processQueue(error, null)
        clearAuth()
        if (window.location.pathname !== LOGIN_PATH) {
          window.location.assign(LOGIN_PATH)
        }
        return Promise.reject(error)
      }

      // Nếu đang trong quá trình refresh token, đưa các request đến sau vào hàng đợi
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((newToken) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newToken}`
            }
            return api(originalRequest)
          })
          .catch((err) => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const currentRefreshToken = getRefreshToken()
      if (!currentRefreshToken) {
        clearAuth()
        if (window.location.pathname !== LOGIN_PATH) {
          window.location.assign(LOGIN_PATH)
        }
        return Promise.reject(error)
      }

      try {
        // Gọi API refresh qua axios độc lập (không qua instance `api` để tránh đệ quy interceptor)
        const refreshResponse = await axios.post<ApiResponse<RefreshTokenResponse>>(
          `${api.defaults.baseURL || '/api'}/auth/refresh`,
          { refreshToken: currentRefreshToken },
          { headers: { 'Content-Type': 'application/json' } },
        )

        const data = refreshResponse.data?.data
        const newAccessToken = data?.accessToken
        const newRefreshToken = data?.refreshToken

        if (!newAccessToken) {
          throw new Error('Không nhận được Access Token mới từ máy chủ')
        }

        // Cập nhật Access Token mới (và Refresh Token mới nếu backend xoay vòng RTR)
        saveToken(newAccessToken)
        if (newRefreshToken) {
          saveRefreshToken(newRefreshToken)
        }

        // Giải phóng hàng đợi và thông báo token mới cho các request đang chờ
        processQueue(null, newAccessToken)

        // Gắn token mới và gửi lại request ban đầu (replay originalRequest)
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        }
        return api(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr, null)
        clearAuth()
        if (window.location.pathname !== LOGIN_PATH) {
          window.location.assign(LOGIN_PATH)
        }
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
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
