import { CopyOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  Alert,
  App,
  Breadcrumb,
  Button,
  Form,
  Input,
  Modal,
  Result,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { useCan } from '../auth/useCan'
import * as apiKeyApi from './apiKeyApi'
import type { ApiKeyItem, CreatedApiKey } from './types'

const { Title, Text, Paragraph } = Typography

/** Số key đang hoạt động tối đa của một doanh nghiệp (khớp ApiKeyService.MAX_ACTIVE_KEYS ở auth-service). */
const MAX_ACTIVE_KEYS = 5

/** Lỗi gói dịch vụ cần hướng người dùng sang trang gói dịch vụ. */
interface PlanProblem {
  title: string
  description: string
}

export default function ApiKeysPage() {
  const { message, modal } = App.useApp()
  const navigate = useNavigate()
  // Quản lý API key: chỉ ADMIN (features/auth/permissions.ts, khớp @PreAuthorize của auth-service)
  const isAdmin = useCan()('apiKey.manage')

  const [keys, setKeys] = useState<ApiKeyItem[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [planProblem, setPlanProblem] = useState<PlanProblem | null>(null)
  const [createdKey, setCreatedKey] = useState<CreatedApiKey | null>(null)
  const [form] = Form.useForm<{ name: string }>()

  const activeCount = keys.filter((key) => key.active).length

  const loadKeys = useCallback(async () => {
    setLoading(true)
    try {
      setKeys(await apiKeyApi.fetchApiKeys())
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không tải được danh sách API key'))
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!isAdmin) return
    let ignore = false
    const run = async () => {
      setLoading(true)
      try {
        const data = await apiKeyApi.fetchApiKeys()
        if (!ignore) setKeys(data)
      } catch (err) {
        if (!ignore) message.error(getApiErrorMessage(err, 'Không tải được danh sách API key'))
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void run()
    return () => {
      ignore = true
    }
  }, [isAdmin, message])

  const openCreate = () => {
    setCreateError(null)
    setCreateOpen(true)
  }

  const handleCreate = async () => {
    let values: { name: string }
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setCreating(true)
    setCreateError(null)
    try {
      const created = await apiKeyApi.createApiKey({ name: values.name.trim() })
      setCreateOpen(false)
      setCreatedKey(created)
      void loadKeys()
    } catch (err) {
      const code = apiKeyApi.getApiErrorCode(err)
      if (code === 'PLAN_LIMIT_EXCEEDED') {
        setCreateOpen(false)
        setPlanProblem({
          title: 'Gói dịch vụ hiện tại không hỗ trợ API key',
          description:
            'API key chỉ có ở gói Dùng thử và gói Chuyên Nghiệp. Nâng cấp gói dịch vụ để kết nối hệ thống ngoài.',
        })
      } else if (code === 'SUBSCRIPTION_EXPIRED') {
        setCreateOpen(false)
        setPlanProblem({
          title: 'Gói dịch vụ đã hết hạn',
          description:
            'Không thể tạo API key mới khi gói dịch vụ đã hết hạn, các key hiện có cũng tạm ngưng hoạt động. Gia hạn gói để tiếp tục.',
        })
      } else {
        setCreateError(getApiErrorMessage(err, 'Không tạo được API key'))
      }
    } finally {
      setCreating(false)
    }
  }

  const copyKey = async (key: string) => {
    try {
      await navigator.clipboard.writeText(key)
      message.success('Đã sao chép API key')
    } catch {
      message.warning('Trình duyệt không cho sao chép tự động, hãy chọn và sao chép key thủ công')
    }
  }

  const confirmRevoke = (item: ApiKeyItem) => {
    modal.confirm({
      title: `Thu hồi API key "${item.name}"?`,
      content:
        'Hệ thống đang dùng key này sẽ bị từ chối (lỗi 401) ngay sau khi thu hồi. Thao tác này không hoàn tác được.',
      okText: 'Thu hồi',
      okType: 'danger',
      cancelText: 'Hủy bỏ',
      onOk: async () => {
        try {
          await apiKeyApi.revokeApiKey(item.id)
          message.success('Đã thu hồi API key')
          await loadKeys()
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không thu hồi được API key'))
        }
      },
    })
  }

  if (!isAdmin) {
    return (
      <div className="p-8 bg-white rounded-xl shadow-xs border border-slate-200">
        <Result
          status="403"
          title="403 - Quyền truy cập bị từ chối"
          subTitle="Chức năng quản lý API key chỉ dành cho Quản trị viên (ADMIN) của doanh nghiệp."
          extra={
            <Button type="primary" onClick={() => navigate('/')}>
              Quay lại Trang chủ
            </Button>
          }
        />
      </div>
    )
  }

  const columns: TableColumnsType<ApiKeyItem> = [
    {
      title: 'Tên',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <span className="font-medium text-gray-900">{name}</span>,
    },
    {
      title: 'Key',
      dataIndex: 'keyPrefix',
      key: 'keyPrefix',
      render: (prefix: string) => <Text code>{prefix}…</Text>,
    },
    {
      title: 'Trạng thái',
      key: 'status',
      render: (_, item) =>
        item.active ? <Tag color="green">Đang hoạt động</Tag> : <Tag color="default">Đã thu hồi</Tag>,
    },
    {
      title: 'Người tạo',
      dataIndex: 'createdBy',
      key: 'createdBy',
      render: (value?: string | null) => value || '—',
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Lần dùng gần nhất',
      dataIndex: 'lastUsedAt',
      key: 'lastUsedAt',
      render: (value?: string | null) => (value ? formatDateTime(value) : 'Chưa dùng'),
    },
    {
      title: 'Thu hồi lúc',
      dataIndex: 'revokedAt',
      key: 'revokedAt',
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      align: 'right',
      render: (_, item) =>
        item.active ? (
          <Button danger size="small" onClick={() => confirmRevoke(item)}>
            Thu hồi
          </Button>
        ) : null,
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      <div>
        <Breadcrumb items={[{ title: 'Trang chủ' }, { title: 'Cài đặt' }, { title: 'API key' }]} className="text-xs mb-1" />
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <Title level={3} className="!mb-0 font-bold text-gray-900">
              API key cho hệ thống ngoài
            </Title>
            <Text type="secondary" className="text-xs">
              Cho phép phần mềm khác (website bán hàng, kế toán, sàn thương mại điện tử) đọc dữ liệu của doanh nghiệp
            </Text>
          </div>
          <div className="flex gap-2">
            <Button icon={<ReloadOutlined />} onClick={() => void loadKeys()} loading={loading}>
              Tải lại
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
              disabled={activeCount >= MAX_ACTIVE_KEYS}
            >
              Tạo API key
            </Button>
          </div>
        </div>
      </div>

      <Alert
        type="info"
        showIcon
        message="Cách dùng"
        description={
          <div className="text-sm">
            Gửi key trong header <Text code>X-Api-Key</Text> khi gọi API qua cổng của hệ thống. Key chỉ được đọc
            (phương thức GET) danh mục hàng hóa, tồn kho và đơn hàng của doanh nghiệp, tối đa 20 yêu cầu mỗi giây.
            Mỗi doanh nghiệp có tối đa {MAX_ACTIVE_KEYS} key đang hoạt động (hiện có {activeCount}).
          </div>
        }
      />

      {planProblem && (
        <Alert
          type="warning"
          showIcon
          closable
          onClose={() => setPlanProblem(null)}
          message={planProblem.title}
          description={planProblem.description}
          action={
            <Button size="small" type="primary" onClick={() => navigate('/subscription')}>
              Xem gói dịch vụ
            </Button>
          }
        />
      )}

      <div className="bg-white rounded-xl border border-slate-200 p-4">
        <Table<ApiKeyItem>
          rowKey="id"
          columns={columns}
          dataSource={keys}
          loading={loading}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: 'Chưa có API key nào' }}
        />
      </div>

      <Modal
        title="Tạo API key"
        open={createOpen}
        onOk={() => void handleCreate()}
        onCancel={() => setCreateOpen(false)}
        okText="Tạo key"
        cancelText="Hủy bỏ"
        confirmLoading={creating}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false} onFinish={() => void handleCreate()}>
          <Form.Item
            name="name"
            label="Tên gợi nhớ"
            extra="Đặt theo hệ thống sẽ dùng key, ví dụ: Website bán hàng"
            rules={[
              { required: true, whitespace: true, message: 'Vui lòng nhập tên API key' },
              { min: 2, max: 100, message: 'Tên API key phải từ 2 đến 100 ký tự' },
            ]}
          >
            <Input maxLength={100} placeholder="Website bán hàng" autoFocus />
          </Form.Item>
        </Form>
        {createError && <Alert type="error" showIcon message={createError} />}
      </Modal>

      <Modal
        title="API key đã được tạo"
        open={createdKey !== null}
        closable={false}
        maskClosable={false}
        keyboard={false}
        onOk={() => setCreatedKey(null)}
        okText="Tôi đã lưu key"
        cancelButtonProps={{ style: { display: 'none' } }}
        width={600}
      >
        {createdKey && (
          <div className="flex flex-col gap-3">
            <Alert
              type="warning"
              showIcon
              message="Key chỉ hiển thị một lần"
              description="Hãy sao chép và lưu key ở nơi an toàn ngay bây giờ. Sau khi đóng hộp thoại này, hệ thống không thể hiển thị lại key; nếu làm mất, hãy thu hồi và tạo key mới."
            />
            <div>
              <Text type="secondary" className="text-xs">
                {createdKey.name}
              </Text>
              <Paragraph className="!mb-0 mt-1 rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-sm break-all">
                {createdKey.key}
              </Paragraph>
            </div>
            <Button icon={<CopyOutlined />} onClick={() => void copyKey(createdKey.key)}>
              Sao chép key
            </Button>
          </div>
        )}
      </Modal>
    </div>
  )
}
