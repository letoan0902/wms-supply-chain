import { App, Breadcrumb, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import AssignWarehousesModal from './components/AssignWarehousesModal'
import UserDetailDrawer from './components/UserDetailDrawer'
import UserFormModal from './components/UserFormModal'
import UserMetricsCards from './components/UserMetricsCards'
import UserStatusConfirmModal from './components/UserStatusConfirmModal'
import UserTable from './components/UserTable'
import * as userApi from './userApi'
import type {
  AdminUpdateUserPayload,
  CreateUserPayload,
  UserDetail,
  UserMetrics,
} from './types'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import { getApiErrorMessage } from '../../services/api'
import { getStoredUser } from '../../utils/storage'

const { Title, Text } = Typography

export default function UsersPage() {
  const { message } = App.useApp()
  const currentUser = getStoredUser()

  // Danh sách users & phân trang
  const [users, setUsers] = useState<UserDetail[]>([])
  const [total, setTotal] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(false)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Danh sách kho
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Bộ lọc
  const [keyword, setKeyword] = useState<string>('')
  const [roleFilter, setRoleFilter] = useState<string | undefined>(undefined)
  const [warehouseFilter, setWarehouseFilter] = useState<number | undefined>(undefined)
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined)

  // Modal State
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<UserDetail | null>(null)
  const [formSubmitting, setFormSubmitting] = useState(false)

  const [isAssignOpen, setIsAssignOpen] = useState(false)
  const [assigningUser, setAssigningUser] = useState<UserDetail | null>(null)
  const [assignSubmitting, setAssignSubmitting] = useState(false)

  const [isDetailOpen, setIsDetailOpen] = useState(false)
  const [viewingUser, setViewingUser] = useState<UserDetail | null>(null)

  // Modal Xác nhận Trạng thái (Khóa / Mở khóa kèm Lý do)
  const [isStatusModalOpen, setIsStatusModalOpen] = useState(false)
  const [statusTargetUser, setStatusTargetUser] = useState<UserDetail | null>(null)
  const [statusTargetValue, setStatusTargetValue] = useState<boolean>(true)
  const [statusSubmitting, setStatusSubmitting] = useState(false)

  // 1. Tải danh sách người dùng từ Backend (kết hợp bộ lọc & phân trang)
  const loadUsers = useCallback(async () => {
    setLoading(true)
    try {
      const data = await userApi.fetchUsers({
        keyword: keyword.trim() || undefined,
        role: roleFilter || undefined,
        warehouseId: warehouseFilter,
        enabled: enabledFilter,
        page,
        size: pageSize,
      })
      setUsers(data?.content || [])
      setTotal(data?.totalElements || 0)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải danh sách người dùng từ máy chủ'))
    } finally {
      setLoading(false)
    }
  }, [keyword, roleFilter, warehouseFilter, enabledFilter, page, pageSize, message])

  // 2. Tải danh sách kho thực tế từ backend để hiển thị tên kho
  useEffect(() => {
    let ignore = false
    const initWarehouses = async () => {
      try {
        const whList = await fetchWarehouses()
        if (!ignore) {
          setWarehouses(whList || [])
        }
      } catch {
        // inventory-service có thể chưa chạy, giữ mảng rỗng để không crash trang
      }
    }
    void initWarehouses()
    return () => {
      ignore = true
    }
  }, [])

  // 3. Tải danh sách người dùng khi bộ lọc / phân trang thay đổi (có debounce nhẹ với keyword)
  useEffect(() => {
    const timer = setTimeout(() => {
      void loadUsers()
    }, 250)
    return () => clearTimeout(timer)
  }, [loadUsers])

  // 4. Tính toán Metrics thống kê
  const metrics: UserMetrics = useMemo(() => {
    const activeCount = users.filter((u) => u.enabled).length
    const lockedCount = users.filter((u) => !u.enabled).length
    const warehouseOpsCount = users.filter(
      (u) =>
        u.roles?.includes('WAREHOUSE_MANAGER') ||
        u.roles?.includes('WAREHOUSE_STAFF')
    ).length

    return {
      totalUsers: total || users.length,
      activeUsers: activeCount,
      lockedUsers: lockedCount,
      warehouseOpsUsers: warehouseOpsCount,
    }
  }, [users, total])

  // 5. Handlers
  const handlePageChange = (newPage: number, newPageSize: number) => {
    setPage(newPage)
    setPageSize(newPageSize)
  }

  const handleResetFilters = () => {
    setKeyword('')
    setRoleFilter(undefined)
    setWarehouseFilter(undefined)
    setEnabledFilter(undefined)
    setPage(0)
  }

  // Thêm mới / Sửa người dùng
  const handleOpenAddUser = () => {
    setEditingUser(null)
    setIsFormOpen(true)
  }

  const handleOpenEditUser = (user: UserDetail) => {
    setEditingUser(user)
    setIsFormOpen(true)
  }

  const handleFormSubmit = async (
    values: CreateUserPayload | AdminUpdateUserPayload
  ) => {
    setFormSubmitting(true)
    try {
      if (editingUser) {
        await userApi.updateUser(editingUser.id, values as AdminUpdateUserPayload)
        message.success(`Đã cập nhật thông tin tài khoản @${editingUser.username}`)
      } else {
        await userApi.createUser(values as CreateUserPayload)
        message.success('Tạo mới tài khoản người dùng thành công!')
      }
      setIsFormOpen(false)
      void loadUsers()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Thao tác không thành công'))
    } finally {
      setFormSubmitting(false)
    }
  }

  // Yêu cầu đổi trạng thái -> Mở Modal Confirm có ô nhập lý do
  const handleRequestToggleStatus = (user: UserDetail, nextStatus: boolean) => {
    setStatusTargetUser(user)
    setStatusTargetValue(nextStatus)
    setIsStatusModalOpen(true)
  }

  // Xác nhận đổi trạng thái từ Modal Confirm
  const handleConfirmStatusChange = async (
    user: UserDetail,
    nextStatus: boolean,
    reason: string
  ) => {
    setStatusSubmitting(true)
    try {
      await userApi.updateUserStatus(user.id, nextStatus)
      message.success(
        nextStatus
          ? `Đã mở khóa tài khoản @${user.username}`
          : `Đã tạm khóa tài khoản @${user.username} (${reason})`
      )
      setIsStatusModalOpen(false)
      void loadUsers()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể thay đổi trạng thái tài khoản'))
    } finally {
      setStatusSubmitting(false)
    }
  }

  // Phân quyền kho làm việc
  const handleOpenAssignWarehouses = (user: UserDetail) => {
    setAssigningUser(user)
    setIsAssignOpen(true)
  }

  const handleAssignWarehousesSubmit = async (
    userId: number,
    warehouseIds: number[]
  ) => {
    setAssignSubmitting(true)
    try {
      await userApi.assignWarehouses(userId, warehouseIds)
      message.success('Cập nhật phân quyền kho thành công!')
      setIsAssignOpen(false)
      void loadUsers()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể cập nhật phân quyền kho'))
    } finally {
      setAssignSubmitting(false)
    }
  }

  // Xem chi tiết hồ sơ
  const handleViewUser = (user: UserDetail) => {
    setViewingUser(user)
    setIsDetailOpen(true)
  }

  return (
    <div className="flex flex-col gap-5">
      {/* Page Header */}
      <div>
        <Breadcrumb
          items={[
            { title: 'Trang chủ' },
            { title: 'Quản lý người dùng & Phân quyền' },
          ]}
          className="text-xs mb-1"
        />
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <Title level={3} className="!mb-0 font-bold text-gray-900">
              Quản lý Người dùng & Phân quyền
            </Title>
            <Text type="secondary" className="text-xs">
              Quản lý tài khoản nhân sự toàn hệ thống, cấp vai trò RBAC và phân bổ phạm vi kho làm việc
            </Text>
          </div>
        </div>
      </div>

      {/* 4 Thẻ chỉ số tổng quan */}
      <UserMetricsCards metrics={metrics} loading={loading && users.length === 0} />

      {/* Bảng dữ liệu người dùng & Toolbar */}
      <UserTable
        users={users}
        total={total}
        loading={loading}
        page={page}
        pageSize={pageSize}
        keyword={keyword}
        roleFilter={roleFilter}
        warehouseFilter={warehouseFilter}
        enabledFilter={enabledFilter}
        warehouses={warehouses}
        currentUsername={currentUser?.username}
        onPageChange={handlePageChange}
        onSearchChange={(kw) => {
          setKeyword(kw)
          setPage(0)
        }}
        onRoleFilterChange={(role) => {
          setRoleFilter(role)
          setPage(0)
        }}
        onWarehouseFilterChange={(whId) => {
          setWarehouseFilter(whId)
          setPage(0)
        }}
        onEnabledFilterChange={(enabled) => {
          setEnabledFilter(enabled)
          setPage(0)
        }}
        onResetFilters={handleResetFilters}
        onAddUser={handleOpenAddUser}
        onEditUser={handleOpenEditUser}
        onViewUser={handleViewUser}
        onAssignWarehouses={handleOpenAssignWarehouses}
        onRequestToggleStatus={handleRequestToggleStatus}
        onRefresh={loadUsers}
      />

      {/* Modal Xác nhận Đổi trạng thái kèm Lý do */}
      <UserStatusConfirmModal
        open={isStatusModalOpen}
        user={statusTargetUser}
        targetStatus={statusTargetValue}
        confirmLoading={statusSubmitting}
        onConfirm={handleConfirmStatusChange}
        onCancel={() => setIsStatusModalOpen(false)}
      />

      {/* Modal Thêm mới / Chỉnh sửa */}
      <UserFormModal
        open={isFormOpen}
        editingUser={editingUser}
        warehouses={warehouses}
        confirmLoading={formSubmitting}
        onSubmit={handleFormSubmit}
        onCancel={() => setIsFormOpen(false)}
      />

      {/* Modal Phân quyền kho */}
      <AssignWarehousesModal
        open={isAssignOpen}
        user={assigningUser}
        warehouses={warehouses}
        confirmLoading={assignSubmitting}
        onSubmit={handleAssignWarehousesSubmit}
        onCancel={() => setIsAssignOpen(false)}
      />

      {/* Drawer Chi tiết hồ sơ */}
      <UserDetailDrawer
        open={isDetailOpen}
        user={viewingUser}
        warehouses={warehouses}
        onClose={() => setIsDetailOpen(false)}
        onEdit={(user) => {
          handleOpenEditUser(user)
        }}
      />
    </div>
  )
}
