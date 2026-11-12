import {
  CheckCircleOutlined,
  FileDoneOutlined,
  PrinterOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Col,
  Descriptions,
  Modal,
  Row,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { formatNumber } from '../../../utils/format'
import { GRN_STATUS_CONFIG, type GrnItemResponse, type GrnResponse } from '../grnTypes'

interface GoodsReceiptDetailModalProps {
  open: boolean
  grn: GrnResponse | null
  onClose: () => void
}

export default function GoodsReceiptDetailModal({
  open,
  grn,
  onClose,
}: GoodsReceiptDetailModalProps) {
  if (!grn) return null

  const statusConf = GRN_STATUS_CONFIG[grn.status] || {
    label: grn.status,
    color: 'default',
    badgeStatus: 'default',
  }

  const handlePrint = () => {
    window.print()
  }

  const columns: ColumnsType<GrnItemResponse> = [
    {
      title: 'STT',
      width: 55,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Mặt hàng & SKU',
      render: (_, r) => (
        <div>
          <div className="font-semibold text-slate-800">{r.productName}</div>
          <div className="text-xs text-slate-500 flex items-center gap-2 mt-0.5">
            <span className="font-mono bg-slate-100 px-1 py-0.5 rounded border border-slate-200">
              {r.skuCode}
            </span>
            <span>ĐVT: {r.uom}</span>
          </div>
        </div>
      ),
    },
    {
      title: 'SL Đạt chuẩn',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span className="font-bold text-emerald-700">
          {formatNumber(r.acceptedQuantity)} {r.uom}
        </span>
      ),
    },
    {
      title: 'SL Từ chối / Hỏng',
      width: 170,
      render: (_, r) => {
        if (!r.rejectedQuantity || r.rejectedQuantity <= 0) {
          return <span className="text-slate-400">0</span>
        }
        return (
          <div>
            <span className="font-bold text-amber-700">
              {formatNumber(r.rejectedQuantity)} {r.uom}
            </span>
            {r.rejectedReason && (
              <div className="text-[11px] text-amber-600 mt-0.5 italic">
                Lý do: {r.rejectedReason}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Số Lô & Hạn sử dụng',
      width: 180,
      render: (_, r) => (
        <div className="text-xs space-y-0.5">
          {r.batchNumber ? (
            <div>
              <span className="text-slate-500">Lô:</span>{' '}
              <strong className="font-mono">{r.batchNumber}</strong>
            </div>
          ) : (
            <span className="text-slate-400 italic">Không có số lô</span>
          )}
          {r.mfgDate && (
            <div className="text-[11px] text-slate-500">NSX: {r.mfgDate}</div>
          )}
          {r.expDate && (
            <div className="text-[11px] text-slate-500">HSD: {r.expDate}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Vị trí cất hàng',
      width: 130,
      render: (_, r) =>
        r.locationCode ? (
          <Tag color="cyan" className="font-mono">
            {r.locationCode}
          </Tag>
        ) : (
          <span className="text-slate-400 text-xs">Mặc định kho</span>
        ),
    },
  ]

  return (
    <Modal
      title={
        <div className="flex items-center justify-between pr-6">
          <div className="flex items-center gap-2">
            <FileDoneOutlined className="text-emerald-600 text-xl" />
            <span className="font-bold text-slate-800">
              Chi tiết Phiếu Nhập Kho: {grn.grnNumber}
            </span>
            <Tag color={statusConf.color}>{statusConf.label}</Tag>
          </div>
        </div>
      }
      open={open}
      onCancel={onClose}
      width={900}
      footer={[
        <Button key="print" icon={<PrinterOutlined />} onClick={handlePrint}>
          In Phiếu Nhập Kho
        </Button>,
        <Button key="close" type="primary" onClick={onClose}>
          Đóng
        </Button>,
      ]}
    >
      <div className="space-y-4 py-2 print:p-0">
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2, md: 3 }}
          className="bg-slate-50 rounded"
        >
          <Descriptions.Item label="Mã Phiếu Nhập">
            <strong className="font-mono text-emerald-800">{grn.grnNumber}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Mã Đơn PO">
            <strong className="font-mono text-blue-700">{grn.poNumber}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Ngày nhập kho">
            {grn.receiptDate}
          </Descriptions.Item>
          <Descriptions.Item label="Nhà cung cấp">
            {grn.supplierName || '---'}
          </Descriptions.Item>
          <Descriptions.Item label="Kho tiếp nhận">
            {grn.warehouseName || `Kho #${grn.warehouseId}`}
          </Descriptions.Item>
          <Descriptions.Item label="Người tiếp nhận">
            {grn.receivedBy || 'Hệ thống'}
          </Descriptions.Item>
          <Descriptions.Item label="Ghi chú" span={3}>
            {grn.note || <span className="text-slate-400 italic">Không có ghi chú</span>}
          </Descriptions.Item>
        </Descriptions>

        {/* 3 Thẻ tóm tắt số lượng */}
        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8}>
            <Card size="small" className="border-l-4 border-l-emerald-500 bg-emerald-50/40">
              <div className="text-xs text-slate-500">Tổng SL Đạt chuẩn</div>
              <div className="text-lg font-bold text-emerald-700 mt-1">
                {formatNumber(grn.totalAcceptedQuantity)}
              </div>
            </Card>
          </Col>
          <Col xs={12} sm={8}>
            <Card size="small" className="border-l-4 border-l-amber-500 bg-amber-50/40">
              <div className="text-xs text-slate-500">Tổng SL Hỏng / Phế phẩm</div>
              <div className="text-lg font-bold text-amber-700 mt-1">
                {formatNumber(grn.totalRejectedQuantity)}
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small" className="border-l-4 border-l-blue-500 bg-blue-50/40">
              <div className="text-xs text-slate-500">Số dòng mặt hàng</div>
              <div className="text-lg font-bold text-blue-700 mt-1">
                {grn.items?.length || 0} SKU
              </div>
            </Card>
          </Col>
        </Row>

        {/* Bảng danh sách mặt hàng */}
        <div>
          <div className="font-semibold text-slate-800 mb-2 flex items-center gap-1.5">
            <CheckCircleOutlined className="text-emerald-600" />
            <span>Danh sách mặt hàng thực nhận</span>
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={grn.items || []}
            pagination={false}
            size="small"
            scroll={{ x: 800 }}
          />
        </div>
      </div>
    </Modal>
  )
}
