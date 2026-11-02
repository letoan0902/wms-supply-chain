import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  SearchOutlined,
  UnlockOutlined,
  ApartmentOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Input,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import type { Warehouse } from '../types'
import { formatNumber } from '../../../utils/format'

interface Props {
  warehouses: Warehouse[]
  loading: boolean
  onAddWarehouse: () => void
  onEditWarehouse: (warehouse: Warehouse) => void
  onToggleStatus: (warehouse: Warehouse) => void
  onDeleteWarehouse: (warehouse: Warehouse) => void
  onViewBins: (warehouse: Warehouse) => void
  onSearch: (keyword: string, active?: boolean) => void
}

export default function WarehouseTable({
  warehouses,
  loading,
  onAddWarehouse,
  onEditWarehouse,
  onToggleStatus,
  onDeleteWarehouse,
  onViewBins,
  onSearch,
}: Props) {
  const [keyword, setKeyword] = useState('')
  const [activeFilter, setActiveFilter] = useState<string>('all')

  const handleSearch = (newKeyword: string, newActive: string) => {
    const activeBool = newActive === 'all' ? undefined : newActive === 'true'
    onSearch(newKeyword, activeBool)
  }

  const columns: ColumnsType<Warehouse> = [
    {
      title: 'Mã kho',
      dataIndex: 'code',
      key: 'code',
      width: 140,
      render: (code: string) => (
        <span className="font-mono font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
          {code}
        </span>
      ),
    },
    {
      title: 'Tên kho bãi',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record) => (
        <div>
          <div className="font-semibold text-gray-900">{name}</div>
          <div className="text-xs text-gray-500 truncate max-w-xs">{record.address || 'Chưa có địa chỉ'}</div>
        </div>
      ),
    },
    {
      title: 'Quản lý & Liên hệ',
      key: 'manager',
      width: 180,
      render: (_, record) => (
        <div className="text-xs">
          <div className="font-medium text-gray-800">{record.managerName || '—'}</div>
          <div className="text-gray-500">{record.contactPhone || '—'}</div>
        </div>
      ),
    },
    {
      title: 'Sức chứa',
      dataIndex: 'capacity',
      key: 'capacity',
      width: 110,
      align: 'right',
      render: (capacity: number) => (
        <span className="font-medium">{formatNumber(capacity || 0)}</span>
      ),
    },
    {
      title: 'Vị trí Ô/Kệ',
      key: 'locations',
      width: 130,
      align: 'center',
      render: (_, record) => (
        <div className="text-xs">
          <span className="text-blue-600 font-semibold">{record.occupiedLocations}</span>
          <span className="text-gray-400"> / </span>
          <span>{record.totalLocations}</span>
          <div className="text-[11px] text-gray-400">đang dùng / tổng</div>
        </div>
      ),
    },
    {
      title: 'Tỷ lệ lấp đầy',
      dataIndex: 'utilizationRate',
      key: 'utilizationRate',
      width: 160,
      render: (rate: number) => {
        const percent = Math.min(rate || 0, 100)
        let strokeColor = '#52c41a'
        if (percent > 85) strokeColor = '#f5222d'
        else if (percent > 65) strokeColor = '#fa8c16'

        return (
          <div className="w-28">
            <div className="text-xs font-semibold mb-0.5">{percent.toFixed(1)}%</div>
            <Progress
              percent={percent}
              size="small"
              showInfo={false}
              strokeColor={strokeColor}
            />
          </div>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      align: 'center',
      render: (active: boolean) =>
        active ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            Hoạt động
          </Tag>
        ) : (
          <Tag color="default" icon={<LockOutlined />}>
            Tạm dừng
          </Tag>
        ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 180,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Xem sơ đồ vị trí Ô/Kệ">
            <Button
              type="text"
              size="small"
              icon={<ApartmentOutlined className="text-blue-600" />}
              onClick={() => onViewBins(record)}
            />
          </Tooltip>
          <Tooltip title="Chỉnh sửa kho">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined className="text-amber-600" />}
              onClick={() => onEditWarehouse(record)}
            />
          </Tooltip>
          <Tooltip title={record.active ? 'Khóa kho' : 'Mở khóa kho'}>
            <Popconfirm
              title={record.active ? 'Khóa kho hàng này?' : 'Mở khóa kho hàng này?'}
              description={
                record.active
                  ? 'Kho bị khóa sẽ không được phép nhập/xuất hàng mới.'
                  : 'Kho sẽ trở lại trạng thái sẵn sàng giao dịch.'
              }
              onConfirm={() => onToggleStatus(record)}
              okText="Đồng ý"
              cancelText="Hủy"
            >
              <Button
                type="text"
                size="small"
                icon={
                  record.active ? (
                    <LockOutlined className="text-gray-500" />
                  ) : (
                    <UnlockOutlined className="text-green-600" />
                  )
                }
              />
            </Popconfirm>
          </Tooltip>
          <Tooltip title="Xóa kho">
            <Popconfirm
              title="Xác nhận xóa kho hàng?"
              description="Chỉ được phép xóa kho khi không còn vị trí ô kệ và không có hàng tồn."
              onConfirm={() => onDeleteWarehouse(record)}
              okText="Xóa"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <Card variant="borderless" className="shadow-xs">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
        <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">
          <Input
            placeholder="Tìm theo mã hoặc tên kho..."
            prefix={<SearchOutlined className="text-gray-400" />}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              handleSearch(e.target.value, activeFilter)
            }}
            className="w-full sm:w-64"
            allowClear
          />
          <Select
            value={activeFilter}
            onChange={(val) => {
              setActiveFilter(val)
              handleSearch(keyword, val)
            }}
            className="w-36"
            options={[
              { value: 'all', label: 'Tất cả trạng thái' },
              { value: 'true', label: 'Đang hoạt động' },
              { value: 'false', label: 'Tạm dừng' },
            ]}
          />
        </div>

        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={onAddWarehouse}
          className="bg-blue-600"
        >
          Thêm kho mới
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={warehouses}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        locale={{ emptyText: 'Chưa có kho hàng nào được tạo' }}
      />
    </Card>
  )
}

