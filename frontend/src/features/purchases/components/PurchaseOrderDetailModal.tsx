import { FileTextOutlined } from '@ant-design/icons'
import {
  Card,
  Descriptions,
  Divider,
  Modal,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { formatCurrency, formatNumber } from '../../../utils/format'
import { STATUS_CONFIG } from '../statusConfig'
import type { PurchaseOrder, PurchaseOrderItem } from '../types'

interface Props {
  open: boolean
  order: PurchaseOrder | null
  onClose: () => void
}

export default function PurchaseOrderDetailModal({ open, order, onClose }: Props) {
  if (!order) return null

  const statusInfo = STATUS_CONFIG[order.status] ?? {
    label: order.status,
    color: 'default',
    icon: null,
  }

  const columns: ColumnsType<PurchaseOrderItem> = [
    {
      title: '#',
      key: 'idx',
      width: 45,
      align: 'center',
      render: (_, __, i) => <span className="text-slate-400 text-xs">{i + 1}</span>,
    },
    {
      title: 'Mặt hàng / SKU',
      key: 'sku',
      render: (_, item) => (
        <div>
          <div className="font-semibold text-slate-800 text-xs sm:text-sm">
            {item.productName}
          </div>
          <span className="font-mono text-xs text-blue-700 bg-blue-50 px-1.5 py-0.5 rounded">
            {item.skuCode}
          </span>
          {item.note && <div className="text-[11px] text-slate-400 mt-0.5">Ghi chú: {item.note}</div>}
        </div>
      ),
    },
    {
      title: 'ĐVT',
      dataIndex: 'uom',
      key: 'uom',
      width: 80,
      align: 'center',
      render: (uom: string | null) => <span className="text-xs">{uom || '-'}</span>,
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 90,
      align: 'right',
      render: (qty: number) => <span className="font-semibold">{formatNumber(qty)}</span>,
    },
    {
      title: 'Đơn giá mua',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 120,
      align: 'right',
      render: (p: number) => <span>{formatCurrency(p)}</span>,
    },
    {
      title: 'Chiết khấu',
      key: 'discount',
      width: 110,
      align: 'right',
      render: (_, it) => (
        <span className={it.discountAmount ? 'text-amber-600 font-medium' : 'text-slate-400'}>
          {it.discountAmount ? `-${formatCurrency(it.discountAmount)} (${it.discountPercent ?? 0}%)` : '0 đ'}
        </span>
      ),
    },
    {
      title: 'Thuế VAT',
      key: 'tax',
      width: 110,
      align: 'right',
      render: (_, it) => (
        <span className={it.taxAmount ? 'text-blue-600' : 'text-slate-400'}>
          {it.taxAmount ? `+${formatCurrency(it.taxAmount)} (${it.taxRate ?? 0}%)` : '0 đ'}
        </span>
      ),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'lineTotal',
      key: 'lineTotal',
      width: 140,
      align: 'right',
      render: (total: number) => (
        <span className="font-bold text-slate-800 text-sm">{formatCurrency(total || 0)}</span>
      ),
    },
  ]

  return (
    <Modal
      title={
        <div className="flex items-center justify-between pr-6">
          <div className="flex items-center gap-2">
            <FileTextOutlined className="text-blue-600" />
            <span className="font-bold text-slate-800">
              Chi tiết Đơn mua hàng: {order.poNumber}
            </span>
          </div>
          <Tag color={statusInfo.color} className="flex items-center gap-1 px-2.5 py-0.5 text-xs">
            {statusInfo.icon}
            <span>{statusInfo.label}</span>
          </Tag>
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width={1000}
      destroyOnClose
    >
      <div className="space-y-4 my-2">
        {/* Thông tin chung */}
        <Card size="small" className="bg-slate-50 border-slate-200">
          <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }} bordered>
            <Descriptions.Item label="Mã PO">
              <span className="font-mono font-bold text-blue-700">{order.poNumber}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Nhà cung cấp">
              <span className="font-medium text-slate-800">{order.supplierName || `ID: ${order.supplierId}`}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Kho tiếp nhận">
              <span>{order.warehouseName || (order.warehouseId ? `Kho #${order.warehouseId}` : 'Chưa chỉ định')}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Ngày lập đơn">
              <span>{order.orderDate ? new Date(order.orderDate).toLocaleDateString('vi-VN') : '-'}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Ngày nhận dự kiến">
              <span>{order.expectedDate ? new Date(order.expectedDate).toLocaleDateString('vi-VN') : 'Chưa xác định'}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Người phê duyệt">
              <span>{order.approvedBy || '-'}</span>
            </Descriptions.Item>
            {order.note && (
              <Descriptions.Item label="Ghi chú" span={3}>
                <span className="text-slate-600">{order.note}</span>
              </Descriptions.Item>
            )}
            {order.rejectedReason && (
              <Descriptions.Item label="Lý do từ chối" span={3}>
                <span className="text-red-600 font-medium">{order.rejectedReason}</span>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>

        {/* Bảng danh sách sản phẩm */}
        <div>
          <div className="font-semibold text-slate-800 text-sm mb-2">
            Danh sách mặt hàng đặt mua ({order.items?.length || 0} dòng)
          </div>
          <Table
            rowKey={(it) => String(it.id || it.skuCode)}
            columns={columns}
            dataSource={order.items || []}
            pagination={false}
            size="small"
            bordered
          />
        </div>

        {/* Tóm tắt tổng tiền */}
        <div className="flex justify-end pt-2">
          <div className="w-full sm:w-80 bg-slate-50 p-3 rounded-lg border border-slate-200 space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-slate-500">Tiền hàng (Subtotal):</span>
              <span className="font-medium">{formatCurrency(order.subtotalAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Tổng chiết khấu:</span>
              <span className="text-amber-600 font-medium">-{formatCurrency(order.discountAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Tổng thuế VAT:</span>
              <span className="text-blue-600 font-medium">+{formatCurrency(order.taxAmount)}</span>
            </div>
            <Divider className="my-1.5" />
            <div className="flex justify-between items-center text-sm">
              <span className="font-bold text-slate-800">Tổng thanh toán:</span>
              <span className="font-bold text-red-600 text-base">{formatCurrency(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  )
}
