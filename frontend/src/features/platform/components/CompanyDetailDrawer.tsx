import { CheckOutlined, CloseOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Drawer, Empty, Skeleton, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ReactNode } from 'react'
import { useCallback } from 'react'
import { formatCurrency } from '../../../utils/format'
import { formatInstantDate } from '../../inventory/inventoryUtils'
import { fetchPlatformCompany, type PlatformCompanyAdmin, type PlatformPayment } from '../platformApi'
import { formatVietnamDateTime, planLabel } from '../platformFormat'
import { useKeyedRequest } from '../useKeyedRequest'
import CompanyUsageList from './CompanyUsageList'
import type { StatusTarget } from './CompanyStatusModal'
import { CompanyStatusTag, PaymentStatusTag, SubscriptionStatusTag } from './StatusTags'

interface Props {
  /** Doanh nghiệp đang xem; null thì đóng */
  companyId: number | null
  /** Tăng lên để tải lại chi tiết (sau khi đổi trạng thái) */
  reloadToken: number
  onClose: () => void
  onRequestStatusChange: (target: StatusTarget) => void
  /** Duyệt bản đăng ký đang chờ (chỉ hiện khi PENDING) */
  onRequestApprove: (target: StatusTarget) => void
  /** Từ chối bản đăng ký đang chờ (chỉ hiện khi PENDING) */
  onRequestReject: (target: StatusTarget) => void
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <div className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2">{title}</div>
      {children}
    </section>
  )
}

const text = (value: string | null | undefined) => value || '—'

const paymentColumns: ColumnsType<PlatformPayment> = [
  { title: 'Mã đơn', dataIndex: 'orderCode', key: 'orderCode', render: (value: number) => <span className="font-mono text-xs">{value}</span> },
  { title: 'Gói', key: 'plan', render: (_, row) => <span className="text-xs">{planLabel(row.plan)}</span> },
  {
    title: 'Số tiền',
    key: 'amount',
    align: 'right',
    render: (_, row) => <span className="text-xs tabular-nums">{formatCurrency(row.amount)}</span>,
  },
  { title: 'Trạng thái', key: 'status', render: (_, row) => <PaymentStatusTag status={row.status} /> },
  {
    title: 'Tạo lúc',
    key: 'createdAt',
    render: (_, row) => <span className="text-xs text-slate-600 whitespace-nowrap">{formatVietnamDateTime(row.createdAt)}</span>,
  },
]

/** Chi tiết một doanh nghiệp: thông tin, gói và hạn, mức dùng, quản trị viên, 10 thanh toán gần nhất. */
export default function CompanyDetailDrawer({
  companyId,
  reloadToken,
  onClose,
  onRequestStatusChange,
  onRequestApprove,
  onRequestReject,
}: Props) {
  const load = useCallback(() => fetchPlatformCompany(companyId ?? 0), [companyId])
  const { data, error, loading } = useKeyedRequest(
    companyId === null ? null : `${companyId}:${reloadToken}`,
    load,
    'Không tải được chi tiết doanh nghiệp',
  )
  // Dữ liệu giữ lại của doanh nghiệp xem trước đó thì không hiển thị
  const detail = data && data.company.id === companyId ? data : null
  const company = detail?.company

  const action =
    company?.status === 'PENDING' ? (
      <Space>
        <Button type="primary" size="small" icon={<CheckOutlined />} onClick={() => onRequestApprove(company)}>
          Duyệt
        </Button>
        <Button danger size="small" icon={<CloseOutlined />} onClick={() => onRequestReject(company)}>
          Từ chối
        </Button>
      </Space>
    ) : company?.status === 'ACTIVE' ? (
      <Button danger size="small" icon={<LockOutlined />} onClick={() => onRequestStatusChange(company)}>
        Tạm khóa
      </Button>
    ) : company?.status === 'SUSPENDED' ? (
      <Button size="small" icon={<UnlockOutlined />} onClick={() => onRequestStatusChange(company)}>
        Mở khóa
      </Button>
    ) : null

  return (
    <Drawer
      title={company ? company.name : 'Chi tiết doanh nghiệp'}
      placement="right"
      width="min(600px, 100vw)"
      open={companyId !== null}
      onClose={onClose}
      extra={action}
    >
      {error && !detail ? (
        <Alert type="error" showIcon message={error} />
      ) : !detail ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <div className="flex flex-col gap-6">
          {error && <Alert type="error" showIcon message={error} />}

          <Section title="Thông tin doanh nghiệp">
            <Descriptions column={1} size="small" bordered className="bg-white">
              <Descriptions.Item label="Mã">
                <span className="font-mono text-xs">{detail.company.code}</span>
              </Descriptions.Item>
              <Descriptions.Item label="Tên">{detail.company.name}</Descriptions.Item>
              <Descriptions.Item label="Mã số thuế">{text(detail.company.taxCode)}</Descriptions.Item>
              <Descriptions.Item label="Email">{text(detail.company.email)}</Descriptions.Item>
              <Descriptions.Item label="Số điện thoại">{text(detail.company.phone)}</Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">{text(detail.company.address)}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <CompanyStatusTag status={detail.company.status} />
              </Descriptions.Item>
              <Descriptions.Item label="Ngày đăng ký">{formatVietnamDateTime(detail.company.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Ngày kích hoạt">{formatVietnamDateTime(detail.company.activatedAt)}</Descriptions.Item>
            </Descriptions>
          </Section>

          <Section title="Gói và hạn">
            <Descriptions column={1} size="small" bordered className="bg-white">
              <Descriptions.Item label="Gói">{planLabel(detail.company.plan, detail.company.planName)}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái thuê bao">
                <SubscriptionStatusTag status={detail.company.subscriptionStatus} />
              </Descriptions.Item>
              <Descriptions.Item label="Hạn">
                {detail.company.expiresAt
                  ? formatInstantDate(detail.company.expiresAt)
                  : detail.company.plan
                    ? 'Không thời hạn'
                    : '—'}
              </Descriptions.Item>
            </Descriptions>
          </Section>

          <Section title="Mức dùng so với giới hạn">
            <CompanyUsageList limits={detail.limits} usage={detail.usage} />
          </Section>

          <Section title="Quản trị viên">
            {detail.admins.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Doanh nghiệp chưa có quản trị viên" />
            ) : (
              <div className="flex flex-col gap-2">
                {detail.admins.map((admin: PlatformCompanyAdmin) => (
                  <div key={admin.id} className="flex items-start justify-between gap-3 p-3 bg-white border border-gray-200 rounded-lg">
                    <div className="min-w-0">
                      <div className="font-semibold text-gray-900 text-sm truncate">{admin.fullName || admin.username}</div>
                      <div className="text-xs text-gray-500 truncate">
                        <span className="font-mono">@{admin.username}</span>
                        {admin.email ? ` · ${admin.email}` : ''}
                      </div>
                    </div>
                    <Tag color={admin.enabled ? 'success' : 'error'} className="m-0 shrink-0">
                      {admin.enabled ? 'Đang hoạt động' : 'Tạm khóa'}
                    </Tag>
                  </div>
                ))}
              </div>
            )}
          </Section>

          <Section title="10 thanh toán gần nhất">
            <Table<PlatformPayment>
              rowKey="orderCode"
              columns={paymentColumns}
              dataSource={detail.payments}
              pagination={false}
              size="small"
              loading={loading}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: 'Chưa có giao dịch nào' }}
            />
          </Section>
        </div>
      )}
    </Drawer>
  )
}
