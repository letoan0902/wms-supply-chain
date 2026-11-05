import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency } from '../../../utils/format'
import * as customerApi from '../../customers/customerApi'
import type {
  Customer,
  CustomerTransaction,
  CustomerTransactionType,
} from '../../customers/types'
import CreateSalesInvoiceModal from './CreateSalesInvoiceModal'

export default function CustomerReceivablesTab() {
  const { message } = App.useApp()

  // State danh sách khách hàng
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loadingCustomers, setLoadingCustomers] = useState(false)
  const [customerSearch, setCustomerSearch] = useState('')

  // State lịch sử biến động công nợ / Recent Invoices (FR18)
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | undefined>(undefined)
  const [transactions, setTransactions] = useState<CustomerTransaction[]>([])
  const [loadingTx, setLoadingTx] = useState(false)
  const [txPage, setTxPage] = useState(0)
  const [txTotal, setTxTotal] = useState(0)

  // Modals state
  const [invoiceModalOpen, setInvoiceModalOpen] = useState(false)
  const [customerForInvoice, setCustomerForInvoice] = useState<Customer | null>(null)

  const [paymentModalOpen, setPaymentModalOpen] = useState(false)
  const [customerForPayment, setCustomerForPayment] = useState<Customer | null>(null)
  const [paymentLoading, setPaymentLoading] = useState(false)
  const [paymentForm] = Form.useForm()

  const [refreshKey, setRefreshKey] = useState(0)

  // Khách hàng hiện đang active
  const activeCustomerId = selectedCustomerId ?? customers[0]?.id

  // 1. Tải danh sách khách hàng & công nợ
  useEffect(() => {
    let ignore = false
    const loadData = async () => {
      setLoadingCustomers(true)
      try {
        const res = await customerApi.fetchCustomers({
          keyword: customerSearch.trim() || undefined,
          size: 100,
        })
        if (!ignore) {
          setCustomers(res.content || [])
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Lỗi khi tải danh sách công nợ khách hàng'))
        }
      } finally {
        if (!ignore) setLoadingCustomers(false)
      }
    }
    void loadData()
    return () => {
      ignore = true
    }
  }, [customerSearch, refreshKey, message])

  // 2. Tải danh sách Hóa đơn gần đây & Biến động công nợ (FR18 - Recent Invoices)
  useEffect(() => {
    if (!activeCustomerId) {
      return
    }

    let ignore = false
    const loadTx = async () => {
      setLoadingTx(true)
      try {
        const res = await customerApi.fetchCustomerTransactions(activeCustomerId, {
          page: txPage,
          size: 20,
        })
        if (!ignore) {
          setTransactions(res.content || [])
          setTxTotal(res.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Lỗi tải lịch sử hóa đơn khách hàng'))
        }
      } finally {
        if (!ignore) setLoadingTx(false)
      }
    }
    void loadTx()
    return () => {
      ignore = true
    }
  }, [activeCustomerId, txPage, refreshKey, message])

  // Tính toán số liệu thống kê
  const totalReceivables = customers.reduce((sum, c) => sum + (c.currentDebt || 0), 0)
  const overLimitCount = customers.filter(
    (c) => c.creditLimit > 0 && c.currentDebt > c.creditLimit
  ).length
  const debtLockedCount = customers.filter((c) => c.debtLocked).length

  // Xử lý ghi nhận thanh toán (Collect Payment - FR18)
  const handlePaymentSubmit = async (values: {
    amount: number
    referenceCode?: string
    note?: string
  }) => {
    if (!customerForPayment) return
    setPaymentLoading(true)
    try {
      await customerApi.recordCustomerTransaction(customerForPayment.id, {
        type: 'PAYMENT',
        amount: values.amount,
        referenceCode:
          values.referenceCode?.trim() ||
          `PT-${dayjs().format('YYYYMMDD')}-${Math.floor(100 + Math.random() * 900)}`,
        note: values.note?.trim() || 'Khách hàng thanh toán tiền hàng',
      })
      message.success(
        `Đã ghi nhận thanh toán ${formatCurrency(values.amount)} cho khách hàng "${customerForPayment.name}"!`
      )
      setPaymentModalOpen(false)
      paymentForm.resetFields()
      setRefreshKey((k) => k + 1)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi ghi nhận thanh toán'))
    } finally {
      setPaymentLoading(false)
    }
  }

  // Cột bảng công nợ khách hàng
  const customerColumns: ColumnsType<Customer> = [
    {
      title: 'Mã KH',
      dataIndex: 'code',
      key: 'code',
      width: 110,
      render: (val: string, r) => (
        <span
          className={`font-semibold cursor-pointer ${
            activeCustomerId === r.id ? 'text-blue-600 underline' : 'text-slate-700 hover:text-blue-500'
          }`}
          onClick={() => setSelectedCustomerId(r.id)}
        >
          {val}
        </span>
      ),
    },
    {
      title: 'Tên Khách hàng / Đại lý',
      dataIndex: 'name',
      key: 'name',
      render: (val: string, r) => (
        <div>
          <div
            className={`font-medium cursor-pointer ${
              activeCustomerId === r.id ? 'text-blue-700' : 'text-slate-800'
            }`}
            onClick={() => setSelectedCustomerId(r.id)}
          >
            {val}
          </div>
          <div className="text-xs text-gray-500">{r.phone || r.email || '—'}</div>
        </div>
      ),
    },
    {
      title: 'Hạn mức nợ',
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      width: 130,
      align: 'right',
      render: (val: number) => formatCurrency(val),
    },
    {
      title: 'Công nợ phải thu',
      dataIndex: 'currentDebt',
      key: 'currentDebt',
      width: 140,
      align: 'right',
      render: (val: number, r) => {
        const isOver = r.creditLimit > 0 && val > r.creditLimit
        return (
          <span className={`font-bold ${isOver ? 'text-red-600' : val > 0 ? 'text-amber-600' : 'text-slate-700'}`}>
            {formatCurrency(val)}
          </span>
        )
      },
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 120,
      align: 'center',
      render: (_val, r) => {
        if (r.debtLocked) {
          return <Tag color="error">Bị khóa nợ</Tag>
        }
        if (r.creditLimit > 0 && r.currentDebt > r.creditLimit) {
          return <Tag color="error">Vượt hạn mức</Tag>
        }
        if (r.creditLimit > 0 && r.currentDebt >= r.creditLimit * 0.8) {
          return <Tag color="warning">Cận hạn mức</Tag>
        }
        return <Tag color="success">An toàn</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 130,
      align: 'center',
      render: (_val, r) => (
        <Space size="small">
          <Tooltip title="Lập hóa đơn bán (Bán nợ)">
            <Button
              size="small"
              type="text"
              icon={<FileTextOutlined className="text-blue-600" />}
              onClick={() => {
                setCustomerForInvoice(r)
                setInvoiceModalOpen(true)
              }}
            />
          </Tooltip>
          <Tooltip title="Thu tiền nợ (Thanh toán)">
            <Button
              size="small"
              type="text"
              icon={<DollarOutlined className="text-green-600" />}
              onClick={() => {
                setCustomerForPayment(r)
                paymentForm.setFieldsValue({
                  amount: r.currentDebt > 0 ? r.currentDebt : undefined,
                  referenceCode: `PT-${dayjs().format('YYYYMMDD')}-${Math.floor(100 + Math.random() * 900)}`,
                  note: `Thu tiền công nợ khách hàng ${r.name}`,
                })
                setPaymentModalOpen(true)
              }}
            />
          </Tooltip>
          <Tooltip title="Xem lịch sử hóa đơn">
            <Button
              size="small"
              type="text"
              icon={<EyeOutlined className="text-slate-600" />}
              onClick={() => setSelectedCustomerId(r.id)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  // Cột bảng Hóa đơn gần đây & Biến động công nợ (FR18 - Recent Invoices)
  const txColumns: ColumnsType<CustomerTransaction> = [
    {
      title: 'Mã chứng từ / Hóa đơn',
      dataIndex: 'referenceCode',
      key: 'referenceCode',
      width: 170,
      render: (val: string, r) => (
        <div>
          <span className="font-semibold text-blue-600">{val || `TX-#${r.id}`}</span>
          <div className="text-[11px] text-gray-500">{dayjs(r.transactionDate).format('DD/MM/YYYY HH:mm')}</div>
        </div>
      ),
    },
    {
      title: 'Loại phát sinh (FR18)',
      dataIndex: 'transactionType',
      key: 'transactionType',
      width: 160,
      render: (type: CustomerTransactionType) => {
        switch (type) {
          case 'SALES_INVOICE':
            return <Tag color="blue" icon={<FileTextOutlined />}>Hóa đơn bán hàng</Tag>
          case 'PAYMENT':
            return <Tag color="success" icon={<CheckCircleOutlined />}>Thu tiền khách</Tag>
          case 'RETURN':
            return <Tag color="warning">Trả lại hàng</Tag>
          case 'ADJUSTMENT':
            return <Tag color="default">Điều chỉnh nợ</Tag>
          default:
            return <Tag>{type}</Tag>
        }
      },
    },
    {
      title: 'Số tiền phát sinh',
      dataIndex: 'amount',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (val: number, r) => {
        const isPositive = r.transactionType === 'SALES_INVOICE'
        return (
          <span className={`font-bold ${isPositive ? 'text-blue-600' : 'text-green-600'}`}>
            {isPositive ? '+' : '-'}
            {formatCurrency(val)}
          </span>
        )
      },
    },
    {
      title: 'Dư nợ sau GD (Balance - FR18)',
      dataIndex: 'balanceAfter',
      key: 'balanceAfter',
      width: 160,
      align: 'right',
      render: (val: number) => <span className="font-bold text-slate-800">{formatCurrency(val)}</span>,
    },
    {
      title: 'Trạng thái (FR18)',
      key: 'paymentStatus',
      width: 150,
      align: 'center',
      render: (_val, r) => {
        if (r.transactionType === 'SALES_INVOICE') {
          return r.balanceAfter === 0 ? (
            <Tag color="success">PAID (Đã thanh toán)</Tag>
          ) : (
            <Tag color="error">UNPAID (Bán nợ)</Tag>
          )
        }
        if (r.transactionType === 'PAYMENT') {
          return <Tag color="cyan">Đã thu tiền</Tag>
        }
        return <Tag color="default">Ghi sổ</Tag>
      },
    },
    {
      title: 'Diễn giải / Ghi chú',
      dataIndex: 'note',
      key: 'note',
      render: (val?: string) => <span className="text-xs text-gray-600">{val || '—'}</span>,
    },
  ]

  const activeCustomer = customers.find((c) => c.id === activeCustomerId)

  return (
    <div className="space-y-4 pt-1">
      {/* Metrics công nợ */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={8}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Tổng công nợ phải thu (Payment Receivables - FR18)"
              value={totalReceivables}
              formatter={(v) => formatCurrency(Number(v))}
              valueStyle={{ color: '#cf1322', fontWeight: 'bold' }}
              prefix={<DollarOutlined className="mr-1 text-red-500" />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={8}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Khách hàng vượt hạn mức nợ"
              value={overLimitCount}
              valueStyle={{ color: overLimitCount > 0 ? '#fa8c16' : '#52c41a' }}
              prefix={<WarningOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6} md={8}>
          <Card size="small" className="shadow-2xs border-slate-200">
            <Statistic
              title="Khách hàng đang bị khóa nợ"
              value={debtLockedCount}
              valueStyle={{ color: debtLockedCount > 0 ? '#ff4d4f' : '#52c41a' }}
              prefix={<ClockCircleOutlined className="mr-1" />}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* Cột trái: Danh sách công nợ khách hàng */}
        <Col xs={24} lg={11}>
          <Card
            title={
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 font-semibold text-slate-800 text-sm">
                  <UserOutlined className="text-blue-600" />
                  <span>Danh sách Công nợ Khách hàng / Đại lý</span>
                </span>
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  onClick={() => setRefreshKey((k) => k + 1)}
                >
                  Tải lại
                </Button>
              </div>
            }
            className="shadow-2xs border-slate-200"
          >
            <div className="space-y-3">
              <Input
                placeholder="Tìm mã KH, tên khách hàng..."
                prefix={<SearchOutlined className="text-gray-400" />}
                value={customerSearch}
                onChange={(e) => setCustomerSearch(e.target.value)}
                allowClear
              />

              <Table
                rowKey="id"
                columns={customerColumns}
                dataSource={customers}
                loading={loadingCustomers}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                size="small"
                rowClassName={(r) =>
                  r.id === activeCustomerId ? 'bg-blue-50 font-medium' : ''
                }
              />
            </div>
          </Card>
        </Col>

        {/* Cột phải: Hóa đơn bán & Biến động công nợ gần đây (Recent Invoices - FR18) */}
        <Col xs={24} lg={13}>
          <Card
            title={
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-semibold text-slate-800 text-sm">
                  <FileDoneOutlined className="text-blue-600" />
                  <span>
                    Hóa đơn bán & Công nợ gần đây (Recent Invoices - FR18)
                    {activeCustomer && (
                      <span className="text-blue-600 font-bold ml-1.5">
                        [{activeCustomer.code} - {activeCustomer.name}]
                      </span>
                    )}
                  </span>
                </div>
                <Space>
                  <Button
                    size="small"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setCustomerForInvoice(activeCustomer || null)
                      setInvoiceModalOpen(true)
                    }}
                    className="bg-blue-600 hover:bg-blue-700"
                  >
                    Lập Hóa đơn
                  </Button>
                  <Button
                    size="small"
                    icon={<DollarOutlined />}
                    onClick={() => {
                      if (!activeCustomer) return
                      setCustomerForPayment(activeCustomer)
                      paymentForm.setFieldsValue({
                        amount: activeCustomer.currentDebt > 0 ? activeCustomer.currentDebt : undefined,
                        referenceCode: `PT-${dayjs().format('YYYYMMDD')}-${Math.floor(100 + Math.random() * 900)}`,
                        note: `Thu tiền công nợ khách hàng ${activeCustomer.name}`,
                      })
                      setPaymentModalOpen(true)
                    }}
                    disabled={!activeCustomer}
                  >
                    Thu tiền
                  </Button>
                </Space>
              </div>
            }
            className="shadow-2xs border-slate-200"
          >
            {activeCustomer ? (
              <div className="space-y-3">
                {/* Thanh tóm tắt khách hàng đang chọn */}
                <div className="flex flex-wrap items-center justify-between text-xs p-2.5 bg-slate-50 rounded border border-slate-200">
                  <div>
                    <span className="text-gray-500">Khách hàng: </span>
                    <strong>{activeCustomer.name}</strong> ({activeCustomer.code})
                  </div>
                  <div>
                    <span className="text-gray-500">Hạn mức: </span>
                    <strong>{formatCurrency(activeCustomer.creditLimit)}</strong>
                  </div>
                  <div>
                    <span className="text-gray-500">Công nợ hiện tại: </span>
                    <strong className="text-amber-600 text-sm">
                      {formatCurrency(activeCustomer.currentDebt)}
                    </strong>
                  </div>
                </div>

                <Table
                  rowKey="id"
                  columns={txColumns}
                  dataSource={transactions}
                  loading={loadingTx}
                  pagination={{
                    current: txPage + 1,
                    pageSize: 10,
                    total: txTotal,
                    onChange: (p) => setTxPage(p - 1),
                  }}
                  size="small"
                  scroll={{ x: 750 }}
                />
              </div>
            ) : (
              <div className="text-center py-12 text-gray-400 text-xs">
                Chọn một khách hàng ở danh sách bên trái để xem lịch sử hóa đơn bán & công nợ phải thu
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Modal Lập Hóa đơn bán hàng (FR18) */}
      <CreateSalesInvoiceModal
        open={invoiceModalOpen}
        customer={customerForInvoice}
        onClose={() => {
          setInvoiceModalOpen(false)
          setCustomerForInvoice(null)
        }}
        onSuccess={() => setRefreshKey((k) => k + 1)}
      />

      {/* Modal Thu tiền công nợ khách hàng (Record Payment - FR18) */}
      <Modal
        open={paymentModalOpen}
        title={
          <div className="flex items-center gap-2 text-slate-800 text-base font-semibold">
            <DollarOutlined className="text-green-600" />
            <span>Thu Tiền Khách Hàng (Giảm Công Nợ Phải Thu - FR18)</span>
          </div>
        }
        onCancel={() => {
          setPaymentModalOpen(false)
          setCustomerForPayment(null)
        }}
        footer={null}
        destroyOnClose
      >
        {customerForPayment && (
          <Form
            form={paymentForm}
            layout="vertical"
            onFinish={handlePaymentSubmit}
            className="pt-2 space-y-3"
          >
            <Alert
              type="info"
              showIcon
              message={
                <div className="text-xs">
                  Khách hàng: <strong>{customerForPayment.name}</strong> | Công nợ hiện tại:{' '}
                  <strong className="text-amber-600">
                    {formatCurrency(customerForPayment.currentDebt)}
                  </strong>
                </div>
              }
            />

            <Form.Item
              name="amount"
              label="Số tiền thu (VNĐ)"
              rules={[
                { required: true, message: 'Vui lòng nhập số tiền thanh toán' },
                { type: 'number', min: 1, message: 'Số tiền phải lớn hơn 0' },
              ]}
            >
              <InputNumber
                className="w-full"
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
                parser={(val) => Number(val?.replace(/\./g, '') || 0)}
                placeholder="0"
                prefix={<DollarOutlined className="text-gray-400" />}
              />
            </Form.Item>

            <Form.Item
              name="referenceCode"
              label="Số Phiếu thu / Mã tham chiếu"
              rules={[{ required: true, message: 'Vui lòng nhập mã phiếu thu' }]}
            >
              <Input placeholder="Ví dụ: PT-20261105-01" />
            </Form.Item>

            <Form.Item name="note" label="Ghi chú / Diễn giải">
              <Input.TextArea rows={2} placeholder="Ví dụ: Khách thanh toán công nợ chuyển khoản" />
            </Form.Item>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
              <Button onClick={() => setPaymentModalOpen(false)}>Hủy bỏ</Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={paymentLoading}
                className="bg-green-600 hover:bg-green-700 font-medium"
              >
                Xác nhận Thu tiền
              </Button>
            </div>
          </Form>
        )}
      </Modal>
    </div>
  )
}
