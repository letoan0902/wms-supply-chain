import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Divider,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { formatCurrency, formatNumber } from '../../../utils/format'
import * as purchasesApi from '../purchasesApi'
import { STATUS_CONFIG } from '../statusConfig'
import type { PurchaseOrder, PurchaseOrderItem } from '../types'
import RejectPurchaseOrderModal from './RejectPurchaseOrderModal'

interface Props {
  open: boolean
  order: PurchaseOrder | null
  onClose: () => void
  onOrderUpdated?: (updated: PurchaseOrder) => void
  canApprove?: boolean
  canCancel?: boolean
}

export default function PurchaseOrderDetailModal({
  open,
  order,
  onClose,
  onOrderUpdated,
  canApprove = false,
  canCancel = false,
}: Props) {
  const { message } = App.useApp()
  const [actionLoading, setActionLoading] = useState(false)
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false)

  if (!order) return null

  const statusInfo = STATUS_CONFIG[order.status] ?? {
    label: order.status,
    color: 'default',
    icon: null,
  }

  // Xử lý Phê duyệt đơn mua hàng
  const handleApprove = async () => {
    setActionLoading(true)
    try {
      const updated = await purchasesApi.approvePurchaseOrder(order.id)
      message.success(`Phê duyệt đơn mua hàng "${order.poNumber}" thành công! Đã gửi sự kiện sang kho.`)
      onOrderUpdated?.(updated)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi phê duyệt đơn mua hàng'))
    } finally {
      setActionLoading(false)
    }
  }

  // Xử lý Hủy đơn mua hàng
  const handleCancel = async () => {
    setActionLoading(true)
    try {
      const updated = await purchasesApi.cancelPurchaseOrder(order.id)
      message.success(`Đã hủy đơn mua hàng "${order.poNumber}"!`)
      onOrderUpdated?.(updated)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi hủy đơn mua hàng'))
    } finally {
      setActionLoading(false)
    }
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

  const isPending = order.status === 'PENDING'
  const isCancellable = order.status === 'DRAFT' || order.status === 'PENDING'

  return (
    <>
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
        width={1000}
        destroyOnClose
        footer={
          <div className="flex items-center justify-between">
            <div className="text-xs text-slate-500">
              {isPending && canApprove && (
                <span className="text-amber-600 font-medium">
                  Đơn đang chờ phê duyệt từ cấp Quản lý kho / Mua hàng
                </span>
              )}
            </div>

            <Space>
              <Button onClick={onClose}>Đóng</Button>

              {isCancellable && canCancel && (
                <Popconfirm
                  title="Xác nhận hủy đơn mua hàng?"
                  description={`Bạn có chắc muốn hủy đơn "${order.poNumber}"?`}
                  onConfirm={handleCancel}
                  okText="Đồng ý hủy"
                  cancelText="Bỏ qua"
                  okButtonProps={{ danger: true }}
                >
                  <Button danger icon={<StopOutlined />} loading={actionLoading}>
                    Hủy đơn
                  </Button>
                </Popconfirm>
              )}

              {isPending && canApprove && (
                <>
                  <Button
                    danger
                    icon={<CloseCircleOutlined />}
                    onClick={() => setIsRejectModalOpen(true)}
                    loading={actionLoading}
                  >
                    Từ chối
                  </Button>

                  <Popconfirm
                    title="Xác nhận phê duyệt đơn mua hàng?"
                    description={
                      <div className="max-w-xs text-xs space-y-1">
                        <div>Bạn có chắc muốn phê duyệt đơn mua <strong>{order.poNumber}</strong>?</div>
                        <div className="text-slate-500">
                          Hệ thống sẽ cập nhật trạng thái sang <strong>APPROVED</strong> và phát sự kiện sang RabbitMQ để Warehouse Service tăng chỉ tiêu hàng đang về (Incoming Stock).
                        </div>
                      </div>
                    }
                    icon={<ExclamationCircleOutlined className="text-blue-500" />}
                    onConfirm={handleApprove}
                    okText="Phê duyệt ngay"
                    cancelText="Xem lại"
                    okButtonProps={{ className: 'bg-green-600 hover:bg-green-700' }}
                  >
                    <Button
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      className="bg-green-600 hover:bg-green-700 font-semibold"
                      loading={actionLoading}
                    >
                      Phê duyệt đơn hàng
                    </Button>
                  </Popconfirm>
                </>
              )}
            </Space>
          </div>
        }
      >
        <div className="space-y-4 my-2">
          {/* Cảnh báo lý do từ chối nếu có */}
          {order.status === 'REJECTED' && order.rejectedReason && (
            <Alert
              type="error"
              showIcon
              icon={<CloseCircleOutlined />}
              message="Đơn mua hàng đã bị từ chối phê duyệt"
              description={
                <div className="text-xs">
                  <strong>Lý do từ chối:</strong> {order.rejectedReason}
                </div>
              }
            />
          )}

          {/* Thông báo đã phê duyệt */}
          {order.status === 'APPROVED' && (
            <Alert
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              message="Đơn mua hàng đã được phê duyệt thành công"
              description={
                <div className="text-xs">
                  Người duyệt: <strong>{order.approvedBy || 'Quản lý'}</strong> • Thời gian:{' '}
                  {order.approvedAt ? new Date(order.approvedAt).toLocaleString('vi-VN') : 'Đã duyệt'}
                </div>
              }
            />
          )}

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
              <Descriptions.Item label="Trạng thái">
                <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
              </Descriptions.Item>
              {order.note && (
                <Descriptions.Item label="Ghi chú" span={3}>
                  <span className="text-slate-600">{order.note}</span>
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

      {/* Modal nhập lý do từ chối đơn hàng */}
      <RejectPurchaseOrderModal
        open={isRejectModalOpen}
        order={order}
        onClose={() => setIsRejectModalOpen(false)}
        onSuccess={(updated) => {
          onOrderUpdated?.(updated)
        }}
      />
    </>
  )
}
