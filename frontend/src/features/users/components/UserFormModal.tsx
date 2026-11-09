import { Form, Input, Modal, Select, Typography } from 'antd'
import { useEffect } from 'react'
import {
  ROLE_CONFIG,
  type AdminUpdateUserPayload,
  type CreateUserPayload,
  type UserDetail,
} from '../types'
import type { Warehouse } from '../../warehouses/types'

interface Props {
  open: boolean
  editingUser: UserDetail | null
  warehouses: Warehouse[]
  confirmLoading: boolean
  onSubmit: (values: CreateUserPayload | AdminUpdateUserPayload) => Promise<void>
  onCancel: () => void
}

export default function UserFormModal({
  open,
  editingUser,
  warehouses,
  confirmLoading,
  onSubmit,
  onCancel,
}: Props) {
  const [form] = Form.useForm()
  const isEdit = Boolean(editingUser)

  useEffect(() => {
    if (open) {
      if (editingUser) {
        form.setFieldsValue({
          username: editingUser.username,
          fullName: editingUser.fullName,
          email: editingUser.email,
          roles: editingUser.roles,
          warehouseIds: editingUser.warehouseIds || [],
          enabled: editingUser.enabled,
        })
      } else {
        form.resetFields()
        form.setFieldsValue({
          enabled: true,
          roles: ['WAREHOUSE_STAFF'],
          warehouseIds: [],
        })
      }
    }
  }, [open, editingUser, form])

  const handleOk = async () => {
    try {
      const values = await form.validateFields()
      await onSubmit(values)
    } catch {
      // Validate error handled by AntD form
    }
  }

  return (
    <Modal
      title={isEdit ? 'Chỉnh sửa tài khoản' : 'Tạo mới tài khoản'}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={confirmLoading}
      okText={isEdit ? 'Lưu thay đổi' : 'Tạo tài khoản'}
      cancelText="Hủy"
      width={560}
      style={{ top: 30 }}
      styles={{
        body: {
          maxHeight: 'calc(100vh - 120px)',
          overflowY: 'auto',
          paddingRight: 6,
        },
      }}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="mt-1">
        {/* Username */}
        <Form.Item
          label="Tên đăng nhập (Username)"
          name="username"
          rules={[
            { required: !isEdit, message: 'Vui lòng nhập tên đăng nhập' },
            { min: 3, max: 50, message: 'Tên đăng nhập từ 3 đến 50 ký tự' },
            {
              pattern: /^[a-zA-Z0-9._-]+$/,
              message: 'Tên đăng nhập chỉ chứa chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang',
            },
          ]}
          extra={
            isEdit ? (
              <Typography.Text type="secondary" className="text-xs">
                Tên đăng nhập không thể thay đổi sau khi tạo.
              </Typography.Text>
            ) : undefined
          }
        >
          <Input
            placeholder="VD: manager, staff_hn01..."
            disabled={isEdit}
            autoComplete="off"
          />
        </Form.Item>

        {/* Full Name */}
        <Form.Item
          label="Họ và tên"
          name="fullName"
          rules={[{ max: 100, message: 'Họ và tên tối đa 100 ký tự' }]}
        >
          <Input placeholder="VD: Lê Phú Toàn" />
        </Form.Item>

        {/* Email */}
        <Form.Item
          label="Địa chỉ Email"
          name="email"
          rules={[
            { required: true, message: 'Vui lòng nhập địa chỉ email' },
            { type: 'email', message: 'Địa chỉ email không hợp lệ' },
            { max: 100, message: 'Email tối đa 100 ký tự' },
          ]}
        >
          <Input placeholder="VD: toan.lp@wms.example.vn" />
        </Form.Item>

        {/* Mật khẩu (Chỉ khi tạo mới) */}
        {!isEdit && (
          <Form.Item
            label="Mật khẩu khởi tạo"
            name="password"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu khởi tạo' },
              { min: 6, max: 72, message: 'Mật khẩu từ 6 đến 72 ký tự' },
            ]}
          >
            <Input.Password placeholder="Tối thiểu 6 ký tự" />
          </Form.Item>
        )}

        {/* Roles (Multiple select) */}
        <Form.Item
          label="Vai trò (RBAC)"
          name="roles"
          rules={[{ required: true, message: 'Vui lòng chọn ít nhất một vai trò' }]}
        >
          <Select
            mode="multiple"
            placeholder="Chọn một hoặc nhiều vai trò"
            options={Object.entries(ROLE_CONFIG).map(([key, val]) => ({
              label: `${val.label} (${key})`,
              value: key,
            }))}
          />
        </Form.Item>

        {/* Warehouse Scope */}
        <Form.Item
          label="Phạm vi kho phụ trách (Warehouse Scope)"
          name="warehouseIds"
          extra={
            <Typography.Text type="secondary" className="text-xs">
              Áp dụng cho Quản lý & Nhân viên kho
            </Typography.Text>
          }
        >
          <Select
            mode="multiple"
            placeholder="Chọn danh sách kho được phân quyền làm việc"
            allowClear
            options={warehouses.map((w) => ({
              label: `${w.name} (${w.code})`,
              value: w.id,
            }))}
          />
        </Form.Item>

        {/* Status */}

      </Form>
    </Modal>
  )
}
