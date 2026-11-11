import { PrinterOutlined } from '@ant-design/icons'
import { Button, Descriptions, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { formatNumber } from '../../../utils/format'
import type { GdnItemResponse, GdnResponse } from '../gdnTypes'

interface GoodsDeliveryDetailModalProps {
  open: boolean
  gdn: GdnResponse | null
  onClose: () => void
  onPrint?: (gdn: GdnResponse) => void
}

export default function GoodsDeliveryDetailModal({
  open,
  gdn,
  onClose,
  onPrint,
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
          <div className="text-xs font-mono text-slate-500">{r.productSku}</div>
        </div>
      ),
    },
    {
      title: 'Số Lô & Hạn dùng',
      key: 'lotNo',
      width: 150,
      render: (_, r) =>
        r.lotNo ? (
          <Tag color="blue" className="font-mono text-xs m-0">
            Lô: {r.lotNo}
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
        r.binCode ? (
          <span className="font-mono font-bold text-blue-700 text-xs">
            {r.binCode}
          </span>
        ) : (
          <span className="text-slate-400 italic text-xs">-</span>
        ),
    },
    {
      title: 'Số lượng thực xuất',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 140,
      align: 'right',
      render: (qty, r) => (
        <div className="text-right">
          <span className="font-bold text-slate-800">{formatNumber(qty)}</span>{' '}
          <span className="text-xs text-slate-500">{r.uom || 'Cái'}</span>
        </div>
      ),
    },
  ]

  const totalQty = (gdn.items || []).reduce((s, it) => s + (it.quantity || 0), 0)

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center justify-between pr-8">
          <span className="text-base font-bold text-slate-800">
            Chi Tiết Phiếu Xuất Kho: <span className="font-mono text-emerald-700">{gdn.code}</span>
          </span>
          <Tag color="success">Đã xuất kho</Tag>
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
        <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Mã Phiếu GDN">
            <span className="font-mono font-bold text-emerald-700">{gdn.code}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Đơn Bán Hàng (SO)">
            <span className="font-mono font-bold text-blue-700">{gdn.salesOrderCode}</span>
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
            {dayjs(gdn.createdAt).format('DD/MM/YYYY HH:mm')}
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
