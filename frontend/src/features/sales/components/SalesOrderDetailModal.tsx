import {
  CheckCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  RocketOutlined,
  StopOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Modal,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { useCan } from '../../auth/useCan'
import { getApiErrorMessage } from '../../../services/api'
import { errorMessageOf, stockShortagesOf } from '../../../services/stockDocuments'
import { formatCurrency, formatNumber } from '../../../utils/format'
import * as customerApi from '../../customers/customerApi'
import type { CustomerCreditCheckResponse } from '../../customers/types'
import * as salesApi from '../salesApi'
import { SALES_STATUS_CONFIG } from '../statusConfig'
import type { SalesOrder, SalesOrderItem } from '../types'
import { StockShortageNotice } from './StockErrorDetails'

interface Props {
  open: boolean
  order: SalesOrder | null
  onClose: () => void
  onOrderUpdated?: (updated: SalesOrder) => void
  onOpenInvoiceModal?: (order: SalesOrder) => void
  onOpenPicking?: (order: SalesOrder) => void
  onOpenDelivery?: (order: SalesOrder) => void
  canConfirm?: boolean
  canCancel?: boolean
  canManageInvoice?: boolean
}

export default function SalesOrderDetailModal({
  open,
  order,
  onClose,
  onOrderUpdated,
  onOpenInvoiceModal,
  onOpenPicking,
  onOpenDelivery,
  canConfirm = false,
  canCancel = false,
  canManageInvoice = false,
}: Props) {
  const { message, modal } = App.useApp()
  // Nhân viên kho xem được đơn bán nhưng không đọc được công nợ khách hàng (403)
  const canReadCustomers = useCan()('customer.read')
  const [actionLoading, setActionLoading] = useState(false)
  const [creditInfo, setCreditInfo] = useState<CustomerCreditCheckResponse | null>(null)

  // Tra cứu công nợ khách hàng tức thời (FR14, FR18)
  useEffect(() => {
    if (!open || !order?.customerId || !canReadCustomers) return

    let ignore = false
    void customerApi
      .checkCustomerCredit(order.customerId, 0)
      .then((res) => {
        if (!ignore) setCreditInfo(res)
      })
      .catch(() => {
        if (!ignore) setCreditInfo(null)
      })

    return () => {
      ignore = true
    }
  }, [open, order?.customerId, canReadCustomers])

  if (!order) return null

  const statusCfg = SALES_STATUS_CONFIG[order.status] ?? {
    label: order.status,
    color: 'default',
    icon: null,
  }

  const isPendingOrDraft = order.status === 'DRAFT' || order.status === 'BACKORDER'
  const isCancellable = ['DRAFT', 'CONFIRMED', 'BACKORDER'].includes(order.status)
  const canCreateInvoice = ['CONFIRMED', 'COMPLETED'].includes(order.status)

  // Xử lý xác nhận đơn bán (giữ chỗ đồng bộ là một phần của xác nhận; thiếu hàng thì đơn giữ nguyên trạng thái)
  const handleConfirm = async () => {
    if (actionLoading) return
    setActionLoading(true)
    try {
      const updated = await salesApi.confirmSalesOrder(order.id)
      message.success(
        `Đã xác nhận đơn bán "${order.soNumber}"! Hệ thống đã tự động giữ hàng và cập nhật ATP.`
      )
      onOrderUpdated?.(updated)
    } catch (err) {
      const shortages = stockShortagesOf(err)
      if (shortages.length > 0) {
        modal.error({
          title: `Chưa xác nhận được đơn "${order.soNumber}": không đủ hàng để giữ chỗ`,
          width: 640,
          content: (
            <StockShortageNotice
              message={errorMessageOf(err)}
              shortages={shortages}
              productName={(productId) => order.items?.find((it) => it.productId === productId)?.productName}
            />
          ),
        })
      } else {
        message.error(getApiErrorMessage(err, 'Lỗi khi xác nhận đơn bán hàng'))
      }
      // Tải lại đơn và danh sách: đơn có thể đã được xác nhận ở nơi khác hoặc số tồn đã đổi
      try {
        onOrderUpdated?.(await salesApi.getSalesOrderById(order.id))
      } catch {
        // Giữ nguyên dữ liệu đang hiển thị nếu không tải lại được
      }
    } finally {
      setActionLoading(false)
    }
  }

  // Xử lý hủy đơn bán (tự động giải phóng hàng giữ)
  const handleCancel = async () => {
    setActionLoading(true)
    try {
      const updated = await salesApi.cancelSalesOrder(order.id)
      message.success(
        `Đã hủy đơn bán "${order.soNumber}"! Các mặt hàng giữ chỗ đã được giải phóng trở lại tồn khả dụng.`
      )
      onOrderUpdated?.(updated)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi hủy đơn bán hàng'))
    } finally {
      setActionLoading(false)
    }
  }

  // Cột bảng chi tiết hàng hóa
  const columns: ColumnsType<SalesOrderItem> = [
    {
      title: 'STT',
      key: 'idx',
      width: 50,
      align: 'center',
      render: (_val, _rec, index) => index + 1,
    },
    {
      title: 'Mã SKU / Tên sản phẩm',
      key: 'product',
      render: (_val, r) => (
        <div>
          <span className="font-semibold text-blue-600">{r.skuCode}</span>
          <div className="text-xs text-slate-800">{r.productName}</div>
          {r.uom && <span className="text-[11px] text-gray-500">ĐVT: {r.uom}</span>}
        </div>
      ),
    },
    {
      title: 'Số lượng đặt',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 100,
      align: 'right',
      render: (qty: number) => <span className="font-bold text-slate-800">{formatNumber(qty)}</span>,
    },
    {
      title: 'Tồn khả dụng (ATP)',
      key: 'atp',
      width: 150,
      render: (_val, r) => {
        const isShortage = r.isBackorder || (r.atpQuantity !== undefined && r.quantity > r.atpQuantity)
        return (
          <div className="text-xs space-y-0.5">
            <div>
              Tồn: <strong>{formatNumber(r.onHandQuantity ?? 0)}</strong> | Giữ: <strong>{formatNumber(r.reservedQuantity ?? 0)}</strong>
            </div>
            <div className="flex items-center gap-1">
              <span>ATP:</span>
              <strong className={isShortage ? 'text-amber-600' : 'text-green-600'}>
                {formatNumber(r.atpQuantity ?? 0)}
              </strong>
              {isShortage && (
                <Tag color="warning" className="text-[10px] py-0 px-1 m-0">
                  Thiếu {formatNumber(Math.max(0, r.quantity - (r.atpQuantity ?? 0)))}
                </Tag>
              )}
            </div>
          </div>
        )
      },
    },
    {
      title: 'Đơn giá',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 110,
      align: 'right',
      render: (val: number) => formatCurrency(val),
    },
    {
      title: 'Chiết khấu',
      key: 'discount',
      width: 100,
      align: 'right',
      render: (_val, r) => (
        <span className="text-xs text-gray-600">
          {r.discountPercent ? `${r.discountPercent}%` : '0%'}
          {r.discountAmount ? ` (-${formatCurrency(r.discountAmount)})` : ''}
        </span>
      ),
    },
    {
      title: 'Thuế VAT',
      key: 'tax',
      width: 80,
      align: 'center',
      render: (_val, r) => (
        <span className="text-xs text-gray-600">{r.taxRate !== undefined ? `${r.taxRate}%` : '0%'}</span>
      ),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'lineTotal',
      key: 'lineTotal',
      width: 130,
      align: 'right',
      render: (val: number) => <span className="font-bold text-slate-800">{formatCurrency(val)}</span>,
    },
  ]

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center justify-between pr-8">
          <div className="flex items-center gap-2">
            <FileTextOutlined className="text-blue-600 text-lg" />
            <span className="text-base font-semibold text-slate-800">
              Chi tiết Đơn bán hàng: {order.soNumber}
            </span>
          </div>
          <Tag color={statusCfg.color} icon={statusCfg.icon} className="px-2.5 py-0.5 text-xs font-medium">
            {statusCfg.label}
          </Tag>
        </div>
      }
      onCancel={() => {
        setCreditInfo(null)
        onClose()
      }}
      width={1000}
      destroyOnClose
      footer={
        <div className="flex items-center justify-between">
          <div className="text-xs text-slate-500">
            {order.status === 'BACKORDER' && (
              <span className="text-amber-600 font-medium">
                Đơn đang ở trạng thái Thiếu hàng (chờ bổ sung hàng vào kho)
              </span>
            )}
            {order.status === 'CONFIRMED' && (
              <span className="text-green-600 font-medium">
                Đã xác nhận & Giữ hàng thành công
              </span>
            )}
          </div>

          <Space>
            <Button
              onClick={() => {
                setCreditInfo(null)
                onClose()
              }}
            >
              Đóng
            </Button>

            {/* Nút Hủy đơn */}
            {isCancellable && canCancel && (
              <Popconfirm
                title="Xác nhận hủy đơn bán hàng?"
                description={
                  <div className="max-w-xs text-xs space-y-1">
                    <div>Bạn có chắc muốn hủy đơn <strong>{order.soNumber}</strong>?</div>
                    <div className="text-slate-500">
                      Hệ thống sẽ chuyển trạng thái sang Đã hủy và tự động giải phóng lượng hàng đã giữ chỗ.
                    </div>
                  </div>
                }
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

            {/* Nút Lấy hàng: chỉ hiện khi màn hình cha cho phép (vai trò được xem danh sách lấy hàng) */}
            {order.status === 'CONFIRMED' && onOpenPicking && (
              <Button
                data-write-action
                type="primary"
                icon={<RocketOutlined />}
                onClick={() => {
                  setCreditInfo(null)
                  onClose()
                  onOpenPicking?.(order)
                }}
                className="bg-orange-500 hover:bg-orange-600 font-semibold"
              >
                Lấy hàng
              </Button>
            )}

            {/* Nút Xuất kho (GDN): chỉ hiện khi vai trò được lập phiếu xuất */}
            {order.status === 'CONFIRMED' && onOpenDelivery && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => {
                  setCreditInfo(null)
                  onClose()
                  onOpenDelivery?.(order)
                }}
                className="bg-emerald-600 hover:bg-emerald-700 font-semibold"
              >
                Xuất kho (GDN)
              </Button>
            )}

            {/* Nút Tạo Hóa đơn bán hàng */}
            {canCreateInvoice && canManageInvoice && (
              <Button
                type="default"
                icon={<DollarOutlined />}
                onClick={() => onOpenInvoiceModal?.(order)}
                className="hover:border-blue-600 hover:text-blue-600 font-medium"
              >
                Tạo Hóa đơn bán
              </Button>
            )}

            {/* Nút Xác nhận đơn bán */}
            {isPendingOrDraft && canConfirm && (
              <Popconfirm
                title="Xác nhận đơn bán hàng?"
                description={
                  <div className="max-w-xs text-xs space-y-1">
                    <div>Xác nhận duyệt đơn <strong>{order.soNumber}</strong>?</div>
                    <div className="text-slate-500">
                      Hệ thống sẽ chuyển đơn sang <strong>CONFIRMED</strong>, tự động tăng Reserved Stock và trừ Tồn khả dụng (ATP).
                    </div>
                  </div>
                }
                icon={<ExclamationCircleOutlined className="text-blue-500" />}
                onConfirm={handleConfirm}
                okText="Xác nhận ngay"
                cancelText="Xem lại"
                okButtonProps={{ className: 'bg-green-600 hover:bg-green-700' }}
              >
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  className="bg-green-600 hover:bg-green-700 font-semibold"
                  loading={actionLoading}
                >
                  Xác nhận đơn hàng
                </Button>
              </Popconfirm>
            )}
          </Space>
        </div>
      }
    >
      <div className="space-y-4 pt-2">
        {/* Cảnh báo Backorder nếu đơn thiếu hàng */}
        {order.status === 'BACKORDER' && (
          <Alert
            type="warning"
            showIcon
            icon={<WarningOutlined />}
            message="Đơn bán hàng đang ghi nhận BACKORDER do thiếu hàng trong kho"
            description={order.backorderReason || 'Một hoặc nhiều mặt hàng trong đơn vượt quá số lượng tồn khả dụng (ATP). Cần nhập thêm hàng để hoàn tất.'}
          />
        )}

        {/* Thông tin chung của Đơn hàng */}
        <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
          <Descriptions.Item label="Mã SO">
            <span className="font-bold text-blue-600">{order.soNumber}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Khách hàng">
            <strong>{order.customerName || 'Khách vãng lai'}</strong>
            {order.customerCode && <Tag color="blue" className="ml-1 text-[11px]">{order.customerCode}</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="Kho xuất hàng">
            {order.warehouseName || (order.warehouseId ? `Kho #${order.warehouseId}` : '—')}
          </Descriptions.Item>
          <Descriptions.Item label="Ngày đặt hàng">
            {dayjs(order.orderDate).format('DD/MM/YYYY')}
          </Descriptions.Item>
          <Descriptions.Item label="Ngày hẹn giao">
            {order.expectedDate ? dayjs(order.expectedDate).format('DD/MM/YYYY') : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Người nhận / SĐT">
            {order.recipientName || order.recipientPhone
              ? `${order.recipientName || ''} - ${order.recipientPhone || ''}`
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Địa chỉ giao hàng" span={2}>
            {order.deliveryAddress || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Ghi chú đơn">
            {order.note || '—'}
          </Descriptions.Item>
        </Descriptions>

        {/* Thẻ Trạng thái Công nợ Khách hàng (FR14, FR18) */}
        {creditInfo && (
          <Card size="small" className="bg-slate-50 border-slate-200">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                <UserOutlined className="text-blue-600" />
                <span>Trạng thái Công nợ Khách hàng hiện tại:</span>
              </span>
              <Tag
                color={
                  creditInfo.status === 'ALLOWED'
                    ? 'success'
                    : creditInfo.status === 'WARNING_NEAR_LIMIT'
                    ? 'warning'
                    : 'error'
                }
                className="font-medium text-xs"
              >
                {creditInfo.status === 'ALLOWED' && 'Nợ an toàn'}
                {creditInfo.status === 'WARNING_NEAR_LIMIT' && 'Cận hạn mức nợ'}
                {creditInfo.status === 'BLOCKED_OVER_LIMIT' && 'Vượt hạn mức nợ'}
                {creditInfo.status === 'BLOCKED_DEBT_LOCKED' && 'Khóa nợ'}
              </Tag>
            </div>
            <Row gutter={[16, 8]} className="text-xs">
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Hạn mức nợ: </span>
                <span className="font-semibold text-slate-800">{formatCurrency(creditInfo.creditLimit)}</span>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Công nợ hiện tại: </span>
                <span className="font-bold text-amber-600">{formatCurrency(creditInfo.currentDebt)}</span>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Hạn mức còn lại: </span>
                <span className="font-bold text-green-600">{formatCurrency(creditInfo.availableCredit)}</span>
              </Col>
              <Col xs={12} sm={6}>
                <span className="text-gray-500">Đơn hàng này: </span>
                <span className="font-bold text-blue-600">{formatCurrency(order.totalAmount)}</span>
              </Col>
            </Row>
          </Card>
        )}

        {/* Bảng danh sách mặt hàng */}
        <div>
          <div className="text-xs font-semibold text-slate-700 mb-2 flex items-center justify-between">
            <span>Danh sách sản phẩm trong đơn ({order.items?.length || 0} mặt hàng):</span>
            <span className="text-[11px] text-gray-500">
              ATP = OnHand (Tồn thực tế) – Reserved (Đã giữ chỗ)
            </span>
          </div>
          <Table
            rowKey={(r) => r.id || r.productId}
            columns={columns}
            dataSource={order.items || []}
            pagination={false}
            size="small"
            bordered
          />
        </div>

        {/* Tóm tắt tổng tiền đơn hàng */}
        <div className="flex justify-end pt-1">
          <div className="w-80 space-y-1.5 text-xs bg-slate-50 p-3 rounded border border-slate-200">
            <div className="flex justify-between text-gray-600">
              <span>Tổng tiền hàng:</span>
              <span>{formatCurrency(order.subtotalAmount)}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Tổng chiết khấu:</span>
              <span className="text-red-500">-{formatCurrency(order.discountAmount)}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Tổng thuế VAT:</span>
              <span>+{formatCurrency(order.taxAmount)}</span>
            </div>
            <Divider className="my-1.5" />
            <div className="flex justify-between text-sm font-bold text-slate-800">
              <span>Tổng thanh toán:</span>
              <span className="text-blue-600 text-base">{formatCurrency(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  )
}
