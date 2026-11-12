export interface LoginRequest {
  username: string
  password: string
}

/** Thông tin người dùng hiện tại, lưu ở localStorage để hiển thị trên giao diện. */
export interface AuthUser {
  username: string
  roles: string[]
  fullName?: string
  email?: string
  warehouseIds?: number[]
  /** Doanh nghiệp của người dùng (từ `company` của kết quả đăng nhập) */
  company?: CompanySummary
  /** Thuê bao lúc đăng nhập (từ `subscription` của kết quả đăng nhập), chỉ để hiển thị */
  subscription?: SubscriptionSummary | null
}

/** Doanh nghiệp của người dùng, trả trong `company` của kết quả đăng nhập và của GET /api/auth/me. */
export interface CompanySummary {
  id: number
  code: string
  name: string
}

/**
 * Tóm tắt thuê bao trả kèm kết quả đăng nhập và GET /api/auth/me (vn.wms.auth.subscription.dto.SubscriptionSummary).
 * `expiresAt`: TRIAL là ngày hết dùng thử, PRO là ngày hết hạn, FREE là null.
 */
export interface SubscriptionSummary {
  plan: string
  status: string
  expiresAt: string | null
}

/** Dữ liệu trả về trong `data` của POST /api/auth/login (vn.wms.auth.dto.LoginResponse). */
export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** Thời gian sống của token, tính bằng giây */
  expiresIn: number
  username: string
  roles: string[]
  company?: CompanySummary
  subscription?: SubscriptionSummary | null
}

/** Dữ liệu trả về trong `data` của GET /api/auth/me (vn.wms.auth.dto.users.UserInfoResponse). */
export interface CurrentUserInfo {
  username: string
  roles: string[]
  company?: CompanySummary | null
  subscription?: SubscriptionSummary | null
}

/** Payload yêu cầu làm mới Access Token. */
export interface RefreshTokenRequest {
  refreshToken: string
}

/** Dữ liệu trả về trong `data` của POST /api/auth/refresh (vn.wms.auth.dto.RefreshTokenResponse). */
export interface RefreshTokenResponse {
  accessToken: string
  tokenType: string
  /** Thời gian sống của token mới, tính bằng giây */
  expiresIn: number
  refreshToken: string
}
