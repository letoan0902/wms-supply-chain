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

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * Không mở hộp thoại vượt giới hạn gói dùng chung cho lỗi PLAN_LIMIT_EXCEEDED của request này: màn hình tự báo
     * nhẹ trong khối của mình (ví dụ khối tóm tắt bằng AI tự tải khi mở dashboard).
     */
    skipPlanLimitDialog?: boolean
  }
}

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

/** Mã lỗi thuê bao do hộp thoại dùng chung hiển thị (docs/SAAS_SPEC.md mục 2.4). */
export const SUBSCRIPTION_EXPIRED = 'SUBSCRIPTION_EXPIRED'
export const PLAN_LIMIT_EXCEEDED = 'PLAN_LIMIT_EXCEEDED'

/** Lỗi thuê bao gửi cho hộp thoại dùng chung. `data` của PLAN_LIMIT_EXCEEDED gồm `limit`, `current`, `max`, `plan`. */
export interface SubscriptionApiError {
  code: typeof SUBSCRIPTION_EXPIRED | typeof PLAN_LIMIT_EXCEEDED
  message: string
  data?: unknown
}

type SubscriptionErrorListener = (error: SubscriptionApiError) => void

const subscriptionErrorListeners = new Set<SubscriptionErrorListener>()
// Lỗi đã được hộp thoại dùng chung hiển thị: getApiErrorMessage trả chuỗi rỗng để màn hình không báo lần hai
const handledErrors = new WeakSet<object>()

/**
 * Đăng ký nhận lỗi SUBSCRIPTION_EXPIRED và PLAN_LIMIT_EXCEEDED từ mọi request (hộp thoại dùng chung của
 * phân hệ Gói dịch vụ đăng ký ở đây). Trả hàm hủy đăng ký.
 */
export function onSubscriptionApiError(listener: SubscriptionErrorListener): () => void {
  subscriptionErrorListeners.add(listener)
  return () => {
    subscriptionErrorListeners.delete(listener)
  }
}

/** Lỗi này đã được hộp thoại dùng chung hiển thị hay chưa. */
export function isHandledByGlobalDialog(error: unknown): boolean {
  return typeof error === 'object' && error !== null && handledErrors.has(error)
}

// Lỗi thuê bao (403 SUBSCRIPTION_EXPIRED, 403 PLAN_LIMIT_EXCEEDED): chuyển cho hộp thoại dùng chung rồi vẫn
// từ chối promise để màn hình gọi API tắt trạng thái đang tải như mọi lỗi khác.
api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 403) {
      const body = error.response.data as { code?: string; message?: string; data?: unknown } | undefined
      const code = body?.code
      const skipDialog = code === PLAN_LIMIT_EXCEEDED && error.config?.skipPlanLimitDialog === true
      if (
        (code === SUBSCRIPTION_EXPIRED || code === PLAN_LIMIT_EXCEEDED) &&
        !skipDialog &&
        subscriptionErrorListeners.size > 0
      ) {
        handledErrors.add(error)
        const payload: SubscriptionApiError = { code, message: body?.message ?? '', data: body?.data }
        subscriptionErrorListeners.forEach((listener) => listener(payload))
      }
    }
    return Promise.reject(error)
  },
)

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

/**
 * Lấy thông báo lỗi thân thiện để hiển thị cho người dùng.
 * Trả chuỗi rỗng khi lỗi đã được hộp thoại gói dịch vụ hiển thị (hết hạn, vượt giới hạn gói): thông báo nổi
 * rỗng bị ẩn bằng CSS trong src/index.css, nhờ vậy màn hình đang gọi `message.error(...)` không báo lần hai.
 */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (isHandledByGlobalDialog(error)) {
    return ''
  }
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    if (!error.response) {
      return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng hoặc thử lại sau.'
    }
    return error.response.data?.message || fallback
  }
  return fallback
}
