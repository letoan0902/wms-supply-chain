import { ExportOutlined, PrinterOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Modal, Popconfirm, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { formatNumber } from '../../../utils/format'
import type { GdnItemResponse, GdnResponse, GoodsDeliveryStatus } from '../gdnTypes'
import { isAwaitingDispatch } from '../useGdnDispatch'

const GDN_STATUS_TAG: Record<GoodsDeliveryStatus, { color: string; label: string }> = {
  DRAFT: { color: 'warning', label: 'Bản nháp' },
  PICKING: { color: 'processing', label: 'Đang lấy hàng' },
  COMPLETED: { color: 'success', label: 'Đã xuất kho' },
  CANCELLED: { color: 'error', label: 'Đã hủy' },
}

interface GoodsDeliveryDetailModalProps {
  open: boolean
  gdn: GdnResponse | null
  onClose: () => void
  onPrint?: (gdn: GdnResponse) => void
  /** Xác nhận xuất kho phiếu DRAFT/PICKING (trừ tồn); không truyền thì không hiện nút */
  onDispatch?: (gdn: GdnResponse) => Promise<void> | void
  /** Người dùng được xuất kho phiếu này (quyền gdn.create) */
  canDispatch?: boolean
  dispatching?: boolean
}

export default function GoodsDeliveryDetailModal({
  open,
  gdn,
  onClose,
  onPrint,
  onDispatch,
  canDispatch = false,
  dispatching = false,
}: GoodsDeliveryDetailModalProps) {
  if (!gdn) return null

  const columns: ColumnsType<GdnItemResponse> = [
    {
      title: 'STT',
      key: 'idx',
      width: 50,
      align: 'center',
      render: (_, __, i) => i + 1,
    },
    {
      title: 'Mã SKU & Mặt hàng',
      key: 'product',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800">{r.productName}</div>
          <div className="text-xs font-mono text-slate-500">{r.skuCode}</div>
        </div>
      ),
    },
    {
      title: 'Số Lô & Hạn dùng',
      key: 'lotNo',
      width: 150,
      render: (_, r) =>
        r.batchNumber ? (
          <Tag color="blue" className="font-mono text-xs m-0">
            Lô: {r.batchNumber}
          </Tag>
        ) : (
          <span className="text-slate-400 italic text-xs">Không theo lô</span>
        ),
    },
    {
      title: 'Vị trí lấy hàng',
      key: 'bin',
      width: 140,
      render: (_, r) =>
        r.locationCode ? (
          <span className="font-mono font-bold text-blue-700 text-xs">
            {r.locationCode}
          </span>
        ) : (
          <span className="text-slate-400 italic text-xs">-</span>
        ),
    },
    {
      title: 'Số lượng thực xuất',
      dataIndex: 'deliveredQuantity',
      key: 'deliveredQuantity',
      width: 140,
      align: 'right',
      render: (qty, r) => (
        <div className="text-right">
          <span className="font-bold text-slate-800">{formatNumber(qty)}</span>{' '}
          <span className="text-xs text-slate-500">{r.uom || ''}</span>
        </div>
      ),
    },
  ]

  const totalQty = Number(gdn.totalQuantity ?? 0)
  const statusTag = GDN_STATUS_TAG[gdn.status] ?? { color: 'default', label: gdn.status }
  const showDispatch = Boolean(onDispatch) && canDispatch && isAwaitingDispatch(gdn)

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center justify-between pr-8">
          <span className="text-base font-bold text-slate-800">
            Chi Tiết Phiếu Xuất Kho: <span className="font-mono text-emerald-700">{gdn.gdnNumber}</span>
          </span>
          <Tag color={statusTag.color}>{statusTag.label}</Tag>
        </div>
      }
      width={780}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        ...(showDispatch
          ? [
              <Popconfirm
                key="dispatch"
                title="Xác nhận xuất kho?"
                description={`Trừ tồn kho theo phiếu "${gdn.gdnNumber}" và hoàn tất phiếu.`}
                onConfirm={() => onDispatch?.(gdn)}
                okText="Xác nhận xuất kho"
                cancelText="Hủy"
              >
                <Button
                  data-write-action
                  icon={<ExportOutlined />}
                  loading={dispatching}
                  className="text-orange-600 border-orange-400"
                >
                  Xác nhận xuất kho
                </Button>
              </Popconfirm>,
            ]
          : []),
        <Button
          key="print"
          type="primary"
          icon={<PrinterOutlined />}
          onClick={() => {
            if (onPrint) onPrint(gdn)
          }}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          In Phiếu Giao Hàng
        </Button>,
      ]}
      destroyOnClose
    >
      <div className="space-y-4 pt-2">
        {isAwaitingDispatch(gdn) && (
          <Alert
            type="warning"
            showIcon
            message="Phiếu chưa trừ tồn kho"
            description={
              showDispatch
                ? 'Bấm "Xác nhận xuất kho" để trừ tồn và hoàn tất phiếu. Bấm lại sau lỗi là an toàn: tồn chỉ bị trừ một lần cho mỗi phiếu.'
                : 'Quản trị hệ thống, Quản lý kho hoặc Nhân viên kho bấm "Xác nhận xuất kho" để trừ tồn và hoàn tất phiếu.'
            }
          />
        )}

        <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Mã Phiếu GDN">
            <span className="font-mono font-bold text-emerald-700">{gdn.gdnNumber}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Đơn Bán Hàng (SO)">
            <span className="font-mono font-bold text-blue-700">{gdn.soNumber}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Kho xuất hàng">
            {gdn.warehouseName || `Kho #${gdn.warehouseId}`}
          </Descriptions.Item>
          <Descriptions.Item label="Khách hàng">
            {gdn.customerName || 'N/A'}
          </Descriptions.Item>
          <Descriptions.Item label="Địa chỉ giao" span={2}>
            {gdn.deliveryAddress || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Ngày xuất">
            {gdn.dispatchedAt
              ? dayjs(gdn.dispatchedAt).format('DD/MM/YYYY HH:mm')
              : gdn.deliveryDate
                ? dayjs(gdn.deliveryDate).format('DD/MM/YYYY')
                : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng số lượng xuất">
            <strong className="text-emerald-700 font-bold">{formatNumber(totalQty)}</strong> sản phẩm
          </Descriptions.Item>
          {gdn.note && (
            <Descriptions.Item label="Ghi chú" span={2}>
              <span className="italic text-slate-600">{gdn.note}</span>
            </Descriptions.Item>
          )}
        </Descriptions>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={gdn.items || []}
          pagination={false}
          size="small"
          bordered
        />
      </div>
    </Modal>
  )
}
