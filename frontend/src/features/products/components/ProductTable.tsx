import { useState } from 'react'
import {
  BarcodeOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PictureOutlined,
  SearchOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import {
  Button,
  Image,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import * as XLSX from 'xlsx'
import type { Category, Product } from '../types'

interface ProductTableProps {
  products: Product[]
  total: number
  loading: boolean
  page: number
  pageSize: number
  categories: Category[]
  onPageChange: (page: number, pageSize: number) => void
  onSearch: (keyword?: string, categoryId?: number, active?: boolean) => void
  onEdit: (product: Product) => void
  onDelete: (id: number) => Promise<void>
  onOpenConversions: (product: Product) => void
  onOpenBarcode: (product: Product) => void
}

export default function ProductTable({
  products,
  total,
  loading,
  page,
  pageSize,
  categories,
  onPageChange,
  onSearch,
  onEdit,
  onDelete,
  onOpenConversions,
  onOpenBarcode,
}: ProductTableProps) {
  const [keyword, setKeyword] = useState<string>('')
  const [selectedCategory, setSelectedCategory] = useState<number | undefined>(undefined)
  const [selectedActive, setSelectedActive] = useState<boolean | undefined>(undefined)

  const handleApplyFilter = () => {
    onSearch(keyword.trim() || undefined, selectedCategory, selectedActive)
  }

  const handleResetFilter = () => {
    setKeyword('')
    setSelectedCategory(undefined)
    setSelectedActive(undefined)
    onSearch(undefined, undefined, undefined)
  }

  const handleExportExcel = () => {
    const exportData = products.map((p, idx) => ({
      STT: idx + 1,
      'Mã SKU': p.sku,
      'Tên sản phẩm': p.name,
      'Mã vạch (Barcode)': p.barcode || '',
      'Nhóm hàng': p.categoryName || '',
      'Đơn vị tính': p.uomName || p.uomCode,
      'Giá nhập (VNĐ)': p.purchasePrice || 0,
      'Giá bán (VNĐ)': p.sellingPrice || 0,
      'Thuế VAT (%)': p.vatRate || 0,
      'Quản lý theo lô': p.manageByLot ? 'Có' : 'Không',
      'Quản lý hạn dùng': p.manageByExpiration ? 'Có' : 'Không',
      'Tồn an toàn': p.safetyStock || 0,
      'Điểm đặt lại': p.reorderPoint || 0,
      'Tồn tối đa': p.maxStock || 0,
      'Trạng thái': p.active ? 'Đang hoạt động' : 'Ngưng hoạt động',
      'Mô tả': p.description || '',
    }))

    const worksheet = XLSX.utils.json_to_sheet(exportData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Danh_muc_SKU')
    XLSX.writeFile(workbook, `WMS_Danh_muc_SKU_${new Date().toISOString().slice(0, 10)}.xlsx`)
  }

  const columns: ColumnsType<Product> = [
    {
      title: 'Ảnh',
      dataIndex: 'imageUrl',
      key: 'imageUrl',
      width: 65,
      align: 'center',
      render: (url?: string) =>
        url ? (
          <Image
            src={url}
            width={44}
            height={44}
            className="rounded object-cover border border-slate-200"
            fallback="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 24 24' fill='%23ccc'><path d='M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z'/></svg>"
          />
        ) : (
          <div className="w-11 h-11 mx-auto rounded bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-400">
            <PictureOutlined />
          </div>
        ),
    },
    {
      title: 'Mã SKU & Tên hàng hóa',
      key: 'name',
      render: (_: unknown, record: Product) => (
        <div className="space-y-0.5">
          <div className="flex items-center gap-2">
            <Tag color="blue" className="font-mono font-medium m-0">
              {record.sku}
            </Tag>
            {record.barcode && (
              <span className="text-xs text-slate-500 font-mono flex items-center gap-1">
                <BarcodeOutlined /> {record.barcode}
              </span>
            )}
          </div>
          <div className="font-medium text-slate-800 hover:text-blue-600 transition-colors">
            {record.name}
          </div>
          {record.description && (
            <div className="text-xs text-slate-500 truncate max-w-xs">{record.description}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Nhóm hàng',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 130,
      render: (name?: string) =>
        name ? <Tag color="geekblue">{name}</Tag> : <span className="text-slate-400">-</span>,
    },
    {
      title: 'Đơn vị cơ sở',
      key: 'uom',
      width: 100,
      align: 'center',
      render: (_: unknown, record: Product) => (
        <Tag color="purple" className="font-medium">
          {record.uomName || record.uomCode}
        </Tag>
      ),
    },
    {
      title: 'Giá bán / Giá nhập',
      key: 'price',
      width: 150,
      render: (_: unknown, record: Product) => (
        <div className="text-xs">
          <div className="font-semibold text-slate-900">
            {record.sellingPrice ? `${record.sellingPrice.toLocaleString('vi-VN')} đ` : '-'}
          </div>
          <div className="text-slate-400">
            Vốn: {record.purchasePrice ? `${record.purchasePrice.toLocaleString('vi-VN')} đ` : '-'}
          </div>
        </div>
      ),
    },
    {
      title: 'VAT',
      dataIndex: 'vatRate',
      key: 'vatRate',
      width: 70,
      align: 'center',
      render: (vat?: number) => (vat ? <span className="font-mono text-xs">{vat}%</span> : '0%'),
    },
    {
      title: 'Quản lý kho',
      key: 'flags',
      width: 140,
      render: (_: unknown, record: Product) => (
        <div className="flex flex-col gap-1 text-xs">
          {record.manageByLot ? (
            <Tag color="cyan" className="m-0 text-[11px]">
              Theo Lô (Lot)
            </Tag>
          ) : null}
          {record.manageByExpiration ? (
            <Tag color="orange" className="m-0 text-[11px]">
              Theo Hạn (Exp)
            </Tag>
          ) : null}
          {!record.manageByLot && !record.manageByExpiration && (
            <span className="text-slate-400">Chuẩn</span>
          )}
        </div>
      ),
    },
    {
      title: 'Định mức tồn',
      key: 'stockThresholds',
      width: 130,
      render: (_: unknown, record: Product) => (
        <div className="text-xs space-y-0.5">
          <div title="Tồn an toàn tối thiểu" className="text-amber-700 font-mono">
            Min: {record.safetyStock || 0}
          </div>
          <div title="Điểm đặt lại" className="text-blue-700 font-mono">
            Đặt: {record.reorderPoint || 0}
          </div>
          <div title="Tồn tối đa" className="text-slate-500 font-mono">
            Max: {record.maxStock || 0}
          </div>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      align: 'center',
      render: (active: boolean) =>
        active ? (
          <Tag color="success">Đang dùng</Tag>
        ) : (
          <Tag color="default">Tạm ngưng</Tag>
        ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 160,
      align: 'center',
      render: (_: unknown, record: Product) => (
        <Space size={4}>
          <Tooltip title="Cấu hình quy đổi đơn vị (1 Thùng = 4 Lốc = 24 Chai)">
            <Button
              type="text"
              icon={<SwapOutlined className="text-blue-600" />}
              size="small"
              onClick={() => onOpenConversions(record)}
            />
          </Tooltip>

          <Tooltip title="Xem & In tem mã vạch (Barcode / QR Code)">
            <Button
              type="text"
              icon={<BarcodeOutlined className="text-emerald-600" />}
              size="small"
              onClick={() => onOpenBarcode(record)}
            />
          </Tooltip>

          <Tooltip title="Chỉnh sửa sản phẩm">
            <Button
              type="text"
              icon={<EditOutlined className="text-slate-600" />}
              size="small"
              onClick={() => onEdit(record)}
            />
          </Tooltip>

          <Popconfirm
            title="Xác nhận ngưng hoạt động sản phẩm này?"
            description="Sản phẩm sẽ chuyển sang trạng thái ngưng hoạt động (xóa mềm) để bảo toàn tham chiếu tồn kho."
            okText="Ngưng hoạt động"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => onDelete(record.id)}
          >
            <Tooltip title="Ngưng hoạt động (Xóa mềm)">
              <Button type="text" danger icon={<DeleteOutlined />} size="small" />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      {/* Bộ lọc và tìm kiếm */}
      <div className="p-4 bg-white rounded-lg border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <Input
            placeholder="Tìm theo mã SKU, tên hàng hoặc barcode..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleApplyFilter}
            className="w-72"
            allowClear
          />

          <Select
            placeholder="Tất cả nhóm hàng"
            allowClear
            value={selectedCategory}
            onChange={(v) => setSelectedCategory(v)}
            className="w-48"
            options={categories.map((c) => ({
              label: c.name,
              value: c.id,
            }))}
          />

          <Select
            placeholder="Trạng thái"
            allowClear
            value={selectedActive}
            onChange={(v) => setSelectedActive(v)}
            className="w-36"
            options={[
              { label: 'Đang hoạt động', value: true },
              { label: 'Ngưng hoạt động', value: false },
            ]}
          />

          <Button type="primary" onClick={handleApplyFilter}>
            Lọc
          </Button>

          <Button onClick={handleResetFilter}>Đặt lại</Button>
        </div>

        <div className="flex items-center gap-2">
          <Button icon={<DownloadOutlined />} onClick={handleExportExcel} disabled={products.length === 0}>
            Xuất Excel
          </Button>
        </div>
      </div>

      {/* Bảng dữ liệu DataTable */}
      <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
        <Table
          dataSource={products}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (totalCount) => `Tổng cộng ${totalCount} sản phẩm (SKU)`,
            onChange: (p, ps) => onPageChange(p - 1, ps),
          }}
          size="middle"
        />
      </div>
    </div>
  )
}
