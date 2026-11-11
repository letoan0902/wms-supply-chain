import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../types/auth'
import { getStoredUser, getToken } from '../../utils/storage'
import { hasGlobalScope, isAllowed, type Capability, type PermissionSubject } from './permissions'

/** Đọc phần payload của JWT (không kiểm tra chữ ký: máy chủ kiểm tra, ở đây chỉ để ẩn nút). */
function readTokenClaims(token: string | null): Record<string, unknown> | null {
  const payload = token?.split('.')[1]
  if (!payload) return null
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')))
        .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    )
    const claims: unknown = JSON.parse(json)
    return typeof claims === 'object' && claims !== null ? (claims as Record<string, unknown>) : null
  } catch {
    return null
  }
}

function toRoles(value: unknown): string[] | null {
  if (!Array.isArray(value)) return null
  return value.filter((r): r is string => typeof r === 'string').map((r) => r.replace(/^ROLE_/, ''))
}

function toIds(value: unknown): number[] | null {
  if (!Array.isArray(value)) return null
  return value.map((id) => Number(id)).filter((id) => Number.isInteger(id) && id > 0)
}

/**
 * Vai trò và kho được gán của người đang đăng nhập. Ưu tiên claim `roles`, `warehouseIds` trong access token vì
 * api-gateway chuyển đúng các claim này cho service (kết quả đăng nhập lưu ở localStorage không có danh sách kho),
 * không đọc được token thì lấy thông tin đã lưu lúc đăng nhập.
 */
export function buildPermissionSubject(token: string | null, user: AuthUser | null): PermissionSubject {
  const claims = readTokenClaims(token)
  return {
    roles: toRoles(claims?.roles) ?? user?.roles ?? [],
    warehouseIds: toIds(claims?.warehouseIds) ?? user?.warehouseIds ?? [],
  }
}

/** Danh tính hiện tại (dùng ngoài component, ví dụ trong hàm tiện ích). */
export function getPermissionSubject(): PermissionSubject {
  return buildPermissionSubject(getToken(), getStoredUser())
}

/** Kiểm tra quyền của người đang đăng nhập ngoài component. Trong component dùng {@link useCan}. */
export function canCurrentUser(capability: Capability, warehouseId?: number | null): boolean {
  return isAllowed(getPermissionSubject(), capability, warehouseId)
}

export interface CanFn {
  (capability: Capability, warehouseId?: number | null): boolean
  /** Dùng được mọi kho của doanh nghiệp cho thao tác này */
  global: (capability: Capability) => boolean
  /** Kho được gán của người dùng (theo access token) */
  warehouseIds: readonly number[]
}

/**
 * Hook xét quyền theo bảng ở permissions.ts: `const can = useCan(); can('transfer.receive', transfer.destinationWarehouseId)`.
 * Tính lại khi access token đổi (làm mới token có thể đổi danh sách kho) hoặc khi thông tin người dùng được cập nhật.
 */
export function useCan(): CanFn {
  const token = getToken()
  const [user, setUser] = useState<AuthUser | null>(() => getStoredUser())

  useEffect(() => {
    const reload = () => setUser(getStoredUser())
    window.addEventListener('wms:user-updated', reload)
    window.addEventListener('storage', reload)
    return () => {
      window.removeEventListener('wms:user-updated', reload)
      window.removeEventListener('storage', reload)
    }
  }, [])

  return useMemo(() => {
    const subject = buildPermissionSubject(token, user)
    const can = ((capability: Capability, warehouseId?: number | null) =>
      isAllowed(subject, capability, warehouseId)) as CanFn
    can.global = (capability: Capability) => hasGlobalScope(subject, capability)
    can.warehouseIds = subject.warehouseIds
    return can
  }, [token, user])
}
