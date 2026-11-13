import {
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
  Input,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import type { Warehouse } from '../types'
import { formatNumber } from '../../../utils/format'
import { useCan } from '../../auth/useCan'

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
  const can = useCan()
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
      width: 130,
      render: (code: string) => (
        <span className="text-xs font-medium text-slate-800 bg-slate-100 px-2 py-0.5 rounded border border-slate-200/60 tabular-nums">
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
          <div className="font-medium text-slate-900">{name}</div>
          <div className="text-xs text-slate-500 truncate max-w-xs">{record.address || '—'}</div>
        </div>
      ),
    },
    {
      title: 'Quản lý & Liên hệ',
      key: 'manager',
      width: 170,
      render: (_, record) => (
        <div className="text-xs">
          <div className="font-medium text-slate-800">{record.managerName || '—'}</div>
          <div className="text-slate-500 tabular-nums">{record.contactPhone || '—'}</div>
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
        <span className="text-xs font-medium text-slate-800 tabular-nums">
          {formatNumber(capacity || 0)}
        </span>
      ),
    },
    {
      title: 'Vị trí ô/kệ',
      key: 'locations',
      width: 130,
      align: 'center',
      render: (_, record) => (
        <div className="text-xs tabular-nums">
          <span className="text-slate-800 font-semibold">{record.occupiedLocations}</span>
          <span className="text-slate-400"> / </span>
          <span className="text-slate-600">{record.totalLocations}</span>
          <div className="text-[11px] text-slate-400 font-sans">đang dùng / tổng</div>
        </div>
      ),
    },
    {
      title: 'Tỷ lệ lấp đầy',
      dataIndex: 'utilizationRate',
      key: 'utilizationRate',
      width: 150,
      render: (rate: number) => {
        const percent = Math.min(rate || 0, 100)
        const strokeColor = percent > 85 ? '#ef4444' : percent > 65 ? '#f59e0b' : '#10b981'

        return (
          <div className="w-28">
            <div className="text-xs font-medium text-slate-700 mb-0.5 tabular-nums">
              {percent.toFixed(1)}%
            </div>
            <Progress
              percent={percent}
              size="small"
              showInfo={false}
              strokeColor={strokeColor}
              className="m-0"
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
      render: (active: boolean) => (
        <span className="inline-flex items-center gap-1.5 text-xs text-slate-600">
          <span className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-emerald-500' : 'bg-slate-400'}`} />
          {active ? 'Hoạt động' : 'Tạm dừng'}
        </span>
      ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 160,
      align: 'center',
      render: (_, record) => (
        <Space size={2}>
          {can('location.read', record.id) && (
            <Tooltip title="Xem sơ đồ vị trí ô/kệ">
              <Button
                type="text"
                size="small"
                icon={<ApartmentOutlined className="text-slate-600" />}
                onClick={() => onViewBins(record)}
              />
            </Tooltip>
          )}
          {can('warehouse.write', record.id) && (
          <Tooltip title="Chỉnh sửa kho">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined className="text-slate-600" />}
              onClick={() => onEditWarehouse(record)}
            />
          </Tooltip>
          )}
          {can('warehouse.write', record.id) && (
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
                    <LockOutlined className="text-slate-400" />
                  ) : (
                    <UnlockOutlined className="text-slate-600" />
                  )
                }
              />
            </Popconfirm>
          </Tooltip>
          )}
          {can('warehouse.delete') && (
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
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="bg-white border border-slate-200/80 rounded-lg p-5">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
        <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">
          <Input
            placeholder="Tìm theo mã hoặc tên kho..."
            prefix={<SearchOutlined className="text-slate-400" />}
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
            className="w-40"
            options={[
              { value: 'all', label: 'Tất cả trạng thái' },
              { value: 'true', label: 'Đang hoạt động' },
              { value: 'false', label: 'Tạm dừng' },
            ]}
          />
        </div>

        {can('warehouse.create') && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={onAddWarehouse}
          >
            Thêm kho mới
          </Button>
        )}
      </div>

      <Table
        columns={columns}
        dataSource={warehouses}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        locale={{ emptyText: 'Chưa có kho hàng nào được tạo' }}
      />
    </div>
  )
}

