/**
 * Kiểu dữ liệu và cấu hình phân hệ Quản lý Người dùng & Phân quyền (RBAC - FR01, FR02).
 */

export type UserRole =
  | 'ADMIN'
  | 'WAREHOUSE_MANAGER'
  | 'WAREHOUSE_STAFF'
  | 'PURCHASING'
  | 'SALES'
  | 'ACCOUNTANT'

export interface RoleConfigItem {
  label: string
  color: string
  description: string
}

/**
 * Cấu hình hiển thị vai trò: sử dụng hệ màu công nghiệp hiện đại, độ tương phản cao,
 * tránh hoàn toàn các gam màu tím, tím than, tím ánh đen và xanh navi.
 */
export const ROLE_CONFIG: Record<string, RoleConfigItem> = {
  ADMIN: {
    label: 'Quản trị hệ thống',
    color: 'volcano', // Đỏ gạch
    description: 'Toàn quyền cấu hình và quản trị hệ thống',
  },
  WAREHOUSE_MANAGER: {
    label: 'Quản lý kho',
    color: 'blue', // Xanh công nghiệp
    description: 'Quản lý danh mục kho, vị trí kệ, duyệt đơn mua và điều chuyển',
  },
  WAREHOUSE_STAFF: {
    label: 'Nhân viên kho',
    color: 'cyan', // Xanh mòng két
    description: 'Thực hiện xuất nhập kho, quét mã vạch và kiểm đếm thực tế',
  },
  PURCHASING: {
    label: 'Nhân viên mua hàng',
    color: 'orange', // Cam ấm
    description: 'Tạo đơn mua hàng (PO), theo dõi nhà cung cấp',
  },
  SALES: {
    label: 'Nhân viên bán hàng',
    color: 'green', // Xanh lục tươi
    description: 'Tạo đơn bán hàng (SO), theo dõi khách hàng và xuất kho',
  },
  ACCOUNTANT: {
    label: 'Kế toán',
    color: 'gold', // Vàng hổ phách
    description: 'Quản lý công nợ, đối soát hóa đơn và duyệt kiểm kê',
  },
}

export interface UserDetail {
  id: number
  username: string
  email: string
  fullName: string
  enabled: boolean
  roles: string[]
  warehouseIds: number[]
  createdAt: string
  updatedAt: string
}

export interface CreateUserPayload {
  username: string
  email: string
  password: string
  fullName?: string
  roles: string[]
  warehouseIds?: number[]
}

export interface AdminUpdateUserPayload {
  email: string
  fullName?: string
  roles: string[]
  warehouseIds?: number[]
  enabled?: boolean
}

export interface AssignWarehousesPayload {
  warehouseIds: number[]
}

export interface UserFilterParams {
  keyword?: string
  role?: string
  warehouseId?: number
  enabled?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface UserMetrics {
  totalUsers: number
  activeUsers: number
  lockedUsers: number
  warehouseOpsUsers: number
}
