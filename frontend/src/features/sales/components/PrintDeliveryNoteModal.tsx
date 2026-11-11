import { PrinterOutlined } from '@ant-design/icons'
import { Button, Descriptions, Divider, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { formatNumber } from '../../../utils/format'
import type { GdnItemResponse, GdnResponse } from '../gdnTypes'

interface PrintDeliveryNoteModalProps {
  open: boolean
  gdn: GdnResponse | null
  onClose: () => void
}

export default function PrintDeliveryNoteModal({
  open,
  gdn,
  onClose,
}: PrintDeliveryNoteModalProps) {
  if (!gdn) return null

  const handlePrint = () => {
    window.print()
  }

  const columns: ColumnsType<GdnItemResponse> = [
    {
      title: 'STT',
      key: 'index',
      width: 55,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Mã SKU & Tên sản phẩm',
      key: 'product',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-900">{r.productName}</div>
          <div className="text-xs text-slate-500 font-mono mt-0.5">{r.productSku}</div>
        </div>
      ),
    },
    {
      title: 'ĐVT',
      dataIndex: 'uom',
      key: 'uom',
      width: 75,
      align: 'center',
      render: (uom) => uom || 'Cái',
    },
    {
      title: 'Số Lô & Vị Trí Xuất',
      key: 'lotAndBin',
      width: 180,
      render: (_, r) => (
        <div className="text-xs space-y-0.5">
          {r.lotNo ? (
            <div>
              <Tag color="blue" className="text-[11px] m-0 font-mono">
                Lô: {r.lotNo}
              </Tag>
            </div>
          ) : (
            <span className="text-slate-400 italic">Không theo lô</span>
          )}
          {r.binCode && (
            <div className="text-slate-600 font-mono text-[11px]">
              Vị trí: <strong>{r.binCode}</strong>
            </div>
          )}
        </div>
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
          <span className="font-bold text-base text-slate-900">
            {formatNumber(qty)}
          </span>{' '}
          <span className="text-xs text-slate-500">{r.uom || 'Cái'}</span>
        </div>
      ),
    },
  ]

  const totalQty = (gdn.items || []).reduce((sum, it) => sum + (it.quantity || 0), 0)

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <PrinterOutlined className="text-emerald-600 text-lg" />
          <span>In Phiếu Xuất Kho Kiêm Giao Hàng (Delivery Note)</span>
        </div>
      }
      width={820}
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
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          In Phiếu
        </Button>,
      ]}
      destroyOnClose
    >
      {/* In ấn cách ly với trang nền */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #printable-delivery-note, #printable-delivery-note * {
            visibility: visible;
          }
          #printable-delivery-note {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
          }
        }
      `}</style>
      <div id="printable-delivery-note" className="p-4 bg-white text-slate-900 space-y-4">
        {/* Header Phiếu Giao Hàng */}
        <div className="flex justify-between items-start border-b-2 border-slate-900 pb-3">
          <div>
            <div className="text-xs font-bold text-slate-500 uppercase tracking-widest">
              HỆ THỐNG QUẢN LÝ CHUỖI CUNG ỨNG & KHO HÀNG (WMS)
            </div>
            <h2 className="text-xl font-bold tracking-tight text-slate-900 m-0 uppercase mt-1">
              Phiếu Xuất Kho Kiêm Giao Hàng
            </h2>
            <div className="text-xs text-slate-500 italic mt-0.5">
              Goods Delivery Note (GDN)
            </div>
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-500">Số Phiếu GDN:</div>
            <div className="text-lg font-mono font-bold text-emerald-700">
              {gdn.code}
            </div>
            <div className="text-xs text-slate-600 font-medium">
              Đơn SO: <span className="font-mono text-blue-700">{gdn.salesOrderCode}</span>
            </div>
            <div className="text-[11px] text-slate-400">
              Ngày xuất: {dayjs(gdn.createdAt).format('DD/MM/YYYY HH:mm')}
            </div>
          </div>
        </div>

        {/* Thông tin bên giao & bên nhận */}
        <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Đơn vị giao (Kho xuất)">
            <strong>{gdn.warehouseName || `Kho #${gdn.warehouseId}`}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Khách hàng / Đơn vị nhận">
            <strong className="text-slate-900">{gdn.customerName || 'N/A'}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Địa chỉ nhận hàng" span={2}>
            {gdn.deliveryAddress || 'Giao tại kho khách hàng'}
          </Descriptions.Item>
          {gdn.note && (
            <Descriptions.Item label="Ghi chú xuất hàng" span={2}>
              <span className="italic text-slate-600">{gdn.note}</span>
            </Descriptions.Item>
          )}
        </Descriptions>

        {/* Danh sách mặt hàng xuất kho */}
        <div>
          <div className="flex justify-between items-center mb-2">
            <span className="font-semibold text-xs text-slate-700 uppercase tracking-wider">
              Chi tiết hàng hóa xuất kho ({gdn.items?.length || 0} mặt hàng):
            </span>
            <span className="text-xs font-bold text-slate-800">
              Tổng số lượng xuất: {formatNumber(totalQty)}
            </span>
          </div>

          <Table
            rowKey="id"
            columns={columns}
            dataSource={gdn.items || []}
            pagination={false}
            size="small"
            bordered
          />
        </div>

        <Divider className="my-3" />

        {/* 4 Chữ ký xác nhận */}
        <div className="grid grid-cols-4 text-center text-xs pt-2">
          <div>
            <div className="font-semibold text-slate-800">Người Lập Phiếu</div>
            <div className="text-[11px] text-slate-400 italic mb-14">(Ký, họ tên)</div>
          </div>
          <div>
            <div className="font-semibold text-slate-800">Thủ Kho Xuất Hàng</div>
            <div className="text-[11px] text-slate-400 italic mb-14">(Ký, họ tên)</div>
          </div>
          <div>
            <div className="font-semibold text-slate-800">Người Vận Chuyển</div>
            <div className="text-[11px] text-slate-400 italic mb-14">(Ký, họ tên)</div>
          </div>
          <div>
            <div className="font-semibold text-slate-800">Khách Hàng / Người Nhận</div>
            <div className="text-[11px] text-slate-400 italic mb-14">(Ký, họ tên)</div>
          </div>
        </div>
      </div>
    </Modal>
  )
}
