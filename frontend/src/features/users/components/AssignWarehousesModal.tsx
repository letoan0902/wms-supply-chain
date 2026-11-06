import { Alert, Checkbox, Divider, Modal, Space, Tag, Typography } from 'antd'
import { useState } from 'react'
import { ROLE_CONFIG, type UserDetail } from '../types'
import type { Warehouse } from '../../warehouses/types'

interface Props {
  open: boolean
  user: UserDetail | null
  warehouses: Warehouse[]
  confirmLoading: boolean
  onSubmit: (userId: number, warehouseIds: number[]) => Promise<void>
  onCancel: () => void
}

export default function AssignWarehousesModal({
  open,
  user,
  warehouses,
  confirmLoading,
  onSubmit,
  onCancel,
}: Props) {
  // Lưu prevUser để đồng bộ selectedIds khi user thay đổi mà không cần useEffect
  // (React official pattern: "Adjusting some state when a prop changes")
  const [prevUser, setPrevUser] = useState<UserDetail | null>(null)
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  if (user !== prevUser) {
    setPrevUser(user)
    setSelectedIds(user?.warehouseIds ? [...user.warehouseIds] : [])
  }

  const handleOk = async () => {
    if (!user || selectedIds.length === 0) return
    await onSubmit(user.id, selectedIds)
  }

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(warehouses.map((w) => w.id))
    } else {
      setSelectedIds([])
    }
  }

  if (!user) return null

  const isAllSelected =
    warehouses.length > 0 && selectedIds.length === warehouses.length
  const isIndeterminate =
    selectedIds.length > 0 && selectedIds.length < warehouses.length

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <span>Phân quyền kho làm việc</span>
          <span className="text-gray-400 font-mono font-normal text-sm">
            @{user.username}
          </span>
        </div>
      }
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={confirmLoading}
      okText="Lưu phân quyền"
      okButtonProps={{
        disabled: selectedIds.length === 0,
      }}
      cancelText="Hủy"
      width={600}
      style={{ top: 20 }}
      destroyOnClose
    >
      <div className="mt-3 flex flex-col gap-3">
        {/* User preview header */}
        <div className="bg-gray-50 p-3 rounded-lg border border-gray-100">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-semibold text-gray-900">
                {user.fullName || user.username}
              </div>
              <div className="text-xs text-gray-500">{user.email}</div>
            </div>
            <Space size="small">
              {user.roles?.map((r) => (
                <Tag
                  key={r}
                  color={ROLE_CONFIG[r]?.color || 'default'}
                  className="text-xs"
                >
                  {ROLE_CONFIG[r]?.label || r}
                </Tag>
              ))}
            </Space>
          </div>
        </div>

        <Alert
          type="info"
          showIcon
          message="Phạm vi dữ liệu theo kho (Warehouse Scope)"
          description="Người dùng chỉ được xem và thao tác trên Vị trí kệ, Tồn kho, Phiếu nhập kho, Phiếu xuất kho và Phiên kiểm kê thuộc đúng các nhà kho được tích chọn bên dưới."
          className="text-xs"
        />

        {selectedIds.length === 0 && (
          <Alert
            type="warning"
            showIcon
            message="Chưa chọn nhà kho nào"
            description="Quy định hệ thống yêu cầu nhân sự phải được gán ít nhất một nhà kho. Vui lòng chọn tối thiểu 1 kho bên dưới để có thể lưu."
            className="text-xs"
          />
        )}

        {/* Checkbox select all */}
        <div className="flex items-center justify-between px-1">
          <Checkbox
            indeterminate={isIndeterminate}
            checked={isAllSelected}
            onChange={(e) => handleSelectAll(e.target.checked)}
          >
            <span className="font-medium text-sm">
              Chọn tất cả ({selectedIds.length}/{warehouses.length} kho)
            </span>
          </Checkbox>
        </div>

        <Divider className="my-1" />

        {/* Danh sách các kho */}
        <div className="max-h-64 overflow-y-auto flex flex-col gap-2 p-1">
          {warehouses.map((wh) => {
            const isChecked = selectedIds.includes(wh.id)
            return (
              <div
                key={wh.id}
                className={`flex items-start gap-3 p-2.5 rounded-lg border transition-colors cursor-pointer ${
                  isChecked
                    ? 'border-blue-300 bg-blue-50/50'
                    : 'border-gray-200 hover:border-gray-300 bg-white'
                }`}
                onClick={() => {
                  if (isChecked) {
                    setSelectedIds(selectedIds.filter((id) => id !== wh.id))
                  } else {
                    setSelectedIds([...selectedIds, wh.id])
                  }
                }}
              >
                <Checkbox
                  checked={isChecked}
                  onChange={(e) => {
                    e.stopPropagation()
                    if (e.target.checked) {
                      setSelectedIds([...selectedIds, wh.id])
                    } else {
                      setSelectedIds(selectedIds.filter((id) => id !== wh.id))
                    }
                  }}
                />
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-gray-900 text-sm">
                      {wh.name}
                    </span>
                    <span className="font-mono text-xs text-blue-700 bg-blue-100 px-1.5 py-0.2 rounded">
                      {wh.code}
                    </span>
                  </div>
                  <Typography.Text type="secondary" className="text-xs">
                    {wh.address || 'Chưa cập nhật địa chỉ'}
                  </Typography.Text>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </Modal>
  )
}
