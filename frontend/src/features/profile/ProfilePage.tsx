import {
  ApartmentOutlined,
  CheckCircleOutlined,
  EditOutlined,
  IdcardOutlined,
  KeyOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  ShopOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Breadcrumb,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  changePassword,
  fetchProfile,
  updateProfile,
  type ChangePasswordPayload,
  type UpdateProfilePayload,
  type UserProfile,
} from './profileApi'
import { ROLE_CONFIG } from '../users/types'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import { getApiErrorMessage } from '../../services/api'
import { updateStoredUser } from '../../utils/storage'

const { Title, Text } = Typography

export default function ProfilePage() {
  const { message } = App.useApp()
  const [searchParams, setSearchParams] = useSearchParams()
  const currentTab = searchParams.get('tab') || 'info'

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [loading, setLoading] = useState<boolean>(true)

  // Form states
  const [updateForm] = Form.useForm<UpdateProfilePayload>()
  const [passwordForm] = Form.useForm<ChangePasswordPayload>()
  const [updateLoading, setUpdateLoading] = useState(false)
  const [passwordLoading, setPasswordLoading] = useState(false)

  // Tải dữ liệu hồ sơ cá nhân và danh sách kho
  useEffect(() => {
    let ignore = false
    const initData = async () => {
      setLoading(true)
      try {
        const [profileData, whList] = await Promise.all([
          fetchProfile(),
          fetchWarehouses().catch(() => []),
        ])
        if (!ignore) {
          setProfile(profileData)
          setWarehouses(whList || [])
          updateForm.setFieldsValue({
            fullName: profileData.fullName,
            email: profileData.email,
          })
        }
      } catch (err) {
        message.error(getApiErrorMessage(err, 'Không thể tải thông tin hồ sơ cá nhân'))
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void initData()
    return () => {
      ignore = true
    }
  }, [message, updateForm])

  // Xử lý Cập nhật thông tin cá nhân
  const handleUpdateProfile = async (values: UpdateProfilePayload) => {
    setUpdateLoading(true)
    try {
      const updated = await updateProfile(values)
      setProfile(updated)
      updateStoredUser({
        fullName: updated.fullName,
        email: updated.email,
      })
      message.success('Cập nhật thông tin cá nhân thành công!')
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể cập nhật thông tin'))
    } finally {
      setUpdateLoading(false)
    }
  }

  // Xử lý Đổi mật khẩu
  const handleChangePassword = async (values: ChangePasswordPayload) => {
    setPasswordLoading(true)
    try {
      await changePassword(values)
      message.success('Đổi mật khẩu thành công! Vui lòng ghi nhớ mật khẩu mới.')
      passwordForm.resetFields()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Đổi mật khẩu thất bại'))
    } finally {
      setPasswordLoading(false)
    }
  }

  // Mapping kho
  const warehouseMap = new Map<number, Warehouse>()
  warehouses.forEach((w) => warehouseMap.set(w.id, w))

  const assignedWarehouses = (profile?.warehouseIds || [])
    .map((id) => warehouseMap.get(id))
    .filter(Boolean) as Warehouse[]

  const displayName = profile?.fullName || profile?.username || '—'
  const initial = displayName.charAt(0).toUpperCase()

  if (loading) {
    return (
      <div className="flex flex-col gap-4 w-full">
        <Skeleton active avatar paragraph={{ rows: 4 }} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5 w-full">
      {/* Header Breadcrumb */}
      <div>
        <Breadcrumb
          items={[
            { title: 'Trang chủ' },
            { title: 'Tài khoản người dùng' },
            { title: 'Hồ sơ cá nhân' },
          ]}
          className="text-xs mb-1"
        />
        <Title level={3} className="!mb-0 font-bold text-gray-900">
          Hồ sơ & Tài khoản cá nhân
        </Title>
        <Text type="secondary" className="text-xs">
          Xem thông tin quyền hạn, cập nhật thông tin liên hệ và quản lý bảo mật mật khẩu
        </Text>
      </div>

      {/* Profile Overview Card */}
      <Card variant="borderless" className="shadow-xs border border-slate-200/80">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-full border-2 border-slate-200 bg-transparent flex items-center justify-center text-xl font-bold text-slate-700 shrink-0 select-none">
              {initial || <UserOutlined className="text-slate-400" />}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-gray-900 mb-0">
                  {displayName}
                </h2>
                <Tag
                  color={profile?.enabled ? 'success' : 'error'}
                  icon={profile?.enabled ? <CheckCircleOutlined /> : <LockOutlined />}
                  className="text-xs"
                >
                  {profile?.enabled ? 'Đang hoạt động' : 'Tạm khóa'}
                </Tag>
              </div>
              <div className="text-xs text-gray-500 font-mono mt-0.5">
                @{profile?.username} • {profile?.email}
              </div>
              <div className="flex flex-wrap gap-1 mt-2">
                {profile?.roles?.map((r) => {
                  const cfg = ROLE_CONFIG[r] || { label: r, color: 'default' }
                  return (
                    <Tag key={r} color={cfg.color} className="text-xs font-medium">
                      {cfg.label}
                    </Tag>
                  )
                })}
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* Tabs Container */}
      <Card variant="borderless" className="shadow-xs border border-slate-200/80">
        <Tabs
          activeKey={currentTab}
          onChange={(key) => setSearchParams({ tab: key })}
          items={[
            {
              key: 'info',
              label: (
                <span className="flex items-center gap-1.5 font-medium">
                  <IdcardOutlined />
                  Thông tin tài khoản
                </span>
              ),
              children: (
                <div className="flex flex-col gap-6 pt-2">
                  <Descriptions
                    bordered
                    size="small"
                    column={{ xs: 1, sm: 2, lg: 3 }}
                    className="bg-white"
                  >
                    <Descriptions.Item label="Mã nhân sự">
                      <span className="font-mono text-xs text-gray-700">#{profile?.id}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Tên đăng nhập">
                      <span className="font-mono text-xs font-semibold text-gray-800">
                        @{profile?.username}
                      </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Họ và tên">
                      <span className="text-gray-900 font-medium">{profile?.fullName || '—'}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Email liên hệ">
                      <span className="text-gray-800">{profile?.email || '—'}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Trạng thái">
                      <Tag color={profile?.enabled ? 'success' : 'error'}>
                        {profile?.enabled ? 'Hoạt động (ACTIVE)' : 'Tạm khóa (LOCKED)'}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Vai trò RBAC">
                      <Space size={[0, 4]} wrap>
                        {profile?.roles?.map((r) => (
                          <Tag key={r} color={ROLE_CONFIG[r]?.color || 'default'}>
                            {ROLE_CONFIG[r]?.label || r}
                          </Tag>
                        ))}
                      </Space>
                    </Descriptions.Item>
                  </Descriptions>

                  {/* Kho phụ trách */}
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2 flex items-center gap-1.5">
                      <ShopOutlined />
                      <span>Phạm vi kho phụ trách (Warehouse Scope)</span>
                    </div>

                    {assignedWarehouses.length === 0 ? (
                      <div className="p-3 bg-gray-50 border border-gray-200 rounded-lg text-xs text-gray-500">
                        {profile?.roles?.includes('ADMIN') || profile?.roles?.includes('ACCOUNTANT')
                          ? 'Tài khoản có quyền truy cập toàn cục trên tất cả các kho bãi trong hệ thống.'
                          : 'Chưa được phân quyền kho cụ thể.'}
                      </div>
                    ) : (
                      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                        {assignedWarehouses.map((wh) => (
                          <div
                            key={wh.id}
                            className="p-3 bg-white border border-gray-200 rounded-lg flex items-start gap-2.5"
                          >
                            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg text-base">
                              <ApartmentOutlined />
                            </div>
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5">
                                <span className="font-semibold text-gray-900 text-sm truncate">
                                  {wh.name}
                                </span>
                                <span className="font-mono text-xs text-blue-700 bg-blue-100 px-1.5 py-0.2 rounded shrink-0">
                                  {wh.code}
                                </span>
                              </div>
                              <div className="text-xs text-gray-500 truncate mt-0.5">
                                {wh.address || 'Chưa cập nhật địa chỉ'}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ),
            },
            {
              key: 'edit',
              label: (
                <span className="flex items-center gap-1.5 font-medium">
                  <EditOutlined />
                  Cập nhật thông tin
                </span>
              ),
              children: (
                <div className="max-w-xl pt-2">
                  <Alert
                    type="info"
                    showIcon
                    message="Cập nhật thông tin cá nhân"
                    description="Tên đăng nhập không thể thay đổi. Thông tin Họ tên và Email sẽ được cập nhật trên toàn hệ thống sau khi lưu."
                    className="text-xs mb-5"
                  />

                  <Form
                    form={updateForm}
                    layout="vertical"
                    onFinish={handleUpdateProfile}
                  >
                    <Form.Item label="Tên đăng nhập (Username)">
                      <Input
                        value={profile?.username}
                        disabled
                        className="bg-gray-50 text-gray-600 font-mono"
                      />
                    </Form.Item>

                    <Form.Item
                      label="Họ và tên"
                      name="fullName"
                      rules={[
                        { required: true, message: 'Vui lòng nhập họ và tên' },
                        { max: 100, message: 'Họ và tên tối đa 100 ký tự' },
                      ]}
                    >
                      <Input placeholder="Nhập họ và tên đầy đủ" />
                    </Form.Item>

                    <Form.Item
                      label="Địa chỉ Email"
                      name="email"
                      rules={[
                        { required: true, message: 'Vui lòng nhập email' },
                        { type: 'email', message: 'Địa chỉ email không hợp lệ' },
                        { max: 100, message: 'Email tối đa 100 ký tự' },
                      ]}
                    >
                      <Input placeholder="Nhập địa chỉ email" />
                    </Form.Item>

                    <Form.Item className="mt-6">
                      <Button
                        type="primary"
                        htmlType="submit"
                        icon={<SaveOutlined />}
                        loading={updateLoading}
                        className="bg-blue-600"
                      >
                        Lưu thay đổi
                      </Button>
                    </Form.Item>
                  </Form>
                </div>
              ),
            },
            {
              key: 'password',
              label: (
                <span className="flex items-center gap-1.5 font-medium">
                  <KeyOutlined />
                  Đổi mật khẩu
                </span>
              ),
              children: (
                <div className="max-w-xl pt-2">
                  <Alert
                    type="warning"
                    showIcon
                    message="Bảo mật tài khoản"
                    description="Mật khẩu mới phải từ 6 đến 72 ký tự. Sau khi đổi mật khẩu, vui lòng ghi nhớ và không chia sẻ mật khẩu cho người khác."
                    className="text-xs mb-5"
                  />

                  <Form
                    form={passwordForm}
                    layout="vertical"
                    onFinish={handleChangePassword}
                  >
                    <Form.Item
                      label="Mật khẩu hiện tại"
                      name="currentPassword"
                      rules={[
                        { required: true, message: 'Vui lòng nhập mật khẩu hiện tại' },
                      ]}
                    >
                      <Input.Password placeholder="Nhập mật khẩu đang dùng" />
                    </Form.Item>

                    <Form.Item
                      label="Mật khẩu mới"
                      name="newPassword"
                      rules={[
                        { required: true, message: 'Vui lòng nhập mật khẩu mới' },
                        { min: 6, max: 72, message: 'Mật khẩu mới phải từ 6 đến 72 ký tự' },
                      ]}
                    >
                      <Input.Password placeholder="Tối thiểu 6 ký tự" />
                    </Form.Item>

                    <Form.Item
                      label="Xác nhận mật khẩu mới"
                      name="confirmPassword"
                      dependencies={['newPassword']}
                      rules={[
                        { required: true, message: 'Vui lòng xác nhận mật khẩu mới' },
                        ({ getFieldValue }) => ({
                          validator(_, value) {
                            if (!value || getFieldValue('newPassword') === value) {
                              return Promise.resolve()
                            }
                            return Promise.reject(new Error('Mật khẩu xác nhận không khớp!'))
                          },
                        }),
                      ]}
                    >
                      <Input.Password placeholder="Nhập lại mật khẩu mới" />
                    </Form.Item>

                    <Form.Item className="mt-6">
                      <Button
                        type="primary"
                        htmlType="submit"
                        icon={<SafetyCertificateOutlined />}
                        loading={passwordLoading}
                        className="bg-blue-600"
                      >
                        Cập nhật mật khẩu mới
                      </Button>
                    </Form.Item>
                  </Form>
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
