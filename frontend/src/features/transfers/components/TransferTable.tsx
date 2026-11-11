import {
  ArrowRightOutlined,
  EditOutlined,
  EyeOutlined,
  InboxOutlined,
  RocketOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { Button, Popconfirm, Space, Table, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { formatDateTime, formatNumber } from '../../../utils/format'
import { TRANSFER_STATUS_CONFIG } from '../statusConfig'
import type { StockTransfer } from '../types'
import { useCan } from '../../auth/useCan'

interface Props {
  transfers: StockTransfer[]
  loading: boolean
  pagination: {
    current: number
    pageSize: number
    total: number
  }
  onChangePage: (page: number, pageSize: number) => void
  onViewDetail: (transfer: StockTransfer) => void
  onEdit: (transfer: StockTransfer) => void
  onDispatch: (transfer: StockTransfer) => void
  onReceive: (transfer: StockTransfer) => void
  onCancel: (transfer: StockTransfer) => void
}

export default function TransferTable({
  transfers,
  loading,
  pagination,
  onChangePage,
  onViewDetail,
  onEdit,
  onDispatch,
  onReceive,
  onCancel,
}: Props) {
  const can = useCan()
  const columns: ColumnsType<StockTransfer> = [
    {
      title: 'Mã lệnh',
      dataIndex: 'transferNumber',
      key: 'transferNumber',
      width: 170,
      render: (text: string, record: StockTransfer) => (
        <button
          type="button"
          onClick={() => onViewDetail(record)}
          className="font-mono font-semibold text-blue-600 hover:text-blue-800 hover:underline cursor-pointer text-left"
        >
          {text}
        </button>
      ),
    },
    {
      title: 'Tuyến điều chuyển',
      key: 'route',
      render: (_, record: StockTransfer) => (
        <div className="flex items-center gap-2">
          <div className="font-medium text-gray-800">
            {record.sourceWarehouseName}
            <span className="text-xs text-gray-400 block font-normal">
              {record.sourceWarehouseCode}
            </span>
          </div>
          <ArrowRightOutlined className="text-gray-400 text-xs shrink-0" />
          <div className="font-medium text-gray-800">
            {record.destinationWarehouseName}
            <span className="text-xs text-gray-400 block font-normal">
              {record.destinationWarehouseCode}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: 'Số mặt hàng',
      dataIndex: 'totalItems',
      key: 'totalItems',
      align: 'right',
      width: 110,
      render: (val: number) => <span>{val} SKU</span>,
    },
    {
      title: 'Tổng số lượng',
      key: 'totalQuantity',
      align: 'right',
      width: 140,
      render: (_, record: StockTransfer) => (
        <div>
          <span className="font-semibold text-gray-800">
            {formatNumber(record.totalQuantity)}
          </span>
          {record.status === 'IN_TRANSIT' && (
            <span className="text-xs text-blue-500 block">
              Đã xuất: {formatNumber(record.totalTransferredQuantity)}
            </span>
          )}
          {(record.status === 'COMPLETED' || record.status === 'PARTIAL_RECEIVED') && (
            <span
              className={`text-xs block ${
                record.status === 'PARTIAL_RECEIVED' ? 'text-amber-600' : 'text-green-600'
              }`}
            >
              Đã nhận: {formatNumber(record.totalReceivedQuantity)}
            </span>
          )}
        </div>
      ),
    },
    {
      title: 'Ngày dự kiến giao',
      dataIndex: 'expectedDeliveryDate',
      key: 'expectedDeliveryDate',
      width: 150,
      render: (val?: string) => formatDateTime(val),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (status: StockTransfer['status']) => {
        const config = TRANSFER_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
          icon: null,
        }
        return (
          <Tag color={config.color} icon={config.icon} className="font-medium py-0.5 px-2">
            {config.label}
          </Tag>
        )
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 140,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 170,
      align: 'center',
      render: (_, record: StockTransfer) => {
        // Sửa, duyệt xuất, hủy: ADMIN hoặc Quản lý của kho nguồn; nhận hàng: ADMIN hoặc Quản lý của kho đích
        const isDraft = record.status === 'DRAFT' && can('transfer.manage', record.sourceWarehouseId)
        const isInTransit =
          record.status === 'IN_TRANSIT' && can('transfer.receive', record.destinationWarehouseId)

        return (
          <Space size="small">
            <Tooltip title="Xem chi tiết">
              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => onViewDetail(record)}
              />
            </Tooltip>

            {isDraft && (
              <>
                <Tooltip title="Chỉnh sửa lệnh">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined className="text-blue-600" />}
                    onClick={() => onEdit(record)}
                  />
                </Tooltip>

                <Tooltip title="Duyệt xuất kho (chuyển đi đường)">
                  <Popconfirm
                    title="Duyệt xuất kho điều chuyển"
                    description={
                      <div className="max-w-xs">
                        Hệ thống sẽ trừ tồn kho thực tế tại kho nguồn{' '}
                        <strong>{record.sourceWarehouseName}</strong> và chuyển lệnh sang trạng thái{' '}
                        <strong>Đang vận chuyển</strong>.
                      </div>
                    }
                    onConfirm={() => onDispatch(record)}
                    okText="Duyệt xuất"
                    cancelText="Hủy"
                    okButtonProps={{ type: 'primary' }}
                  >
                    <Button
                      type="text"
                      size="small"
                      icon={<RocketOutlined className="text-green-600" />}
                    />
                  </Popconfirm>
                </Tooltip>

                <Tooltip title="Hủy lệnh điều chuyển">
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    onClick={() => onCancel(record)}
                  />
                </Tooltip>
              </>
            )}

            {isInTransit && (
              <Tooltip title="Xác nhận nhận hàng tại kho đích">
                <Button
                  type="primary"
                  ghost
                  size="small"
                  icon={<InboxOutlined />}
                  onClick={() => onReceive(record)}
                >
                  Nhận hàng
                </Button>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <Table<StockTransfer>
      rowKey="id"
      columns={columns}
      dataSource={transfers}
      loading={loading}
      pagination={{
        current: pagination.current,
        pageSize: pagination.pageSize,
        total: pagination.total,
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50'],
        showTotal: (total, range) => `${range[0]}-${range[1]} trong số ${total} lệnh`,
        onChange: onChangePage,
      }}
      className="bg-white rounded-lg shadow-xs overflow-hidden"
    />
  )
}
