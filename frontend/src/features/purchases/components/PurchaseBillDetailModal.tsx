import {
  CalendarOutlined,
  DollarOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Descriptions,
  Modal,
  Progress,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { formatCurrency, formatNumber } from '../../../utils/format'
import { getStoredUser } from '../../../utils/storage'
import {
  BILL_STATUS_CONFIG,
  type PurchaseBill,
  type PurchaseBillItem,
} from '../purchaseBillTypes'

const { Title } = Typography

interface PurchaseBillDetailModalProps {
  open: boolean
  bill: PurchaseBill | null
  onClose: () => void
  onOpenPayment?: (bill: PurchaseBill) => void
}

export default function PurchaseBillDetailModal({
  open,
  bill,
  onClose,
  onOpenPayment,
}: PurchaseBillDetailModalProps) {
  const currentUser = getStoredUser()
  const canRecordPayment =
    currentUser?.roles?.some((r) => ['ADMIN', 'ACCOUNTANT'].includes(r)) ?? false

  if (!bill) return null

  const statusCfg = BILL_STATUS_CONFIG[bill.status] || {
    label: bill.status,
    color: 'default',
  }

  const percentPaid =
    bill.totalAmount > 0
      ? Math.min(100, Math.round((bill.paidAmount / bill.totalAmount) * 100))
      : 0

  const canPay =
    canRecordPayment &&
    bill.status !== 'PAID' &&
    bill.status !== 'CANCELLED' &&
    bill.remainingAmount > 0

  const columns: ColumnsType<PurchaseBillItem> = [
    {
      title: 'STT',
      width: 60,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Mã SKU',
      dataIndex: 'skuCode',
      key: 'skuCode',
      width: 140,
      render: (sku) => (
        <Tag color="cyan" className="font-mono text-xs">
          {sku}
        </Tag>
      ),
    },
    {
      title: 'Tên mặt hàng',
      dataIndex: 'productName',
      key: 'productName',
      render: (name) => <span className="font-medium text-slate-800">{name}</span>,
    },
    {
      title: 'ĐVT',
      dataIndex: 'uom',
      key: 'uom',
      width: 80,
      align: 'center',
    },
    {
      title: 'SL thực nhận',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 120,
      align: 'right',
      render: (qty) => (
        <span className="font-semibold text-emerald-700">{formatNumber(qty)}</span>
      ),
    },
    {
      title: 'Đơn giá (VNĐ)',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 130,
      align: 'right',
      render: (p) => formatCurrency(p),
    },
    {
      title: 'Thành tiền (VNĐ)',
      dataIndex: 'lineTotal',
      key: 'lineTotal',
      width: 150,
      align: 'right',
      render: (tot) => (
        <span className="font-bold text-slate-800">{formatCurrency(tot)}</span>
      ),
    },
  ]

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <FileTextOutlined className="text-blue-600 text-lg" />
          <span>Chi Tiết Hóa Đơn Mua Hàng (Purchase Bill)</span>
        </div>
      }
      width={850}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        canPay && onOpenPayment && (
          <Button
            key="pay"
            type="primary"
            icon={<DollarOutlined />}
            onClick={() => onOpenPayment(bill)}
            className="bg-emerald-600 hover:bg-emerald-700"
          >
            Thanh toán hóa đơn ({formatCurrency(bill.remainingAmount)})
          </Button>
        ),
      ]}
      destroyOnClose
    >
      <div className="space-y-4 pt-2">
        {/* Header thông tin hóa đơn */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-slate-200 pb-3">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xl font-bold font-mono text-slate-800">
                {bill.billNumber}
              </span>
              <Tag color={statusCfg.color} className="font-semibold m-0">
                {statusCfg.label}
              </Tag>
            </div>
            <div className="text-xs text-slate-500 mt-1 flex items-center gap-4">
              <span>
                <CalendarOutlined className="mr-1" />
                Ngày lập: {bill.billDate ? dayjs(bill.billDate).format('DD/MM/YYYY') : '-'}
              </span>
              {bill.dueDate && (
                <span className="text-amber-700 font-medium">
                  Hạn TT: {dayjs(bill.dueDate).format('DD/MM/YYYY')}
                </span>
              )}
            </div>
          </div>

          <div className="text-right">
            <span className="text-xs text-slate-500 block">Tổng thanh toán:</span>
            <span className="text-xl font-bold text-emerald-700">
              {formatCurrency(bill.totalAmount)}
            </span>
          </div>
        </div>

        {/* Thẻ theo dõi tiến độ thanh toán & công nợ */}
        <Card size="small" className="bg-slate-50 border-slate-200">
          <div className="space-y-2">
            <div className="flex justify-between items-center text-xs">
              <span className="font-semibold text-slate-700">Tiến độ thanh toán tiền hàng:</span>
              <span className="font-bold text-slate-800">
                Đã trả: {formatCurrency(bill.paidAmount)} / Còn nợ: {formatCurrency(bill.remainingAmount)} ({percentPaid}%)
              </span>
            </div>
            <Progress
              percent={percentPaid}
              status={bill.status === 'PAID' ? 'success' : 'active'}
              strokeColor={bill.status === 'PAID' ? '#10b981' : '#3b82f6'}
            />
          </div>
        </Card>

        {/* Thông tin chứng từ liên kết */}
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2 }}
          className="text-xs"
        >
          <Descriptions.Item label="Nhà cung cấp">
            <span className="font-semibold text-slate-800 flex items-center gap-1">
              <ShopOutlined className="text-slate-400" />
              {bill.supplierName || 'NCC không xác định'}
            </span>
          </Descriptions.Item>
          <Descriptions.Item label="Phiếu nhập kho (GRN)">
            <span className="font-mono font-semibold text-emerald-700 flex items-center gap-1">
              <FileDoneOutlined className="text-slate-400" />
              {bill.grnNumber || 'N/A'}
            </span>
          </Descriptions.Item>
          <Descriptions.Item label="Đơn mua hàng (PO)">
            <span className="font-mono text-blue-700">{bill.poNumber || 'N/A'}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Ghi chú">
            <span>{bill.note || 'Không có ghi chú'}</span>
          </Descriptions.Item>
        </Descriptions>

        {/* Bảng danh sách mặt hàng */}
        <div>
          <Title level={5} className="!text-sm !mb-2 text-slate-800">
            Danh sách mặt hàng đã nhập kho & tính tiền
          </Title>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={bill.items || []}
            pagination={false}
            size="small"
            bordered
            summary={() => (
              <Table.Summary fixed>
                <Table.Summary.Row className="bg-slate-50 font-bold">
                  <Table.Summary.Cell index={0} colSpan={4} align="right">
                    Tổng cộng:
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={1} align="right" className="text-emerald-700">
                    {formatNumber(
                      (bill.items || []).reduce((acc, it) => acc + (Number(it.quantity) || 0), 0)
                    )}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2} />
                  <Table.Summary.Cell index={3} align="right" className="text-base text-emerald-700">
                    {formatCurrency(bill.totalAmount)}
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              </Table.Summary>
            )}
          />
        </div>
      </div>
    </Modal>
  )
}
