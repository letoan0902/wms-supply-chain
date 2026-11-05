import {
  ApartmentOutlined,
  CheckCircleOutlined,
  EditOutlined,
  IdcardOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Button,
  Descriptions,
  Drawer,
  Tag,
} from 'antd'
import { ROLE_CONFIG, type UserDetail } from '../types'
import { formatDateTime } from '../../../utils/format'
import type { Warehouse } from '../../warehouses/types'

interface Props {
  open: boolean
  user: UserDetail | null
  warehouses: Warehouse[]
  onClose: () => void
  onEdit: (user: UserDetail) => void
}

export default function UserDetailDrawer({
  open,
  user,
  warehouses,
  onClose,
  onEdit,
}: Props) {
  if (!user) return null

  const warehouseMap = new Map<number, Warehouse>()
  warehouses.forEach((w) => warehouseMap.set(w.id, w))

  const assignedWarehouses = (user.warehouseIds || [])
    .map((id) => warehouseMap.get(id))
    .filter(Boolean) as Warehouse[]

  const displayName = user.fullName || user.username
  const initial = displayName.charAt(0).toUpperCase()

  return (
    <Drawer
      title="Hồ sơ nhân sự chi tiết"
      placement="right"
      width={480}
      onClose={onClose}
      open={open}
      extra={
        <Button
          type="primary"
          icon={<EditOutlined />}
          size="small"
          onClick={() => {
            onClose()
            onEdit(user)
          }}
          className="bg-blue-600"
        >
          Chỉnh sửa
        </Button>
      }
    >
      <div className="flex flex-col gap-6">
        {/* Header Profile Box */}
        <div className="flex items-center gap-4 bg-gray-50 p-4 rounded-xl border border-gray-100">
          <div className="w-16 h-16 rounded-full border-2 border-slate-200 bg-transparent flex items-center justify-center text-xl font-bold text-slate-700 shrink-0 select-none">
            {initial || <UserOutlined className="text-slate-400" />}
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="font-bold text-gray-900 text-lg truncate mb-0">
                {displayName}
              </h3>
            </div>
            <div className="text-sm text-gray-500 font-mono">@{user.username}</div>
            <div className="mt-1">
              <Tag
                color={user.enabled ? 'success' : 'error'}
                icon={user.enabled ? <CheckCircleOutlined /> : <LockOutlined />}
                className="text-xs px-2 py-0.5 rounded-full"
              >
                {user.enabled ? 'Đang hoạt động' : 'Tài khoản bị khóa'}
              </Tag>
            </div>
          </div>
        </div>

        {/* Thông tin định danh & Liên hệ */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2 flex items-center gap-1.5">
            <IdcardOutlined />
            <span>Thông tin tài khoản</span>
          </div>
          <Descriptions column={1} size="small" bordered className="bg-white">
            <Descriptions.Item label="Mã định danh">
              <span className="font-mono text-xs text-gray-600">#{user.id}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Email">
              <span className="text-gray-800">{user.email || '—'}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Ngày tạo">
              <span className="text-gray-600 text-xs">
                {formatDateTime(user.createdAt)}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="Cập nhật gần nhất">
              <span className="text-gray-600 text-xs">
                {formatDateTime(user.updatedAt)}
              </span>
            </Descriptions.Item>
          </Descriptions>
        </div>

        {/* Phân quyền vai trò (RBAC) */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2 flex items-center gap-1.5">
            <SafetyCertificateOutlined />
            <span>Phân quyền vai trò (RBAC)</span>
          </div>
          <div className="flex flex-col gap-2">
            {user.roles?.map((r) => {
              const cfg = ROLE_CONFIG[r] || {
                label: r,
                color: 'default',
                description: 'Vai trò tùy chỉnh hệ thống',
              }
              return (
                <div
                  key={r}
                  className="p-3 bg-white border border-gray-200 rounded-lg"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-semibold text-gray-900 text-sm">
                      {cfg.label}
                    </span>
                    <Tag color={cfg.color} className="font-mono text-xs">
                      {r}
                    </Tag>
                  </div>
                  <div className="text-xs text-gray-500">{cfg.description}</div>
                </div>
              )
            })}
          </div>
        </div>

        {/* Phạm vi kho phụ trách (Warehouse Scope) */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2 flex items-center gap-1.5">
            <ShopOutlined />
            <span>Phạm vi kho phụ trách (Warehouse Scope)</span>
          </div>

          {assignedWarehouses.length === 0 ? (
            <div className="p-4 bg-gray-50 border border-dashed border-gray-200 rounded-lg text-center text-xs text-gray-500">
              {user.roles?.includes('ADMIN') || user.roles?.includes('ACCOUNTANT')
                ? 'Tài khoản có quyền truy cập toàn cục trên tất cả kho hàng của hệ thống.'
                : 'Chưa có kho nào được phân quyền cho tài khoản này.'}
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              {assignedWarehouses.map((wh) => (
                <div
                  key={wh.id}
                  className="p-3 bg-white border border-gray-200 rounded-lg flex items-start gap-3"
                >
                  <div className="p-2 bg-blue-50 text-blue-600 rounded-lg text-base">
                    <ApartmentOutlined />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-gray-900 text-sm">
                        {wh.name}
                      </span>
                      <span className="font-mono text-xs text-blue-700 bg-blue-100 px-1.5 py-0.2 rounded">
                        {wh.code}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500 mt-0.5">
                      {wh.address || 'Chưa cập nhật địa chỉ'}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Drawer>
  )
}
