import {
  CheckCircleOutlined,
  DollarOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
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
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { useCan } from '../../auth/useCan'
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
  // Lập hóa đơn bán và thu tiền đều ghi giao dịch công nợ khách hàng: ADMIN, Kế toán
  const canRecord = useCan()('customer.transaction')

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
          `PT-${dayjs().format('YYYYMMDD-HHmmss')}`,
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
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã KH</span>,
      dataIndex: 'code',
      key: 'code',
      width: 90,
      render: (val: string, r) => (
        <span
          className={`font-mono text-sm font-semibold cursor-pointer ${activeCustomerId === r.id ? 'text-blue-600 underline' : 'text-slate-700 hover:text-blue-500'
            }`}
          onClick={() => setSelectedCustomerId(r.id)}
        >
          {val}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Tên Khách hàng / Đại lý</span>,
      dataIndex: 'name',
      key: 'name',
      width: 170,
      render: (val: string, r) => (
        <div className="py-0.5">
          <div
            className={`font-semibold cursor-pointer text-sm leading-snug ${activeCustomerId === r.id ? 'text-blue-700' : 'text-slate-900'
              }`}
            onClick={() => setSelectedCustomerId(r.id)}
          >
            {val}
          </div>
          <div className="text-xs text-slate-500 font-mono mt-0.5">{r.phone || r.email || '—'}</div>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Hạn mức nợ</span>,
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      width: 115,
      align: 'right',
      render: (val: number) => (
        <span className="font-mono text-sm text-slate-700 font-medium tabular-nums">
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Công nợ phải thu</span>,
      dataIndex: 'currentDebt',
      key: 'currentDebt',
      width: 125,
      align: 'right',
      render: (val: number, r) => {
        const isOver = r.creditLimit > 0 && val > r.creditLimit
        return (
          <span className={`font-mono font-bold text-sm tabular-nums ${isOver ? 'text-rose-600' : 'text-slate-900'}`}>
            {formatCurrency(val)}
          </span>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Trạng thái</span>,
      key: 'status',
      width: 110,
      align: 'center',
      render: (_val, r) => {
        if (r.debtLocked) {
          return <Tag color="error" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Bị khóa nợ</Tag>
        }
        if (r.creditLimit > 0 && r.currentDebt > r.creditLimit) {
          return <Tag color="error" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Vượt hạn mức</Tag>
        }
        if (r.creditLimit > 0 && r.currentDebt >= r.creditLimit * 0.8) {
          return <Tag color="warning" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Cận hạn mức</Tag>
        }
        return <Tag color="success" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">An toàn</Tag>
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Thao tác</span>,
      key: 'action',
      width: 100,
      align: 'center',
      render: (_val, r) => (
        <Space size="small">
          {canRecord && (
            <Tooltip title="Lập hóa đơn bán (Bán nợ)">
              <Button
                data-write-action
                size="small"
                type="text"
                icon={<FileTextOutlined className="text-blue-600" />}
                onClick={() => {
                  setCustomerForInvoice(r)
                  setInvoiceModalOpen(true)
                }}
              />
            </Tooltip>
          )}
          {canRecord && (
            <Tooltip title="Thu tiền nợ (Thanh toán)">
              <Button
                size="small"
                type="text"
                icon={<DollarOutlined className="text-green-600" />}
                onClick={() => {
                  setCustomerForPayment(r)
                  paymentForm.setFieldsValue({
                    amount: r.currentDebt > 0 ? r.currentDebt : undefined,
                    referenceCode: `PT-${dayjs().format('YYYYMMDD-HHmmss')}`,
                    note: `Thu tiền công nợ khách hàng ${r.name}`,
                  })
                  setPaymentModalOpen(true)
                }}
              />
            </Tooltip>
          )}
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
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Mã chứng từ / Hóa đơn</span>,
      dataIndex: 'referenceCode',
      key: 'referenceCode',
      width: 170,
      render: (val: string, r) => (
        <div className="py-0.5">
          <span className="font-mono text-sm font-semibold text-blue-600">{val || `TX-#${r.id}`}</span>
          <div className="text-xs text-slate-500 font-mono mt-0.5">{dayjs(r.transactionDate).format('DD/MM/YYYY HH:mm')}</div>
        </div>
      ),
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Loại phát sinh</span>,
      dataIndex: 'transactionType',
      key: 'transactionType',
      width: 160,
      render: (type: CustomerTransactionType) => {
        switch (type) {
          case 'SALES_INVOICE':
            return <Tag color="blue" icon={<FileTextOutlined />} className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Hóa đơn bán hàng</Tag>
          case 'PAYMENT':
            return <Tag color="success" icon={<CheckCircleOutlined />} className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Thu tiền khách</Tag>
          case 'RETURN':
            return <Tag color="warning" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Trả lại hàng</Tag>
          case 'ADJUSTMENT':
            return <Tag color="default" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">Điều chỉnh nợ</Tag>
          default:
            return <Tag className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">{type}</Tag>
        }
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Số tiền phát sinh</span>,
      dataIndex: 'amount',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (val: number, r) => {
        const isPositive = r.transactionType === 'SALES_INVOICE'
        return (
          <span className={`font-mono font-bold text-sm tabular-nums ${isPositive ? 'text-blue-600' : 'text-slate-900'}`}>
            {isPositive ? '+' : '-'}
            {formatCurrency(val)}
          </span>
        )
      },
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Dư nợ sau giao dịch</span>,
      dataIndex: 'balanceAfter',
      key: 'balanceAfter',
      width: 160,
      align: 'right',
      render: (val: number) => <span className="font-mono font-bold text-sm text-slate-900 tabular-nums">{formatCurrency(val)}</span>,
    },
    {
      title: <span className="whitespace-nowrap font-semibold text-slate-700 text-sm">Trạng thái</span>,
      key: 'paymentStatus',
      width: 150,
      align: 'center',
      render: (_val, r) => {
        if (r.transactionType === 'SALES_INVOICE') {
          return r.balanceAfter === 0 ? (
            <Tag color="success" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">PAID (Đã thanh toán)</Tag>
          ) : (
            <Tag color="error" className="text-xs px-2.5 py-0.5 rounded-full border m-0 font-medium">UNPAID (Bán nợ)</Tag>
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
      {/* Metrics công nợ chuẩn Enterprise */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={8}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Tổng công nợ phải thu
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {formatCurrency(totalReceivables)}
              </span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Tổng nợ của toàn bộ khách hàng
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6} md={8}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
              <span>Khách vượt hạn mức nợ</span>
              {overLimitCount > 0 && (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200/80">
                  Cần xử lý
                </span>
              )}
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {overLimitCount}
              </span>
              <span className="text-sm text-slate-500 font-normal">khách</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Vượt quá số tiền được phép nợ
            </div>
          </Card>
        </Col>

        <Col xs={12} sm={6} md={8}>
          <Card variant="borderless" className="shadow-xs border border-slate-200/80 bg-white">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
              <span>Khách đang bị khóa nợ</span>
              {debtLockedCount > 0 && (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200/80">
                  Đang khóa
                </span>
              )}
            </div>
            <div className="mt-2 flex items-baseline gap-1.5">
              <span className="text-3xl font-bold text-slate-900 tracking-tight tabular-nums">
                {debtLockedCount}
              </span>
              <span className="text-sm text-slate-500 font-normal">khách</span>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 text-xs text-slate-600">
              Tạm ngừng cho phép mua nợ
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* Cột trái: Danh sách công nợ khách hàng */}
        <Col xs={24} lg={13}>
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
                size="middle"
                scroll={{ x: 710 }}
                rowClassName={(r) =>
                  r.id === activeCustomerId ? 'bg-blue-50 font-medium' : ''
                }
              />
            </div>
          </Card>
        </Col>

        {/* Cột phải: Hóa đơn bán & Biến động công nợ gần đây */}
        <Col xs={24} lg={11}>
          <Card
            title={
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-semibold text-slate-800 text-sm">
                  <FileDoneOutlined className="text-blue-600" />
                  <span>
                    Hóa đơn bán & Công nợ gần đây
                    {activeCustomer && (
                      <span className="text-blue-600 font-bold ml-1.5">
                        [{activeCustomer.code} - {activeCustomer.name}]
                      </span>
                    )}
                  </span>
                </div>
                {canRecord && (
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
                          referenceCode: `PT-${dayjs().format('YYYYMMDD-HHmmss')}`,
                          note: `Thu tiền công nợ khách hàng ${activeCustomer.name}`,
                        })
                        setPaymentModalOpen(true)
                      }}
                      disabled={!activeCustomer}
                    >
                      Thu tiền
                    </Button>
                  </Space>
                )}
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
            <span>Thu Tiền Khách Hàng (Giảm Công Nợ Phải Thu)</span>
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
                () => ({
                  validator(_, value) {
                    const currentDebt = customerForPayment?.currentDebt ?? 0
                    if (value && currentDebt > 0 && value > currentDebt) {
                      return Promise.reject(
                        new Error(
                          `Số tiền thu (${formatCurrency(value)}) không được lớn hơn công nợ hiện tại (${formatCurrency(currentDebt)})!`
                        )
                      )
                    }
                    return Promise.resolve()
                  },
                }),
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
