import {
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Modal,
  Progress,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import * as supplierApi from '../supplierApi'
import type { Supplier, SupplierTransaction, SupplierTransactionType } from '../types'
import { useCan } from '../../auth/useCan'

const { Text } = Typography

interface Props {
  open: boolean
  supplier: Supplier | null
  onClose: () => void
  onOpenRecordModal: (supplier: Supplier) => void
}

const TYPE_CONFIG: Record<
  SupplierTransactionType,
  { label: string; color: string; prefix: string; textColor: string }
> = {
  PURCHASE_INVOICE: {
    label: 'Hóa đơn nhập',
    color: 'orange',
    prefix: '+',
    textColor: 'text-amber-600 font-semibold',
  },
  PAYMENT: {
    label: 'Thanh toán',
    color: 'green',
    prefix: '-',
    textColor: 'text-green-600 font-semibold',
  },
  RETURN: {
    label: 'Trả hàng',
    color: 'purple',
    prefix: '-',
    textColor: 'text-purple-600 font-semibold',
  },
  ADJUSTMENT: {
    label: 'Điều chỉnh',
    color: 'blue',
    prefix: '±',
    textColor: 'text-blue-600 font-semibold',
  },
}

export default function SupplierTransactionsModal({
  open,
  supplier,
  onClose,
  onOpenRecordModal,
}: Props) {
  const { message } = App.useApp()
  const canRecord = useCan()('supplier.transaction')
  const [transactions, setTransactions] = useState<SupplierTransaction[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)

  // Reset trang về 0 khi đổi nhà cung cấp mà không dùng effect gây cascading render
  const [prevSupplierId, setPrevSupplierId] = useState(supplier?.id)
  if (supplier?.id !== prevSupplierId) {
    setPrevSupplierId(supplier?.id)
    setPage(0)
  }

  useEffect(() => {
    let ignore = false
    if (!open || !supplier) {
      return
    }

    const fetchTransactions = async () => {
      setLoading(true)
      try {
        const data = await supplierApi.fetchSupplierTransactions(supplier.id, {
          page,
          size: pageSize,
        })
        if (!ignore) {
          setTransactions(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải lịch sử giao dịch công nợ'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    void fetchTransactions()
    return () => {
      ignore = true
    }
  }, [open, supplier, page, pageSize, reloadKey, message])

  const creditLimit = supplier?.creditLimit ?? 0
  const currentDebt = supplier?.currentDebt ?? 0
  const isOverLimit = supplier?.isOverCreditLimit ?? false
  const debtPercent =
    creditLimit > 0 ? Math.min(Math.round((currentDebt / creditLimit) * 100), 100) : 0

  const columns: ColumnsType<SupplierTransaction> = [
    {
      title: 'Thời gian',
      dataIndex: 'transactionDate',
      key: 'transactionDate',
      width: 160,
      render: (date: string) => (
        <span className="text-xs text-slate-600">
          {new Date(date).toLocaleString('vi-VN')}
        </span>
      ),
    },
    {
      title: 'Loại giao dịch',
      dataIndex: 'transactionType',
      key: 'transactionType',
      width: 130,
      render: (type: SupplierTransactionType) => {
        const conf = TYPE_CONFIG[type] ?? {
          label: type,
          color: 'default',
          prefix: '',
          textColor: '',
        }
        return <Tag color={conf.color}>{conf.label}</Tag>
      },
    },
    {
      title: 'Mã chứng từ',
      dataIndex: 'referenceCode',
      key: 'referenceCode',
      width: 140,
      render: (code: string | null) =>
        code ? (
          <span className="text-xs font-semibold text-slate-700 bg-slate-100 px-1.5 py-0.5 rounded">
            {code}
          </span>
        ) : (
          <span className="text-slate-300">-</span>
        ),
    },
    {
      title: 'Số tiền phát sinh',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      width: 160,
      render: (val: number, record) => {
        const conf = TYPE_CONFIG[record.transactionType]
        return (
          <span className={`text-xs tabular-nums ${conf.textColor}`}>
            {conf.prefix} {formatCurrency(val)}
          </span>
        )
      },
    },
    {
      title: 'Dư nợ sau GD',
      dataIndex: 'balanceAfter',
      key: 'balanceAfter',
      align: 'right',
      width: 150,
      render: (val: number) => (
        <span className="text-xs font-medium text-slate-800 tabular-nums">
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: 'Ghi chú / Diễn giải',
      dataIndex: 'note',
      key: 'note',
      ellipsis: true,
      render: (note: string | null) => (
        <span className="text-xs text-slate-600">{note || '-'}</span>
      ),
    },
  ]

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-lg font-semibold text-slate-800">
          <FileTextOutlined className="text-blue-600" />
          <span>Sổ chi tiết công nợ: {supplier?.code} - {supplier?.name}</span>
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        ...(canRecord
          ? [
              <Button
                key="record"
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => supplier && onOpenRecordModal(supplier)}
              >
                Ghi nhận giao dịch
              </Button>,
            ]
          : []),
      ]}
      width={950}
      destroyOnClose
    >
      {supplier && (
        <div className="space-y-4 my-3">
          {/* Cảnh báo nợ quá hạn mức nếu có */}
          {isOverLimit && (
            <Alert
              type="error"
              showIcon
              icon={<WarningOutlined />}
              message={
                <div className="flex items-center justify-between">
                  <span className="font-semibold">
                    Cảnh báo: Công nợ hiện tại đã vượt quá hạn mức nợ cho phép!
                  </span>
                  <span>
                    Vượt mức:{' '}
                    <strong>{formatCurrency(currentDebt - creditLimit)}</strong>
                  </span>
                </div>
              }
            />
          )}

          {/* Thẻ tóm tắt hạn mức & nợ hiện tại */}
          <Card size="small" className="bg-slate-50 border-slate-200">
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 items-center">
              <div>
                <div className="text-xs text-slate-500">Mã số thuế / MST:</div>
                <div className="font-medium text-slate-800">{supplier.taxCode || 'Chưa cập nhật'}</div>
                <div className="text-xs text-slate-500 mt-1">Điều khoản thanh toán:</div>
                {supplier.paymentTerms ? (
                  <Tag color="cyan" className="mt-0.5">{supplier.paymentTerms}</Tag>
                ) : (
                  <div className="text-slate-400 text-xs italic mt-0.5">Chưa thiết lập</div>
                )}
              </div>

              <div>
                <div className="text-xs text-slate-500">Hạn mức nợ:</div>
                <div className="text-base font-semibold text-slate-800">
                  {formatCurrency(creditLimit)}
                </div>
                <div className="text-xs text-slate-400 mt-1">
                  {creditLimit > 0 ? 'Có thỏa thuận hạn mức' : 'Thanh toán theo từng đơn'}
                </div>
              </div>

              <div>
                <div className="text-xs text-slate-500">Công nợ hiện tại:</div>
                <div
                  className={`text-base font-bold ${
                    isOverLimit ? 'text-red-600' : 'text-amber-600'
                  }`}
                >
                  {formatCurrency(currentDebt)}
                </div>
                <div className="text-xs text-slate-500 mt-1">
                  Còn được nợ:{' '}
                  <strong>
                    {formatCurrency(Math.max(0, creditLimit - currentDebt))}
                  </strong>
                </div>
              </div>

              <div>
                <div className="text-xs text-slate-500 mb-1">
                  Tỷ lệ sử dụng hạn mức ({debtPercent}%):
                </div>
                <Progress
                  percent={debtPercent}
                  status={isOverLimit ? 'exception' : debtPercent >= 90 ? 'normal' : 'success'}
                  strokeColor={isOverLimit ? '#ff4d4f' : debtPercent >= 90 ? '#fa8c16' : '#52c41a'}
                  size="small"
                />
              </div>
            </div>
          </Card>

          {/* Thanh công cụ bảng */}
          <div className="flex items-center justify-between pt-2">
            <Text strong className="text-slate-700">
              Lịch sử phát sinh giao dịch ({formatNumber(total)} bản ghi)
            </Text>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => setReloadKey((k) => k + 1)}
              loading={loading}
            >
              Làm mới
            </Button>
          </div>

          {/* Bảng lịch sử giao dịch */}
          <Table
            rowKey="id"
            columns={columns}
            dataSource={transactions}
            loading={loading}
            size="small"
            pagination={{
              current: page + 1,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50'],
              onChange: (p, ps) => {
                setPage(p - 1)
                setPageSize(ps)
              },
            }}
            locale={{
              emptyText: 'Chưa có giao dịch công nợ nào phát sinh với nhà cung cấp này',
            }}
          />
        </div>
      )}
    </Modal>
  )
}
