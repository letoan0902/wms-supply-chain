import {
  CarOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  InboxOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Modal,
  Steps,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { formatDateTime, formatNumber } from '../../../utils/format'
import { TRANSFER_STATUS_CONFIG } from '../statusConfig'
import type { StockTransfer, StockTransferItem } from '../types'
import { useCan } from '../../auth/useCan'

interface Props {
  transfer: StockTransfer | null
  open: boolean
  onClose: () => void
  onReceive?: (transfer: StockTransfer) => void
}

export default function TransferDetailModal({ transfer, open, onClose, onReceive }: Props) {
  const can = useCan()
  if (!transfer) return null

  const statusConfig = TRANSFER_STATUS_CONFIG[transfer.status] || {
    label: transfer.status,
    color: 'default',
    icon: null,
  }

  // Xác định bước hiện tại trong quy trình
  let currentStep = 0
  let stepStatus: 'process' | 'finish' | 'error' = 'process'

  if (transfer.status === 'CANCELLED') {
    currentStep = 0
    stepStatus = 'error'
  } else if (transfer.status === 'DRAFT') {
    currentStep = 0
    stepStatus = 'process'
  } else if (transfer.status === 'IN_TRANSIT') {
    currentStep = 1
    stepStatus = 'process'
  } else if (transfer.status === 'COMPLETED' || transfer.status === 'PARTIAL_RECEIVED') {
    currentStep = 2
    stepStatus = transfer.status === 'PARTIAL_RECEIVED' ? 'error' : 'finish'
  }

  const totalDamaged = (transfer.items || []).reduce(
    (sum, it) => sum + (it.damagedQuantity || 0),
    0
  )
  const totalMissing =
    transfer.status === 'COMPLETED' || transfer.status === 'PARTIAL_RECEIVED'
      ? Math.max(
          0,
          (transfer.totalTransferredQuantity || 0) -
            (transfer.totalReceivedQuantity || 0) -
            totalDamaged
        )
      : 0

  const columns: ColumnsType<StockTransferItem> = [
    {
      title: 'STT',
      width: 50,
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    {
      title: 'Mã SKU / Tên sản phẩm',
      key: 'product',
      render: (_, record) => (
        <div>
          <span className="font-mono font-semibold text-gray-800">
            {record.skuCode}
          </span>
          {record.productName && (
            <span className="text-xs text-gray-500 block">
              {record.productName}
            </span>
          )}
        </div>
      ),
    },
    {
      title: 'ĐVT',
      dataIndex: 'uom',
      key: 'uom',
      width: 70,
      render: (uom?: string) => uom || '—',
    },
    {
      title: 'Vị trí xuất nguồn',
      dataIndex: 'sourceBinLocationCode',
      key: 'sourceBinLocationCode',
      width: 140,
      render: (code?: string) =>
        code ? <Tag color="blue">{code}</Tag> : <span className="text-gray-400">Tự động FEFO</span>,
    },
    {
      title: 'SL yêu cầu',
      dataIndex: 'quantity',
      key: 'quantity',
      align: 'right',
      width: 100,
      render: (qty: number) => formatNumber(qty),
    },
    {
      title: 'SL thực xuất',
      dataIndex: 'transferredQuantity',
      key: 'transferredQuantity',
      align: 'right',
      width: 105,
      render: (qty: number) => (
        <span className={qty > 0 ? 'text-blue-600 font-semibold' : 'text-gray-400'}>
          {formatNumber(qty)}
        </span>
      ),
    },
    {
      title: 'SL thực nhận',
      dataIndex: 'receivedQuantity',
      key: 'receivedQuantity',
      align: 'right',
      width: 105,
      render: (qty: number) => (
        <span className={qty > 0 ? 'text-green-600 font-semibold' : 'text-gray-400'}>
          {formatNumber(qty)}
        </span>
      ),
    },
    {
      title: 'Thiếu / Hỏng',
      key: 'discrepancy',
      align: 'right',
      width: 120,
      render: (_, record) => {
        const damaged = record.damagedQuantity || 0
        const missing =
          transfer.status === 'COMPLETED' || transfer.status === 'PARTIAL_RECEIVED'
            ? Math.max(0, (record.transferredQuantity || 0) - (record.receivedQuantity || 0) - damaged)
            : 0

        if (damaged === 0 && missing === 0) {
          return <span className="text-gray-400">0</span>
        }
        return (
          <div className="space-y-0.5 text-right">
            {damaged > 0 && (
              <div className="text-red-500 font-semibold text-xs">
                Hỏng: {formatNumber(damaged)}
              </div>
            )}
            {missing > 0 && (
              <div className="text-amber-600 font-semibold text-xs">
                Thiếu: {formatNumber(missing)}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Vị trí nhập đích',
      dataIndex: 'destinationBinLocationCode',
      key: 'destinationBinLocationCode',
      width: 130,
      render: (code?: string) =>
        code ? <Tag color="green">{code}</Tag> : <span className="text-gray-400">—</span>,
    },
    {
      title: 'Ghi chú',
      dataIndex: 'notes',
      key: 'notes',
      render: (notes?: string) => notes || '—',
    },
  ]

  const footer =
    // Nhận hàng: ADMIN hoặc Quản lý của kho đích
    transfer.status === 'IN_TRANSIT' && onReceive && can('transfer.receive', transfer.destinationWarehouseId)
      ? [
          <Button key="close" onClick={onClose}>
            Đóng
          </Button>,
          <Button
            key="receive"
            type="primary"
            icon={<InboxOutlined />}
            onClick={() => onReceive(transfer)}
          >
            Xác nhận nhận hàng tại kho đích
          </Button>,
        ]
      : null

  return (
    <Modal
      title={
        <div className="flex items-center gap-3">
          <span className="text-xl font-bold font-mono text-blue-600">
            {transfer.transferNumber}
          </span>
          <Tag color={statusConfig.color} icon={statusConfig.icon} className="py-0.5 px-2">
            {statusConfig.label}
          </Tag>
        </div>
      }
      open={open}
      width={1100}
      onCancel={onClose}
      footer={footer}
      destroyOnClose
    >
      <div className="py-3">
        {/* Tiến trình điều chuyển */}
        <div className="p-4 bg-gray-50 rounded-xl mb-5 border border-gray-200">
          <Steps
            current={currentStep}
            status={stepStatus}
            items={[
              {
                title: 'Lập lệnh điều chuyển',
                description: (
                  <div className="text-xs text-gray-500 mt-1">
                    <div>{transfer.createdBy || 'Người dùng'}</div>
                    <div>{formatDateTime(transfer.createdAt)}</div>
                  </div>
                ),
                icon: <FileTextOutlined />,
              },
              {
                title: 'Duyệt xuất kho đi đường',
                description: (
                  <div className="text-xs text-gray-500 mt-1">
                    {transfer.approvedBy ? (
                      <>
                        <div>{transfer.approvedBy}</div>
                        <div>{formatDateTime(transfer.approvedAt)}</div>
                      </>
                    ) : (
                      'Chờ thủ kho duyệt xuất'
                    )}
                  </div>
                ),
                icon: <CarOutlined />,
              },
              {
                title:
                  transfer.status === 'PARTIAL_RECEIVED'
                    ? 'Nhận một phần (Có sự cố)'
                    : 'Nhận hàng tại kho đích',
                description: (
                  <div className="text-xs text-gray-500 mt-1">
                    {transfer.receivedBy ? (
                      <>
                        <div>{transfer.receivedBy}</div>
                        <div>{formatDateTime(transfer.receivedAt)}</div>
                      </>
                    ) : (
                      'Chờ kho đích kiểm đếm'
                    )}
                  </div>
                ),
                icon:
                  transfer.status === 'PARTIAL_RECEIVED' ? (
                    <ExclamationCircleOutlined />
                  ) : (
                    <CheckCircleOutlined />
                  ),
              },
            ]}
          />
        </div>

        {/* Cảnh báo biên bản sự cố nếu nhận một phần */}
        {transfer.status === 'PARTIAL_RECEIVED' && (
          <Alert
            type="warning"
            showIcon
            icon={<ExclamationCircleOutlined />}
            className="mb-4"
            message="Lệnh điều chuyển có phát sinh thiếu / hư hỏng khi nhận hàng"
            description={
              <div className="text-sm space-y-1">
                <div>
                  Tổng thực nhận:{' '}
                  <strong className="text-green-700">
                    {formatNumber(transfer.totalReceivedQuantity)}
                  </strong>{' '}
                  / {formatNumber(transfer.totalTransferredQuantity)} • Hư hỏng:{' '}
                  <strong className="text-red-600">{formatNumber(totalDamaged)}</strong> • Thiếu
                  hụt: <strong className="text-amber-700">{formatNumber(totalMissing)}</strong>
                </div>
                {transfer.notes && (
                  <div className="text-gray-700 whitespace-pre-wrap pt-1 border-t border-amber-200/70">
                    {transfer.notes}
                  </div>
                )}
              </div>
            }
          />
        )}

        {/* Thông tin kho bãi và tuyến đường */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <Card size="small" className="border-blue-100 bg-blue-50/30">
            <div className="text-xs font-semibold text-blue-600 uppercase mb-2 flex items-center gap-1">
              <ShopOutlined /> Kho xuất nguồn
            </div>
            <div className="text-base font-bold text-gray-800">
              {transfer.sourceWarehouseName}
            </div>
            <div className="text-xs text-gray-500 font-mono mt-0.5">
              Mã kho: {transfer.sourceWarehouseCode}
            </div>
          </Card>

          <Card size="small" className="border-green-100 bg-green-50/30">
            <div className="text-xs font-semibold text-green-600 uppercase mb-2 flex items-center gap-1">
              <ShopOutlined /> Kho nhập đích
            </div>
            <div className="text-base font-bold text-gray-800">
              {transfer.destinationWarehouseName}
            </div>
            <div className="text-xs text-gray-500 font-mono mt-0.5">
              Mã kho: {transfer.destinationWarehouseCode}
            </div>
          </Card>
        </div>

        {/* Chi tiết thông tin chung */}
        <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }} className="mb-4">
          <Descriptions.Item label="Ngày xuất chuyển">
            {formatDateTime(transfer.transferDate)}
          </Descriptions.Item>
          <Descriptions.Item label="Ngày dự kiến giao">
            {formatDateTime(transfer.expectedDeliveryDate)}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng số mặt hàng">
            <strong>{transfer.totalItems}</strong> mặt hàng
          </Descriptions.Item>
          <Descriptions.Item label="Tổng SL yêu cầu">
            <strong>{formatNumber(transfer.totalQuantity)}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Tổng SL thực xuất">
            <strong className="text-blue-600">
              {formatNumber(transfer.totalTransferredQuantity)}
            </strong>
          </Descriptions.Item>
          <Descriptions.Item label="Tổng SL thực nhận">
            <strong className="text-green-600">
              {formatNumber(transfer.totalReceivedQuantity)}
            </strong>
          </Descriptions.Item>
          {transfer.notes && transfer.status !== 'PARTIAL_RECEIVED' && (
            <Descriptions.Item label="Ghi chú / Biên bản" span={3}>
              <div className="text-gray-700 whitespace-pre-wrap">{transfer.notes}</div>
            </Descriptions.Item>
          )}
        </Descriptions>

        <Divider orientation="left" className="!my-4 text-sm font-semibold text-gray-700">
          Danh sách mặt hàng điều chuyển ({transfer.items?.length || 0} SKU)
        </Divider>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={transfer.items || []}
          pagination={false}
          size="small"
          bordered
          className="rounded-lg overflow-hidden"
        />
      </div>
    </Modal>
  )
}
