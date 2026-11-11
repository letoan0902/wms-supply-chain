import { PrinterOutlined } from '@ant-design/icons'
import { Button, Descriptions, Divider, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { formatNumber } from '../../../utils/format'
import type { PickingItem } from '../pickingTypes'
import type { SalesOrder } from '../types'

interface PrintPickingListModalProps {
  open: boolean
  order: SalesOrder | null
  items: PickingItem[]
  onClose: () => void
}

export default function PrintPickingListModal({
  open,
  order,
  items,
  onClose,
}: PrintPickingListModalProps) {
  if (!order) return null

  const handlePrint = () => {
    window.print()
  }

  const columns: ColumnsType<PickingItem> = [
    {
      title: 'Chặng',
      dataIndex: 'step',
      key: 'step',
      width: 65,
      align: 'center',
      render: (step) => (
        <span className="font-bold text-slate-800 bg-slate-100 px-2 py-0.5 rounded border border-slate-300">
          #{step}
        </span>
      ),
    },
    {
      title: 'Vị trí (Zone → Rack → Bin)',
      key: 'location',
      width: 170,
      render: (_, r) => (
        <div>
          <span className="font-mono font-bold text-blue-700 text-sm">
            {r.locationCode}
          </span>
          <div className="text-[11px] text-slate-500">
            Khu {r.zone || '-'} / Dãy {r.rack || '-'} / Ô {r.bin || '-'}
          </div>
        </div>
      ),
    },
    {
      title: 'Mặt hàng & SKU',
      key: 'product',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-900">{r.productName}</div>
          <div className="text-xs text-slate-500 flex items-center gap-2 mt-0.5">
            <span className="font-mono bg-slate-50 px-1 py-0.5 rounded border border-slate-200">
              {r.skuCode}
            </span>
            {r.lotNo && (
              <Tag color="blue" className="text-[10px] m-0 font-mono">
                Lô: {r.lotNo}
              </Tag>
            )}
            {r.expDate && (
              <span className="text-[11px] text-purple-700">
                HSD: {dayjs(r.expDate).format('DD/MM/YYYY')}
              </span>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'SL cần lấy',
      key: 'quantity',
      width: 110,
      align: 'right',
      render: (_, r) => (
        <div className="text-right">
          <span className="font-bold text-base text-slate-800">
            {formatNumber(r.orderedQuantity)}
          </span>{' '}
          <span className="text-xs text-slate-500">{r.uom}</span>
        </div>
      ),
    },
    {
      title: 'Kiểm đếm',
      key: 'check',
      width: 90,
      align: 'center',
      render: (_, r) => (
        <div className="flex flex-col items-center justify-center">
          <div className="w-6 h-6 border-2 border-slate-400 rounded-sm">
            {r.isPicked && <span className="text-emerald-600 font-bold">✓</span>}
          </div>
          <span className="text-[10px] text-slate-400 mt-0.5">
            {r.isPicked ? `${r.pickedQuantity} ${r.uom}` : 'Đã nhặt'}
          </span>
        </div>
      ),
    },
  ]

  const totalQty = items.reduce((sum, it) => sum + (it.orderedQuantity || 0), 0)

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <PrinterOutlined className="text-blue-600 text-lg" />
          <span>In Phiếu Lấy Hàng (Picking List)</span>
        </div>
      }
      width={780}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="print"
          type="primary"
          icon={<PrinterOutlined />}
          onClick={handlePrint}
          className="bg-blue-600 hover:bg-blue-700"
        >
          In Phiếu (Print)
        </Button>,
      ]}
      destroyOnClose
    >
      <div className="p-4 bg-white text-slate-900 space-y-4">
        {/* Header Phiếu Lấy Hàng */}
        <div className="flex justify-between items-start border-b-2 border-slate-800 pb-3">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-slate-900 m-0 uppercase">
              Phiếu Lấy Hàng Xuất Kho
            </h2>
            <div className="text-xs text-slate-500 mt-0.5">
              Smart Picking List (Lộ trình tối ưu Zone → Rack → Bin - FR17)
            </div>
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-500">Mã Đơn Bán Hàng:</div>
            <div className="text-base font-mono font-bold text-blue-700">
              {order.soNumber}
            </div>
            <div className="text-[11px] text-slate-400">
              Ngày lập: {dayjs().format('DD/MM/YYYY HH:mm')}
            </div>
          </div>
        </div>

        {/* Thông tin đơn và kho nhận */}
        <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Khách hàng">
            <strong>{order.customerName || 'N/A'}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Kho xuất hàng">
            {order.warehouseName || `Kho #${order.warehouseId}`}
          </Descriptions.Item>
          <Descriptions.Item label="SĐT nhận hàng">
            {order.recipientPhone || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Ngày đặt hàng">
            {order.orderDate}
          </Descriptions.Item>
          <Descriptions.Item label="Địa chỉ giao hàng" span={2}>
            {order.deliveryAddress || '-'}
          </Descriptions.Item>
          {order.note && (
            <Descriptions.Item label="Ghi chú đơn" span={2}>
              <span className="italic text-slate-600">{order.note}</span>
            </Descriptions.Item>
          )}
        </Descriptions>

        {/* Bảng lộ trình lấy hàng */}
        <div>
          <div className="flex justify-between items-center mb-2">
            <span className="font-semibold text-xs text-slate-700 uppercase tracking-wider">
              Lộ trình lấy hàng tối ưu ({items.length} mặt hàng - {formatNumber(totalQty)} sản phẩm):
            </span>
            <span className="text-[11px] text-slate-500">
              Sắp xếp theo thứ tự lối đi: Khu vực → Dãy kệ → Ô kệ
            </span>
          </div>

          <Table
            rowKey="id"
            columns={columns}
            dataSource={items}
            pagination={false}
            size="small"
            bordered
          />
        </div>

        <Divider className="my-4" />

        {/* Chữ ký xác nhận */}
        <div className="grid grid-cols-3 text-center text-xs pt-2">
          <div>
            <div className="font-semibold text-slate-700">Người Lập Phiếu</div>
            <div className="text-[11px] text-slate-400 italic mb-12">(Ký, họ tên)</div>
          </div>
          <div>
            <div className="font-semibold text-slate-700">Nhân Viên Lấy Hàng</div>
            <div className="text-[11px] text-slate-400 italic mb-12">(Ký, họ tên)</div>
          </div>
          <div>
            <div className="font-semibold text-slate-700">Thủ Kho Xuất Hàng</div>
            <div className="text-[11px] text-slate-400 italic mb-12">(Ký, họ tên)</div>
          </div>
        </div>
      </div>
    </Modal>
  )
}
