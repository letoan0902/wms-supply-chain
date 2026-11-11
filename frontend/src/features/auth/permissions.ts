/**
 * Bảng quyền thao tác của giao diện theo vai trò và theo kho.
 *
 * Nguồn: ma trận vai trò ở docs/SECURITY.md mục 3, đối chiếu với `@PreAuthorize` trên controller của từng service và
 * các kiểm tra phạm vi kho viết tay (`UserContext.requireWarehouseWriteAccess`, `resolveAllowedWarehouses`,
 * `StocktakePermissions`, `ReportAccess`). Bảng này và tài liệu phải đổi cùng nhau: khi backend đổi vai trò của một
 * API, sửa docs/SECURITY.md và dòng tương ứng ở đây trong cùng một thay đổi.
 *
 * Giao diện chỉ dùng bảng để ẩn hoặc khóa nút; backend vẫn là nơi quyết định và trả 403 khi không được phép.
 * Tệp không phụ thuộc React hay localStorage để kiểm tra được bằng Node (hook đọc phiên đăng nhập ở useCan.ts).
 */

export type Role = 'ADMIN' | 'WAREHOUSE_MANAGER' | 'WAREHOUSE_STAFF' | 'PURCHASING' | 'SALES' | 'ACCOUNTANT'

/**
 * Cách backend giới hạn theo kho:
 * - `read`: ADMIN, ACCOUNTANT dùng mọi kho; vai trò khác chỉ các kho được gán (`resolveAllowedWarehouses`, kiểm tra
 *   kho nguồn khi lập lệnh điều chuyển).
 * - `write`: ADMIN dùng mọi kho; WAREHOUSE_MANAGER chỉ kho được gán (`requireWarehouseWriteAccess`).
 * - `count`: ADMIN dùng mọi kho; WAREHOUSE_MANAGER, WAREHOUSE_STAFF chỉ kho được gán (`StocktakePermissions.requireCount`).
 */
export type WarehouseScope = 'read' | 'write' | 'count'

export interface CapabilityRule {
  /** Vai trò được gọi API (khớp `hasAnyRole` của backend) */
  roles: readonly Role[]
  /** Bỏ trống: không giới hạn theo kho */
  scope?: WarehouseScope
}

const ALL: readonly Role[] = ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT']

/** Vai trò dùng mọi kho của doanh nghiệp theo từng kiểu phạm vi. */
export const GLOBAL_SCOPE_ROLES: Record<WarehouseScope, readonly Role[]> = {
  read: ['ADMIN', 'ACCOUNTANT'],
  write: ['ADMIN'],
  count: ['ADMIN'],
}

export const CAPABILITIES = {
  // product-service
  'product.read': { roles: ALL },
  /** Thêm, sửa, nhập Excel hàng hóa; tạo danh mục, đơn vị tính; thêm, sửa, xóa quy đổi đơn vị */
  'product.write': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING'] },
  /** Ngưng hoạt động hàng hóa (DELETE /api/products/{id}) */
  'product.delete': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'] },
  /** In tem mã vạch theo lô (POST /api/products/{id}/barcode/batch) */
  'product.barcodeBatch': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },

  // inventory-service: kho, vị trí, lô, tồn
  /** Đọc danh sách kho; vai trò ngoài ADMIN, ACCOUNTANT chưa được gán kho nhận 403 */
  'warehouse.read': { roles: ALL, scope: 'read' },
  'warehouse.create': { roles: ['ADMIN'] },
  'warehouse.delete': { roles: ['ADMIN'] },
  /** Sửa kho, đổi trạng thái kho */
  'warehouse.write': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },
  'location.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'], scope: 'read' },
  /** Tạo, tạo hàng loạt, sửa, xóa vị trí ô kệ */
  'location.write': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },
  /** Đọc tồn, lô, hàng đang về, ATP, cảnh báo tồn kho */
  'stock.read': { roles: ALL },
  /** Khóa, mở khóa lô, khóa lô hết hạn */
  'lot.lock': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },
  /** Chạy quét cảnh báo tồn kho ngay (POST /api/inventory/alerts/scan) */
  'alert.scan': { roles: ['ADMIN'] },
  /** Báo cáo nhập-xuất-tồn, thẻ kho, truy vết lô */
  'report.view': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT'], scope: 'read' },
  'stockAdjustment.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'ACCOUNTANT'], scope: 'read' },

  // inventory-service: điều chuyển (giới hạn kho theo kho nguồn hoặc kho đích của lệnh)
  'transfer.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT'], scope: 'read' },
  /** Lập lệnh điều chuyển: kho truyền vào là kho nguồn */
  'transfer.create': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'], scope: 'read' },
  /** Sửa, hủy, xóa, duyệt xuất lệnh điều chuyển: kho truyền vào là kho nguồn */
  'transfer.manage': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },
  /** Nhận hàng điều chuyển: kho truyền vào là kho đích */
  'transfer.receive': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },

  // inventory-service: kiểm kê (màn hình kiểm kê do nhóm khác làm, bảng có sẵn để dùng)
  'stocktake.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT'], scope: 'read' },
  /** Lập, bắt đầu, kết thúc, đếm lại, hủy, ghi lý do, duyệt cấp quản lý */
  'stocktake.manage': { roles: ['ADMIN', 'WAREHOUSE_MANAGER'], scope: 'write' },
  'stocktake.count': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'], scope: 'count' },
  /** Xem chênh lệch, tồn hệ thống */
  'stocktake.variance': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'ACCOUNTANT'], scope: 'read' },
  'stocktake.approveAdjustment': { roles: ['ADMIN', 'ACCOUNTANT'] },
  'stocktake.reject': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'ACCOUNTANT'] },

  // order-service: khách hàng, nhà cung cấp
  'customer.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'SALES', 'ACCOUNTANT'] },
  /** Tạo, nhập hàng loạt, sửa, xóa khách hàng */
  'customer.write': { roles: ['ADMIN', 'SALES', 'ACCOUNTANT'] },
  /** Ghi giao dịch công nợ khách hàng */
  'customer.transaction': { roles: ['ADMIN', 'ACCOUNTANT'] },
  'supplier.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT'] },
  /** Tổng hợp công nợ và lịch sử giao dịch nhà cung cấp */
  'supplier.debt': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT'] },
  /** Tạo, nhập hàng loạt, sửa nhà cung cấp */
  'supplier.write': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT'] },
  'supplier.delete': { roles: ['ADMIN', 'PURCHASING', 'ACCOUNTANT'] },
  /** Ghi giao dịch công nợ nhà cung cấp */
  'supplier.transaction': { roles: ['ADMIN', 'ACCOUNTANT'] },

  // order-service: mua hàng
  'purchaseOrder.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT'] },
  /** Tạo, sửa, xóa, hủy đơn mua */
  'purchaseOrder.write': { roles: ['ADMIN', 'PURCHASING'] },
  /** Duyệt, từ chối đơn mua */
  'purchaseOrder.approve': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING'] },
  'goodsReceipt.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT'] },
  /** Lập, xác nhận phiếu nhập kho */
  'goodsReceipt.create': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
  'purchaseBill.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT'] },
  /** Ghi thanh toán hóa đơn mua */
  'purchaseBill.pay': { roles: ['ADMIN', 'ACCOUNTANT'] },

  // order-service: bán hàng
  'salesOrder.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT'] },
  'salesOrder.checkAtp': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'SALES'] },
  /** Tạo, sửa, xóa, hủy đơn bán */
  'salesOrder.write': { roles: ['ADMIN', 'SALES'] },
  'salesOrder.confirm': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'SALES'] },
  /** Danh sách lấy hàng của đơn bán (GET .../picking-list, gọi tiếp POST /api/inventory/stock/picking-list) */
  'picking.view': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES'] },
  'gdn.read': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT'] },
  /** Lập phiếu xuất kho, xác nhận xuất */
  'gdn.create': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },

  // notification-service, ai-service
  'dashboard.view': { roles: ALL },
  /** Cấu hình RabbitMQ, SMTP (GET /api/notifications/health-info) */
  'system.health': { roles: ['ADMIN'] },
  /** Trích xuất bằng AI dữ liệu hàng hóa, nhà cung cấp */
  'aiImport.productSupplier': { roles: ['ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING'] },
  /** Trích xuất bằng AI dữ liệu khách hàng */
  'aiImport.customer': { roles: ['ADMIN', 'SALES'] },

  // auth-service
  'user.manage': { roles: ['ADMIN'] },
  'apiKey.manage': { roles: ['ADMIN'] },
  /** Thanh toán, gia hạn, hạ gói, xem lịch sử thanh toán */
  'subscription.pay': { roles: ['ADMIN'] },
  /** Sửa thông tin doanh nghiệp */
  'company.update': { roles: ['ADMIN'] },
} as const satisfies Record<string, CapabilityRule>

export type Capability = keyof typeof CAPABILITIES

/** Danh tính dùng để xét quyền: vai trò và kho được gán đúng như api-gateway chuyển cho service. */
export interface PermissionSubject {
  roles: readonly string[]
  warehouseIds: readonly number[]
}

function hasAnyRole(subject: PermissionSubject, roles: readonly Role[]): boolean {
  return roles.some((role) => subject.roles.includes(role))
}

/**
 * Người dùng có được dùng thao tác này hay không.
 * - Không truyền `warehouseId`: có được dùng ở ít nhất một kho (vai trò bị giới hạn mà chưa được gán kho nào thì không).
 * - Truyền `warehouseId`: có được dùng ở đúng kho đó (với điều chuyển là kho nguồn, riêng nhận hàng là kho đích).
 */
export function isAllowed(subject: PermissionSubject, capability: Capability, warehouseId?: number | null): boolean {
  const rule: CapabilityRule = CAPABILITIES[capability]
  if (!hasAnyRole(subject, rule.roles)) return false
  if (!rule.scope) return true
  if (hasAnyRole(subject, GLOBAL_SCOPE_ROLES[rule.scope])) return true
  // Vai trò bị giới hạn theo kho: với `write` chỉ WAREHOUSE_MANAGER, với `count` thêm WAREHOUSE_STAFF
  if (rule.scope === 'write' && !subject.roles.includes('WAREHOUSE_MANAGER')) return false
  if (
    rule.scope === 'count' &&
    !subject.roles.includes('WAREHOUSE_MANAGER') &&
    !subject.roles.includes('WAREHOUSE_STAFF')
  ) {
    return false
  }
  if (warehouseId === undefined || warehouseId === null) return subject.warehouseIds.length > 0
  return subject.warehouseIds.includes(warehouseId)
}

/** Người dùng dùng được mọi kho của doanh nghiệp cho thao tác này (không cần lọc theo kho được gán). */
export function hasGlobalScope(subject: PermissionSubject, capability: Capability): boolean {
  const rule: CapabilityRule = CAPABILITIES[capability]
  if (!hasAnyRole(subject, rule.roles)) return false
  return !rule.scope || hasAnyRole(subject, GLOBAL_SCOPE_ROLES[rule.scope])
}
