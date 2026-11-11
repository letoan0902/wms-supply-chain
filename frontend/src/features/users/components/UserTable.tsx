import {
  ApartmentOutlined,
  CheckCircleOutlined,
  EditOutlined,
  EyeOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ROLE_CONFIG, type UserDetail } from '../types'
import { formatDateTime } from '../../../utils/format'
import type { Warehouse } from '../../warehouses/types'

interface Props {
  users: UserDetail[]
  total: number
  loading: boolean
  page: number
  pageSize: number
  keyword: string
  roleFilter?: string
  warehouseFilter?: number
  enabledFilter?: boolean
  warehouses: Warehouse[]
  currentUsername?: string
  onPageChange: (page: number, pageSize: number) => void
  onSearchChange: (keyword: string) => void
  onRoleFilterChange: (role?: string) => void
  onWarehouseFilterChange: (warehouseId?: number) => void
  onEnabledFilterChange: (enabled?: boolean) => void
  onResetFilters: () => void
  onAddUser: () => void
  onEditUser: (user: UserDetail) => void
  onViewUser: (user: UserDetail) => void
  onAssignWarehouses: (user: UserDetail) => void
  onRequestToggleStatus: (user: UserDetail, nextStatus: boolean) => void
  onRefresh: () => void
}

export default function UserTable({
  users,
  total,
  loading,
  page,
  pageSize,
  keyword,
  roleFilter,
  warehouseFilter,
  enabledFilter,
  warehouses,
  currentUsername,
  onPageChange,
  onSearchChange,
  onRoleFilterChange,
  onWarehouseFilterChange,
  onEnabledFilterChange,
  onResetFilters,
  onAddUser,
  onEditUser,
  onViewUser,
  onAssignWarehouses,
  onRequestToggleStatus,
  onRefresh,
}: Props) {
  // Tạo lookup map warehouseId -> Warehouse Name
  const warehouseMap = new Map<number, Warehouse>()
  warehouses.forEach((w) => warehouseMap.set(w.id, w))

  const columns: ColumnsType<UserDetail> = [
    {
      title: 'Mã',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      align: 'center',
      render: (id: number) => <span className="text-gray-400 font-mono text-xs">#{id}</span>,
    },
    {
      title: 'Nhân sự',
      key: 'user',
      render: (_, record) => {
        const displayName = record.fullName || record.username
        const initial = displayName.charAt(0).toUpperCase()
        return (
          <div className="flex items-center gap-3">
            {/* Avatar nền trong suốt, viền xám tinh tế */}
            <div className="w-8 h-8 rounded-full border border-slate-200 bg-transparent flex items-center justify-center text-xs font-semibold text-slate-600 shrink-0 select-none">
              {initial || <UserOutlined className="text-slate-400" />}
            </div>
            <div>
              <div className="font-semibold text-gray-900 flex items-center gap-1.5">
                <span>{displayName}</span>
                {record.username === currentUsername && (
                  <Tag color="blue" className="text-[10px] py-0 px-1 font-normal">
                    Bạn
                  </Tag>
                )}
              </div>
              <div className="text-xs text-gray-500 font-mono">@{record.username}</div>
            </div>
          </div>
        )
      },
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (email: string) => (
        <span className="text-sm text-gray-700">{email || '—'}</span>
      ),
    },
    {
      title: 'Vai trò (RBAC)',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => (
        <Space size={[0, 4]} wrap>
          {roles?.map((role) => {
            const config = ROLE_CONFIG[role] || {
              label: role,
              color: 'default',
            }
            return (
              <Tag
                key={role}
                color={config.color}
                className="font-medium text-xs rounded-md px-2 py-0.5"
              >
                {config.label}
              </Tag>
            )
          })}
        </Space>
      ),
    },
    {
      title: 'Phạm vi kho',
      key: 'warehouses',
      render: (_, record) => {
        const isAdmin = record.roles?.includes('ADMIN')
        const isAccountant = record.roles?.includes('ACCOUNTANT')
        const assignedIds = record.warehouseIds || []

        if ((isAdmin || isAccountant) && assignedIds.length === 0) {
          return (
            <Tag className="text-xs text-gray-500 bg-gray-50 border-gray-200">
              Toàn cục (Tất cả kho)
            </Tag>
          )
        }

        if (assignedIds.length === 0) {
          return (
            <div className="flex items-center gap-1 text-xs text-amber-600">
              <span>Chưa gán kho</span>
              <Tooltip title="Gán kho làm việc">
                <Button
                  type="link"
                  size="small"
                  className="p-0 h-auto text-xs"
                  onClick={() => onAssignWarehouses(record)}
                >
                  Gán ngay
                </Button>
              </Tooltip>
            </div>
          )
        }

        return (
          <Space size={[0, 4]} wrap>
            {assignedIds.map((whId) => {
              const wh = warehouseMap.get(whId)
              return (
                <Tag
                  key={whId}
                  color="cyan"
                  className="text-xs rounded font-normal"
                >
                  {wh ? wh.name : `Kho #${whId}`}
                </Tag>
              )
            })}
          </Space>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 120,
      align: 'center',
      render: (enabled: boolean, record) => {
        const isSelf = record.username === currentUsername
        const switchNode = (
          <Switch
            checked={enabled}
            disabled={isSelf}
            checkedChildren={<CheckCircleOutlined />}
            unCheckedChildren={<LockOutlined />}
            onChange={(checked) => onRequestToggleStatus(record, checked)}
          />
        )

        return (
          <div className="flex flex-col items-center justify-center gap-1 py-0.5">
            {isSelf ? (
              <Tooltip title="Không thể tự khóa tài khoản của chính mình">
                <div>{switchNode}</div>
              </Tooltip>
            ) : (
              switchNode
            )}
            <span
              className={`text-[11px] font-medium leading-tight ${
                enabled ? 'text-green-600' : 'text-red-500'
              }`}
            >
              {enabled ? 'Hoạt động' : 'Tạm khóa'}
            </span>
          </div>
        )
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (date: string) => (
        <span className="text-xs text-gray-500">{formatDateTime(date)}</span>
      ),
    },
    {
      title: 'Hành động',
      key: 'actions',
      width: 130,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Xem chi tiết hồ sơ">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              className="text-gray-500 hover:text-blue-600"
              onClick={() => onViewUser(record)}
            />
          </Tooltip>
          <Tooltip title="Chỉnh sửa thông tin">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              className="text-gray-500 hover:text-blue-600"
              onClick={() => onEditUser(record)}
            />
          </Tooltip>
          <Tooltip title="Phân quyền kho làm việc">
            <Button
              type="text"
              size="small"
              icon={<ApartmentOutlined />}
              className="text-gray-500 hover:text-cyan-600"
              onClick={() => onAssignWarehouses(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <Card variant="borderless" className="shadow-xs">
      {/* Search & Filter Toolbar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap items-center gap-2">
          {/* Ô tìm kiếm từ khóa */}
          <Input
            placeholder="Tìm theo tên, username, email..."
            prefix={<SearchOutlined className="text-gray-400" />}
            value={keyword}
            onChange={(e) => onSearchChange(e.target.value)}
            allowClear
            className="w-64"
          />

          {/* Lọc vai trò */}
          <Select
            placeholder="Lọc vai trò"
            value={roleFilter}
            onChange={onRoleFilterChange}
            allowClear
            className="w-48"
            options={Object.entries(ROLE_CONFIG).map(([key, val]) => ({
              label: val.label,
              value: key,
            }))}
          />

          {/* Lọc theo kho */}
          <Select
            placeholder="Lọc theo kho"
            value={warehouseFilter}
            onChange={onWarehouseFilterChange}
            allowClear
            className="w-48"
            options={warehouses.map((w) => ({
              label: `${w.name} (${w.code})`,
              value: w.id,
            }))}
          />

          {/* Lọc trạng thái */}
          <Select
            placeholder="Trạng thái"
            value={enabledFilter}
            onChange={onEnabledFilterChange}
            allowClear
            className="w-36"
            options={[
              { label: 'Đang hoạt động', value: true },
              { label: 'Tạm khóa', value: false },
            ]}
          />

          {/* Nút đặt lại */}
          {(keyword || roleFilter !== undefined || warehouseFilter !== undefined || enabledFilter !== undefined) && (
            <Button onClick={onResetFilters} size="middle">
              Đặt lại
            </Button>
          )}
        </div>

        {/* Nút hành động chính */}
        <div className="flex items-center gap-2">
          <Button
            icon={<ReloadOutlined />}
            onClick={onRefresh}
            loading={loading}
          >
            Làm mới
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={onAddUser}
            className="bg-blue-600"
          >
            Thêm người dùng
          </Button>
        </div>
      </div>

      {/* Bảng dữ liệu */}
      <Table<UserDetail>
        columns={columns}
        dataSource={users}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (totalCount, range) =>
            `Hiển thị ${range[0]} - ${range[1]} của ${totalCount} tài khoản`,
          onChange: (newPage, newPageSize) =>
            onPageChange(newPage - 1, newPageSize),
        }}
      />
    </Card>
  )
}
