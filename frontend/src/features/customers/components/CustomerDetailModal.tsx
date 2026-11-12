import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarCircleOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  InfoCircleOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Col,
  Descriptions,
  InputNumber,
  Modal,
  Progress,
  Row,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { checkCustomerCredit, fetchCustomerTransactions } from '../customerApi'
import type {
  Customer,
  CustomerCreditCheckResponse,
  CustomerTransaction,
  CustomerTransactionType,
} from '../types'

interface CustomerDetailModalProps {
  open: boolean
  customer: Customer | null
  onClose: () => void
  onOpenRecordTransaction?: (customer: Customer) => void
  canRecordTransaction?: boolean
}

const formatCurrency = (val?: number): string =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val ?? 0)

const formatDateTime = (dateStr?: string): string => {
  if (!dateStr) return '-'
  try {
    return new Date(dateStr).toLocaleString('vi-VN')
  } catch {
    return dateStr
  }
}

const TRANSACTION_TYPE_MAP: Record<
  CustomerTransactionType,
  { label: string; color: string; prefix: string }
> = {
  SALES_INVOICE: { label: 'Hóa đơn bán hàng', color: 'volcano', prefix: '+' },
  PAYMENT: { label: 'Thu tiền khách hàng', color: 'success', prefix: '-' },
  RETURN: { label: 'Khách trả hàng', color: 'blue', prefix: '-' },
  ADJUSTMENT: { label: 'Điều chỉnh số dư', color: 'purple', prefix: '±' },
}

export default function CustomerDetailModal({
  open,
  customer,
  onClose,
  onOpenRecordTransaction,
  canRecordTransaction = false,
}: CustomerDetailModalProps) {
  // Simulator state (FR14 Credit Check)
  const [simulatorAmount, setSimulatorAmount] = useState<number | null>(10000000)
  const [simulatorLoading, setSimulatorLoading] = useState(false)
  const [simulatorResult, setSimulatorResult] = useState<CustomerCreditCheckResponse | null>(null)

  // Transactions tab state
  const [transactions, setTransactions] = useState<CustomerTransaction[]>([])
  const [txTotal, setTxTotal] = useState(0)
  const [txLoading, setTxLoading] = useState(false)
  const [txPage, setTxPage] = useState(0)
  const [txPageSize, setTxPageSize] = useState(10)

  // Reset trang về 0 khi đổi khách hàng mà không dùng effect gây cascading render
  const [prevCustomerId, setPrevCustomerId] = useState(customer?.id)
  if (customer?.id !== prevCustomerId) {
    setPrevCustomerId(customer?.id)
    setTxPage(0)
  }

  useEffect(() => {
    let ignore = false
    if (!open || !customer) {
      return
    }

    const fetchTransactions = async () => {
      setTxLoading(true)
      try {
        const data = await fetchCustomerTransactions(customer.id, {
          page: txPage,
          size: txPageSize,
        })
        if (!ignore) {
          setTransactions(data.content || [])
          setTxTotal(data.totalElements || 0)
        }
      } catch {
        if (!ignore) {
          setTransactions([])
          setTxTotal(0)
        }
      } finally {
        if (!ignore) {
          setTxLoading(false)
        }
      }
    }

    void fetchTransactions()
    return () => {
      ignore = true
    }
  }, [open, customer, txPage, txPageSize])

  const handleClose = () => {
    setSimulatorResult(null)
    setSimulatorAmount(10000000)
    setTxPage(0)
    onClose()
  }

  // Credit simulator test action
  const handleRunSimulator = async () => {
    if (!customer) return
    setSimulatorLoading(true)
    try {
      const res = await checkCustomerCredit(customer.id, simulatorAmount ?? 0)
      setSimulatorResult(res)
    } catch {
      setSimulatorResult(null)
    } finally {
      setSimulatorLoading(false)
    }
  }

  if (!customer) return null

  // Tính toán tỷ lệ sử dụng công nợ
  const limit = customer.creditLimit || 0
  const debt = customer.currentDebt || 0
  const usagePercent = limit > 0 ? Math.min(Math.round((debt / limit) * 100), 100) : 0
  const actualRatio = limit > 0 ? (debt / limit) * 100 : 0
  const isOverLimit = limit > 0 && debt > limit
  const isNearLimit = limit > 0 && debt >= limit * 0.9 && !isOverLimit

  const progressStatus: 'success' | 'normal' | 'exception' =
    isOverLimit || customer.debtLocked
      ? 'exception'
      : isNearLimit
      ? 'normal'
      : 'success'

  // Cột cho bảng lịch sử giao dịch
  const txColumns: ColumnsType<CustomerTransaction> = [
    {
      title: 'Thời gian',
      dataIndex: 'transactionDate',
      key: 'transactionDate',
      width: 150,
      render: (val: string, r) => formatDateTime(val || r.createdAt),
    },
    {
      title: 'Loại phát sinh',
      dataIndex: 'transactionType',
      key: 'transactionType',
      width: 170,
      render: (type: CustomerTransactionType) => {
        const meta = TRANSACTION_TYPE_MAP[type] ?? {
          label: type,
          color: 'default',
          prefix: '',
        }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: 'Số tiền phát sinh',
      dataIndex: 'amount',
      key: 'amount',
      width: 160,
      align: 'right',
      render: (amt: number, r) => {
        const meta = TRANSACTION_TYPE_MAP[r.transactionType]
        const isDeduction = r.transactionType === 'PAYMENT' || r.transactionType === 'RETURN'
        return (
          <span
            className={`font-semibold ${
              isDeduction ? 'text-emerald-600' : 'text-red-600'
            }`}
          >
            {meta?.prefix} {formatCurrency(amt)}
          </span>
        )
      },
    },
    {
      title: 'Số dư nợ sau GD',
      dataIndex: 'balanceAfter',
      key: 'balanceAfter',
      width: 160,
      align: 'right',
      render: (val: number) => (
        <span className="font-semibold text-gray-800">{formatCurrency(val)}</span>
      ),
    },
    {
      title: 'Mã tham chiếu / Hóa đơn',
      dataIndex: 'referenceCode',
      key: 'referenceCode',
      width: 160,
      render: (val?: string) =>
        val ? (
          <Tag>{val}</Tag>
        ) : (
          <span className="text-gray-400">-</span>
        ),
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      key: 'note',
      ellipsis: true,
      render: (val?: string) => val || <span className="text-gray-400">-</span>,
    },
  ]

  return (
    <Modal
      open={open}
      onCancel={handleClose}
      footer={[
        canRecordTransaction && (
          <Button
            key="record"
            type="primary"
            icon={<DollarCircleOutlined />}
            onClick={() => {
              handleClose()
              onOpenRecordTransaction?.(customer)
            }}
          >
            Ghi nhận giao dịch
          </Button>
        ),
        <Button key="close" onClick={handleClose}>
          Đóng
        </Button>,
      ]}
      width={900}
      title={
        <div className="flex items-center justify-between pr-8">
          <div className="flex items-center gap-2">
            <UserOutlined className="text-blue-600 text-lg" />
            <div>
              <span className="text-lg font-bold text-gray-900">{customer.name}</span>
              <span className="text-sm text-gray-500 ml-2">({customer.code})</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {customer.customerType === 'WHOLESALE' ? (
              <Tag color="geekblue" className="font-semibold">
                Khách sỉ / Đại lý B2B
              </Tag>
            ) : (
              <Tag color="cyan">Khách lẻ B2C</Tag>
            )}
            {customer.active ? (
              <Tag color="success">Đang hợp tác</Tag>
            ) : (
              <Tag color="default">Ngưng hợp tác</Tag>
            )}
          </div>
        </div>
      }
      destroyOnClose
    >
      {/* KHUNG CẢNH BÁO HẠN MỨC CÔNG NỢ */}
      <div className="my-3 p-4 rounded-lg bg-slate-50 border border-slate-200/80">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <SafetyCertificateOutlined className="text-slate-600 text-base" />
            <span className="text-sm font-semibold text-slate-800">
              Kiểm soát hạn mức tín dụng & Công nợ
            </span>
          </div>

          <div>
            {customer.debtLocked ? (
              <Tag
                color="magenta"
                icon={<LockOutlined />}
                className="font-medium py-0.5 px-2 text-xs"
              >
                ĐANG BỊ KHÓA NỢ
              </Tag>
            ) : isOverLimit ? (
              <Tag
                color="error"
                icon={<CloseCircleOutlined />}
                className="font-medium py-0.5 px-2 text-xs"
              >
                VƯỢT HẠN MỨC ({actualRatio.toFixed(1)}%)
              </Tag>
            ) : isNearLimit ? (
              <Tag
                color="warning"
                icon={<ExclamationCircleOutlined />}
                className="font-medium py-0.5 px-2 text-xs"
              >
                CẬN HẠN MỨC ({actualRatio.toFixed(1)}%)
              </Tag>
            ) : limit === 0 ? (
              <Tag color="default">Thanh toán ngay (Không có hạn mức nợ)</Tag>
            ) : (
              <Tag
                color="success"
                icon={<CheckCircleOutlined />}
                className="font-medium py-0.5 px-2 text-xs"
              >
                Hạn mức an toàn ({actualRatio.toFixed(1)}%)
              </Tag>
            )}
          </div>
        </div>

        <Row gutter={[16, 16]} className="mb-3">
          <Col xs={24} sm={8}>
            <div className="bg-white p-3 rounded-lg border border-slate-200/80">
              <span className="text-xs text-slate-500 block mb-1">Hạn mức nợ tối đa:</span>
              <span className="text-base font-semibold text-slate-800 tabular-nums">
                {formatCurrency(customer.creditLimit)}
              </span>
            </div>
          </Col>
          <Col xs={24} sm={8}>
            <div className="bg-white p-3 rounded-lg border border-slate-200/80">
              <span className="text-xs text-slate-500 block mb-1">Công nợ hiện tại:</span>
              <span
                className={`text-base font-semibold tabular-nums ${
                  isOverLimit || customer.debtLocked ? 'text-red-600' : 'text-slate-900'
                }`}
              >
                {formatCurrency(customer.currentDebt)}
              </span>
            </div>
          </Col>
          <Col xs={24} sm={8}>
            <div className="bg-white p-3 rounded-lg border border-slate-200/80">
              <span className="text-xs text-slate-500 block mb-1">Hạn mức khả dụng còn lại:</span>
              <span
                className={`text-base font-semibold tabular-nums ${
                  (customer.availableCredit ?? 0) <= 0 ? 'text-slate-400' : 'text-emerald-600'
                }`}
              >
                {formatCurrency(customer.availableCredit)}
              </span>
            </div>
          </Col>
        </Row>

        {limit > 0 && (
          <div>
            <div className="flex justify-between items-center text-xs text-gray-500 mb-1">
              <span>Tỷ lệ sử dụng hạn mức tín dụng:</span>
              <span className="font-semibold text-gray-700">{actualRatio.toFixed(1)}%</span>
            </div>
            <Progress
              percent={usagePercent}
              status={progressStatus}
              strokeColor={
                customer.debtLocked || isOverLimit
                  ? '#ef4444'
                  : isNearLimit
                  ? '#f59e0b'
                  : '#10b981'
              }
              showInfo={false}
            />
          </div>
        )}
      </div>

      <Tabs
        defaultActiveKey="info"
        items={[
          {
            key: 'info',
            label: (
              <span className="flex items-center gap-1.5">
                <InfoCircleOutlined />
                Hồ sơ thông tin
              </span>
            ),
            children: (
              <Descriptions
                bordered
                size="small"
                column={{ xs: 1, sm: 2 }}
                className="mt-2"
                labelStyle={{ width: '180px', backgroundColor: '#f8fafc', fontWeight: 500 }}
              >
                <Descriptions.Item label="Mã khách hàng">
                  <span className="font-semibold">{customer.code}</span>
                </Descriptions.Item>
                <Descriptions.Item label="Tên khách hàng">
                  <span className="font-semibold">{customer.name}</span>
                </Descriptions.Item>
                <Descriptions.Item label="Phân loại">
                  {customer.customerType === 'WHOLESALE'
                    ? 'Khách sỉ / Đại lý (B2B)'
                    : 'Khách lẻ / Cá nhân (B2C)'}
                </Descriptions.Item>
                <Descriptions.Item label="Mã số thuế">
                  {customer.taxCode ? (
                    <span>{customer.taxCode}</span>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Người liên hệ / Đại diện">
                  {customer.contactName || <span className="text-gray-400">-</span>}
                </Descriptions.Item>
                <Descriptions.Item label="Số điện thoại">
                  {customer.phone ? (
                    <Space size={4}>
                      <PhoneOutlined className="text-gray-400" />
                      <span>{customer.phone}</span>
                    </Space>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Email">
                  {customer.email ? (
                    <Space size={4}>
                      <MailOutlined className="text-gray-400" />
                      <span>{customer.email}</span>
                    </Space>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Điều khoản thanh toán">
                  <Tag color="blue">{customer.paymentTerms || 'NET_30'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Địa chỉ" span={2}>
                  {customer.address || <span className="text-gray-400">-</span>}
                </Descriptions.Item>
                <Descriptions.Item label="Ghi chú" span={2}>
                  {customer.notes || <span className="text-gray-400">Không có ghi chú</span>}
                </Descriptions.Item>
                <Descriptions.Item label="Ngày tạo hồ sơ">
                  {formatDateTime(customer.createdAt)}
                </Descriptions.Item>
                <Descriptions.Item label="Cập nhật lần cuối">
                  {formatDateTime(customer.updatedAt)}
                </Descriptions.Item>
              </Descriptions>
            ),
          },
          {
            key: 'simulator',
            label: (
              <span className="flex items-center gap-1.5">
                <SafetyCertificateOutlined />
                Kiểm tra hạn mức đơn mới (Simulator)
              </span>
            ),
            children: (
              <div className="py-2">
                <p className="text-xs text-gray-500 mb-3">
                  Công cụ giả lập kiểm tra hạn mức tín dụng: Nhập giá trị dự kiến của đơn bán
                  hàng SO để kiểm tra xem hệ thống sẽ cho phép duyệt, cảnh báo hay tự động chặn theo
                  chính sách công nợ.
                </p>

                <div className="p-4 bg-slate-50 rounded-lg border border-slate-200 mb-4">
                  <Row gutter={16} align="middle">
                    <Col xs={24} sm={14}>
                      <label className="text-xs font-semibold text-gray-700 block mb-1">
                        Giá trị đơn hàng dự kiến (VNĐ):
                      </label>
                      <InputNumber<number>
                        className="w-full text-base font-semibold"
                        size="large"
                        min={0}
                        step={1000000}
                        value={simulatorAmount}
                        onChange={(v) => setSimulatorAmount(v)}
                        formatter={(val) =>
                          val !== undefined ? `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''
                        }
                        parser={(val) => {
                          const cleaned = val?.replace(/\$\s?|(,*)/g, '') ?? '0'
                          return Number(cleaned) || 0
                        }}
                        addonAfter="VNĐ"
                      />
                    </Col>
                    <Col xs={24} sm={10} className="mt-3 sm:mt-0">
                      <Button
                        type="primary"
                        size="large"
                        className="w-full mt-4 sm:mt-0"
                        loading={simulatorLoading}
                        onClick={handleRunSimulator}
                      >
                        Kiểm tra hạn mức ngay
                      </Button>
                    </Col>
                  </Row>
                </div>

                {simulatorResult && (
                  <div className="mt-4">
                    {simulatorResult.status === 'ALLOWED' && (
                      <Alert
                        type="success"
                        showIcon
                        message={
                          <span className="font-bold text-emerald-800">
                            CHO PHÉP DUYỆT ĐƠN HÀNG (ALLOWED)
                          </span>
                        }
                        description={
                          <div className="text-xs space-y-1 mt-1 text-emerald-950">
                            <div>{simulatorResult.message}</div>
                            <div className="font-semibold">
                              Công nợ dự kiến sau đơn: {formatCurrency(simulatorResult.projectedDebt)}{' '}
                              / Hạn mức: {formatCurrency(simulatorResult.creditLimit)}
                            </div>
                          </div>
                        }
                      />
                    )}

                    {simulatorResult.status === 'WARNING_NEAR_LIMIT' && (
                      <Alert
                        type="warning"
                        showIcon
                        message={
                          <span className="font-bold text-amber-800">
                            CẢNH BÁO: ĐẠT HẠN MỨC CẬN TRÊN (WARNING)
                          </span>
                        }
                        description={
                          <div className="text-xs space-y-1 mt-1 text-amber-950">
                            <div>{simulatorResult.message}</div>
                            <div className="font-semibold">
                              Công nợ dự kiến sau đơn: {formatCurrency(simulatorResult.projectedDebt)}{' '}
                              / Hạn mức: {formatCurrency(simulatorResult.creditLimit)}
                            </div>
                          </div>
                        }
                      />
                    )}

                    {simulatorResult.status === 'BLOCKED_OVER_LIMIT' && (
                      <Alert
                        type="error"
                        showIcon
                        message={
                          <span className="font-bold text-red-800">
                            CHẶN ĐƠN HÀNG: VƯỢT HẠN MỨC CÔNG NỢ (BLOCKED)
                          </span>
                        }
                        description={
                          <div className="text-xs space-y-1 mt-1 text-red-950">
                            <div>{simulatorResult.message}</div>
                            <div className="font-semibold">
                              Hạn mức tối đa: {formatCurrency(simulatorResult.creditLimit)} | Nợ dự
                              kiến: {formatCurrency(simulatorResult.projectedDebt)}
                            </div>
                          </div>
                        }
                      />
                    )}

                    {simulatorResult.status === 'BLOCKED_DEBT_LOCKED' && (
                      <Alert
                        type="error"
                        showIcon
                        icon={<LockOutlined />}
                        message={
                          <span className="font-bold text-red-800">
                            CHẶN ĐƠN HÀNG: KHÁCH HÀNG ĐANG BỊ KHÓA NỢ
                          </span>
                        }
                        description={
                          <div className="text-xs mt-1 text-red-950">
                            {simulatorResult.message}
                          </div>
                        }
                      />
                    )}

                    {simulatorResult.status === 'BLOCKED_INACTIVE' && (
                      <Alert
                        type="error"
                        showIcon
                        message="CHẶN ĐƠN HÀNG: KHÁCH HÀNG ĐÃ NGƯNG HOẠT ĐỘNG"
                        description={simulatorResult.message}
                      />
                    )}
                  </div>
                )}
              </div>
            ),
          },
          {
            key: 'transactions',
            label: (
              <span className="flex items-center gap-1.5">
                <HistoryOutlined />
                Lịch sử biến động công nợ ({txTotal})
              </span>
            ),
            children: (
              <div className="pt-2">
                <div className="flex justify-between items-center mb-3">
                  <span className="text-xs text-gray-500">
                    Bao gồm các hóa đơn bán hàng SO, phiếu thu thanh toán, hàng trả lại và điều
                    chỉnh.
                  </span>
                  {canRecordTransaction && (
                    <Button
                      size="small"
                      type="dashed"
                      icon={<DollarCircleOutlined />}
                      onClick={() => {
                        handleClose()
                        onOpenRecordTransaction?.(customer)
                      }}
                    >
                      Ghi nhận GD
                    </Button>
                  )}
                </div>

                <Table<CustomerTransaction>
                  dataSource={transactions}
                  columns={txColumns}
                  rowKey="id"
                  loading={txLoading}
                  size="small"
                  pagination={{
                    current: txPage + 1,
                    pageSize: txPageSize,
                    total: txTotal,
                    showSizeChanger: true,
                    pageSizeOptions: ['10', '20', '50'],
                    onChange: (page, size) => {
                      setTxPage(page - 1)
                      setTxPageSize(size)
                    },
                    showTotal: (total) => `Tổng số ${total} giao dịch`,
                  }}
                  scroll={{ x: 750 }}
                />
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}
