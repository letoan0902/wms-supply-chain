import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatDateTime } from '../../../utils/format'
import { fetchPayments } from '../paymentApi'
import { isSafeCheckoutUrl, PAYMENT_STATUS_COLORS, PAYMENT_STATUS_LABELS } from '../paymentLogic'
import { CYCLE_LABELS, PLAN_LABELS } from '../subscriptionFormat'
import type { PaymentInfo } from '../types'

const PAGE_SIZE = 10

/** Lịch sử giao dịch thanh toán của doanh nghiệp (GET /api/auth/payments, chỉ ADMIN), phân trang phía máy chủ. */
export default function PaymentHistoryTable() {
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [rows, setRows] = useState<PaymentInfo[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    fetchPayments(page, PAGE_SIZE)
      .then((result) => {
        if (ignore) return
        setRows(result.content)
        setTotal(result.totalElements)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(getApiErrorMessage(err, 'Không tải được lịch sử giao dịch'))
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [page, reloadKey])

  const reload = () => {
    setLoading(true)
    setReloadKey((key) => key + 1)
  }

  const columns: ColumnsType<PaymentInfo> = [
    {
      title: 'Mã đơn',
      dataIndex: 'orderCode',
      key: 'orderCode',
      render: (value: number) => <Typography.Text className="font-mono">{value}</Typography.Text>,
    },
    {
      title: 'Gói',
      dataIndex: 'plan',
      key: 'plan',
      render: (value: string) => PLAN_LABELS[value] ?? value,
    },
    {
      title: 'Chu kỳ',
      dataIndex: 'cycle',
      key: 'cycle',
      render: (value: string) => CYCLE_LABELS[value] ?? value,
    },
    {
      title: 'Số tiền',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (value: number) => formatCurrency(value),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      render: (value: string) => (
        <Tag color={PAYMENT_STATUS_COLORS[value] ?? 'default'}>{PAYMENT_STATUS_LABELS[value] ?? value}</Tag>
      ),
    },
    {
      title: 'Thời điểm',
      key: 'time',
      render: (_: unknown, row) => (
        <div className="flex flex-col">
          <span>{formatDateTime(row.createdAt)}</span>
          {row.paidAt && (
            <Typography.Text type="secondary" className="text-xs">
              Thanh toán lúc {formatDateTime(row.paidAt)}
            </Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_: unknown, row) => {
        if (row.status !== 'PENDING') return null
        const linkAlive = row.expiresAt ? new Date(row.expiresAt).getTime() > Date.now() : false
        return (
          <Space size="small" wrap>
            {linkAlive && isSafeCheckoutUrl(row.checkoutUrl) && (
              <Typography.Link href={row.checkoutUrl}>Tiếp tục thanh toán</Typography.Link>
            )}
            <Link to={`/payment/result?orderCode=${row.orderCode}`}>Kiểm tra trạng thái</Link>
          </Space>
        )
      },
    },
  ]

  return (
    <Card
      title="Lịch sử giao dịch"
      extra={
        <Button size="small" icon={<ReloadOutlined />} onClick={reload} loading={loading}>
          Tải lại
        </Button>
      }
    >
      {error && <Alert type="error" showIcon message={error} className="mb-3" />}
      <Table<PaymentInfo>
        rowKey="orderCode"
        columns={columns}
        dataSource={rows}
        loading={loading}
        size="middle"
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Chưa có giao dịch nào' }}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          showTotal: (count) => `${count} giao dịch`,
          onChange: (nextPage) => {
            setLoading(true)
            setPage(nextPage - 1)
          },
        }}
      />
    </Card>
  )
}
