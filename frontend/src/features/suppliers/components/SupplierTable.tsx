import {
  ClearOutlined,
  DeleteOutlined,
  DollarOutlined,
  EditOutlined,
  FileTextOutlined,
  MailOutlined,
  PhoneOutlined,
  SearchOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Badge,
  Button,
  Card,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { formatCurrency } from '../../../utils/format'
import type { Supplier } from '../types'
import { useCan } from '../../auth/useCan'

interface Props {
  suppliers: Supplier[]
  total: number
  loading: boolean
  page: number
  pageSize: number
  onPageChange: (page: number, pageSize: number) => void
  onSearch: (keyword?: string, active?: boolean) => void
  onEdit: (supplier: Supplier) => void
  onDelete: (id: number) => Promise<void>
  onViewTransactions: (supplier: Supplier) => void
  onRecordTransaction: (supplier: Supplier) => void
}

export default function SupplierTable({
  suppliers,
  total,
  loading,
  page,
  pageSize,
  onPageChange,
  onSearch,
  onEdit,
  onDelete,
  onViewTransactions,
  onRecordTransaction,
}: Props) {
  const can = useCan()
  const [keywordInput, setKeywordInput] = useState('')
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>(undefined)

  const handleApplyFilter = () => {
    onSearch(
      keywordInput.trim() ? keywordInput.trim() : undefined,
      activeFilter
    )
  }

  const handleResetFilter = () => {
    setKeywordInput('')
    setActiveFilter(undefined)
    onSearch(undefined, undefined)
  }

  const columns: ColumnsType<Supplier> = [
    {
      title: 'Mã NCC',
      dataIndex: 'code',
      key: 'code',
      width: 140,
      render: (code: string) => (
        <span className="font-mono font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded text-xs">
          {code}
        </span>
      ),
    },
    {
      title: 'Nhà cung cấp / Mã số thuế',
      key: 'name',
      width: 250,
      render: (_, record) => (
        <div>
          <div className="font-semibold text-slate-800 text-sm">{record.name}</div>
          <div className="text-xs text-slate-400 mt-0.5">
            {record.taxCode ? `MST: ${record.taxCode}` : 'Chưa có MST'}
            {record.address && ` • ${record.address}`}
          </div>
        </div>
      ),
    },
    {
      title: 'Liên hệ',
      key: 'contact',
      width: 200,
      render: (_, record) => (
        <div className="text-xs space-y-0.5">
          {record.contactName && (
            <div className="text-slate-700 flex items-center gap-1">
              <UserOutlined className="text-slate-400" />
              <span>{record.contactName}</span>
            </div>
          )}
          {record.phone && (
            <div className="text-slate-600 flex items-center gap-1 font-mono">
              <PhoneOutlined className="text-slate-400" />
              <a href={`tel:${record.phone}`} className="hover:underline text-slate-700">
                {record.phone}
              </a>
            </div>
          )}
          {record.email && (
            <div className="text-slate-500 flex items-center gap-1 truncate max-w-[180px]">
              <MailOutlined className="text-slate-400" />
              <span title={record.email}>{record.email}</span>
            </div>
          )}
          {!record.contactName && !record.phone && !record.email && (
            <span className="text-slate-400 italic">Chưa cập nhật</span>
          )}
        </div>
      ),
    },
    {
      title: 'Điều khoản TT',
      dataIndex: 'paymentTerms',
      key: 'paymentTerms',
      width: 120,
      render: (val: string | null) =>
        val ? (
          <Tag color="cyan" className="font-mono text-xs">
            {val}
          </Tag>
        ) : (
          <span className="text-slate-400 text-xs italic">-</span>
        ),
    },
    {
      title: 'Hạn mức nợ',
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      align: 'right',
      width: 150,
      render: (val: number) => (
        <span className="font-medium text-slate-700">{formatCurrency(val)}</span>
      ),
    },
    {
      title: 'Công nợ hiện tại',
      key: 'currentDebt',
      align: 'right',
      width: 180,
      render: (_, record) => {
        const isOver = record.isOverCreditLimit
        const hasDebt = record.currentDebt > 0
        return (
          <div className="flex flex-col items-end">
            <span
              className={`font-bold text-sm ${
                isOver ? 'text-red-600' : hasDebt ? 'text-amber-600' : 'text-slate-400'
              }`}
            >
              {formatCurrency(record.currentDebt)}
            </span>
            {isOver && (
              <Tag color="error" className="mt-1 m-0 text-[10px] flex items-center gap-1">
                <WarningOutlined /> Vượt hạn mức
              </Tag>
            )}
          </div>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 130,
      render: (active: boolean) =>
        active ? (
          <Badge status="success" text={<span className="text-xs text-green-700">Hoạt động</span>} />
        ) : (
          <Badge status="default" text={<span className="text-xs text-slate-400">Tạm dừng</span>} />
        ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {can('supplier.debt') && (
            <Tooltip title="Xem sổ chi tiết công nợ & giao dịch">
              <Button
                type="text"
                size="small"
                icon={<FileTextOutlined className="text-blue-600" />}
                onClick={() => onViewTransactions(record)}
              />
            </Tooltip>
          )}

          {can('supplier.transaction') && (
            <Tooltip title="Ghi nhận giao dịch công nợ mới (Thanh toán / Hóa đơn)">
              <Button
                type="text"
                size="small"
                icon={<DollarOutlined className="text-amber-600" />}
                onClick={() => onRecordTransaction(record)}
              />
            </Tooltip>
          )}

          {can('supplier.write') && (
            <Tooltip title="Chỉnh sửa thông tin NCC">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined className="text-slate-600" />}
                onClick={() => onEdit(record)}
              />
            </Tooltip>
          )}

          {record.active && can('supplier.delete') && (
            <Tooltip title="Ngưng hoạt động nhà cung cấp này">
              <Popconfirm
                title="Xác nhận ngưng hoạt động?"
                description={`Bạn có chắc muốn chuyển NCC "${record.code}" sang trạng thái tạm dừng?`}
                onConfirm={() => onDelete(record.id)}
                okText="Đồng ý"
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
    <Card variant="borderless" className="shadow-xs">
      {/* Bộ lọc & Tìm kiếm */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap items-center gap-2 flex-1">
          <Input
            placeholder="Tìm theo Mã, Tên NCC, MST, SĐT, Người liên hệ..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onPressEnter={handleApplyFilter}
            className="w-full sm:w-80"
            allowClear
          />

          <Select
            placeholder="Trạng thái"
            value={activeFilter}
            onChange={(v) => setActiveFilter(v)}
            className="w-36"
            allowClear
            options={[
              { value: true, label: 'Đang hoạt động' },
              { value: false, label: 'Tạm dừng' },
            ]}
          />

          <Button type="primary" icon={<SearchOutlined />} onClick={handleApplyFilter}>
            Tìm kiếm
          </Button>

          {(keywordInput || activeFilter !== undefined) && (
            <Button icon={<ClearOutlined />} onClick={handleResetFilter}>
              Xóa bộ lọc
            </Button>
          )}
        </div>
      </div>

      {/* Bảng dữ liệu */}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={suppliers}
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (totalCount) => `Tổng cộng ${totalCount} nhà cung cấp`,
          onChange: (p, ps) => onPageChange(p - 1, ps),
        }}
      />
    </Card>
  )
}
