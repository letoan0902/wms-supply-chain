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
} from '@ant-design/icons'
import {
  Button,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
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
      width: 130,
      render: (code: string) => (
        <span className="text-xs font-medium text-slate-800 bg-slate-100 px-2 py-0.5 rounded border border-slate-200/60 tabular-nums">
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
          <div className="font-medium text-slate-900 text-sm">{record.name}</div>
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
            <div className="text-slate-600 flex items-center gap-1 tabular-nums">
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
          <span className="text-xs text-slate-700 bg-slate-100 px-1.5 py-0.5 rounded">
            {val}
          </span>
        ) : (
          <span className="text-slate-400 text-xs italic">-</span>
        ),
    },
    {
      title: 'Hạn mức nợ',
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      align: 'right',
      width: 140,
      render: (val: number) => (
        <span className="text-xs font-medium text-slate-700 tabular-nums">
          {formatCurrency(val)}
        </span>
      ),
    },
    {
      title: 'Công nợ hiện tại',
      key: 'currentDebt',
      align: 'right',
      width: 170,
      render: (_, record) => {
        const isOver = record.isOverCreditLimit
        const hasDebt = record.currentDebt > 0
        return (
          <div className="flex flex-col items-end">
            <span
              className={`text-xs font-semibold tabular-nums ${
                isOver ? 'text-red-600' : hasDebt ? 'text-slate-900' : 'text-slate-400'
              }`}
            >
              {formatCurrency(record.currentDebt)}
            </span>
            {isOver && (
              <span className="mt-0.5 text-[11px] text-red-600 font-medium inline-flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-red-500" /> Vượt hạn mức
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      render: (active: boolean) => (
        <span className="inline-flex items-center gap-1.5 text-xs text-slate-600">
          <span className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-emerald-500' : 'bg-slate-400'}`} />
          {active ? 'Hoạt động' : 'Tạm dừng'}
        </span>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={2}>
          {can('supplier.debt') && (
            <Tooltip title="Xem sổ chi tiết công nợ & giao dịch">
              <Button
                type="text"
                size="small"
                icon={<FileTextOutlined className="text-slate-600" />}
                onClick={() => onViewTransactions(record)}
              />
            </Tooltip>
          )}

          {can('supplier.transaction') && (
            <Tooltip title="Ghi nhận giao dịch công nợ mới">
              <Button
                type="text"
                size="small"
                icon={<DollarOutlined className="text-slate-600" />}
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
    <div className="bg-white border border-slate-200/80 rounded-lg p-5">
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
    </div>
  )
}
