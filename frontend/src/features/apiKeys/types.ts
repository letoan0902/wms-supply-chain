/** Một API key trong danh sách (GET /api/auth/api-keys). Không bao giờ chứa key đầy đủ. */
export interface ApiKeyItem {
  id: number
  name: string
  /** 8 ký tự đầu của key, ví dụ "wms_Ab3x" */
  keyPrefix: string
  active: boolean
  createdAt: string
  /** Tên đăng nhập của ADMIN đã tạo key */
  createdBy?: string | null
  /** Lần dùng gần nhất, cập nhật định kỳ khoảng một phút một lần */
  lastUsedAt?: string | null
  revokedAt?: string | null
}

/** Kết quả POST /api/auth/api-keys: lần duy nhất có key đầy đủ. */
export interface CreatedApiKey {
  id: number
  name: string
  keyPrefix: string
  key: string
  createdAt: string
}

export interface CreateApiKeyPayload {
  name: string
}
