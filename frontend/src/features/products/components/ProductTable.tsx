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
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import * as XLSX from 'xlsx'
import type { Category, Product } from '../types'
import { formatCurrency, formatNumber } from '../../../utils/format'

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
      width: 56,
      align: 'center',
      render: (url?: string) =>
        url ? (
          <Image
            src={url}
            width={38}
            height={38}
            className="rounded object-cover border border-slate-200/80"
            fallback="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='38' height='38' viewBox='0 0 24 24' fill='%2394a3b8'><path d='M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z'/></svg>"
          />
        ) : (
          <div className="w-9 h-9 mx-auto rounded bg-slate-100 border border-slate-200/60 flex items-center justify-center text-slate-400">
            <PictureOutlined className="text-xs" />
          </div>
        ),
    },
    {
      title: 'Mã SKU & Tên hàng hóa',
      key: 'name',
      render: (_: unknown, record: Product) => (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-slate-800 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200/70 inline-flex items-center">
              {record.sku}
            </span>
            {record.barcode && (
              <span className="text-[11px] text-slate-400 inline-flex items-center gap-1">
                <BarcodeOutlined className="text-[10px]" /> {record.barcode}
              </span>
            )}
          </div>
          <div className="font-medium text-slate-900 leading-tight">
            {record.name}
          </div>
          {record.description && (
            <div className="text-xs text-slate-500 truncate max-w-sm">{record.description}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Nhóm hàng',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 140,
      render: (name?: string) =>
        name ? (
          <span className="text-xs text-slate-600 font-normal">{name}</span>
        ) : (
          <span className="text-slate-400 text-xs">—</span>
        ),
    },
    {
      title: 'Đơn vị',
      key: 'uom',
      width: 90,
      align: 'center',
      render: (_: unknown, record: Product) => (
        <span className="text-xs font-medium text-slate-700 bg-slate-100/80 px-2 py-0.5 rounded border border-slate-200/70">
          {record.uomName || record.uomCode}
        </span>
      ),
    },
    {
      title: 'Giá bán / Giá vốn',
      key: 'price',
      width: 140,
      align: 'right',
      render: (_: unknown, record: Product) => (
        <div className="text-right">
          <div className="text-xs font-semibold text-slate-900 tabular-nums">
            {record.sellingPrice ? formatCurrency(record.sellingPrice) : '—'}
          </div>
          <div className="text-[11px] text-slate-400 tabular-nums mt-0.5">
            {record.purchasePrice ? `Vốn: ${formatCurrency(record.purchasePrice)}` : 'Vốn: —'}
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
      render: (vat?: number) => (
        <span className="text-xs text-slate-600">{vat ? `${vat}%` : '0%'}</span>
      ),
    },
    {
      title: 'Quản lý kho',
      key: 'flags',
      width: 130,
      render: (_: unknown, record: Product) => (
        <div className="flex flex-col gap-1 text-[11px]">
          {record.manageByLot ? (
            <span className="text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200/60 inline-block w-fit">
              Theo Lô
            </span>
          ) : null}
          {record.manageByExpiration ? (
            <span className="text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded border border-amber-200/60 inline-block w-fit">
              Theo Hạn
            </span>
          ) : null}
          {!record.manageByLot && !record.manageByExpiration && (
            <span className="text-slate-400">Tiêu chuẩn</span>
          )}
        </div>
      ),
    },
    {
      title: 'Định mức tồn',
      key: 'stockThresholds',
      width: 120,
      align: 'right',
      render: (_: unknown, record: Product) => (
        <div className="text-right text-[11px] space-y-0.5 tabular-nums">
          <div title="Tồn tối thiểu an toàn" className="text-slate-700">
            <span className="text-slate-400 text-[10px]">Min:</span> {formatNumber(record.safetyStock || 0)}
          </div>
          <div title="Điểm đặt hàng lại" className="text-slate-500">
            <span className="text-slate-400 text-[10px]">Đặt:</span> {formatNumber(record.reorderPoint || 0)}
          </div>
          <div title="Tồn tối đa" className="text-slate-400">
            <span className="text-slate-400 text-[10px]">Max:</span> {formatNumber(record.maxStock || 0)}
          </div>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      key: 'active',
      width: 110,
      align: 'center',
      render: (active: boolean) =>
        active ? (
          <span className="inline-flex items-center gap-1.5 text-xs text-emerald-700 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            Hoạt động
          </span>
        ) : (
          <span className="inline-flex items-center gap-1.5 text-xs text-slate-400 font-normal">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-300"></span>
            Tạm ngưng
          </span>
        ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 140,
      align: 'center',
      render: (_: unknown, record: Product) => (
        <Space size={2}>
          <Tooltip title="Cấu hình quy đổi đơn vị đo lường">
            <Button
              type="text"
              icon={<SwapOutlined />}
              size="small"
              className="text-slate-500 hover:text-slate-900 hover:bg-slate-100"
              onClick={() => onOpenConversions(record)}
            />
          </Tooltip>

          <Tooltip title="Xem & In tem mã vạch Barcode/QR">
            <Button
              type="text"
              icon={<BarcodeOutlined />}
              size="small"
              className="text-slate-500 hover:text-slate-900 hover:bg-slate-100"
              onClick={() => onOpenBarcode(record)}
            />
          </Tooltip>

          <Tooltip title="Chỉnh sửa thông tin">
            <Button
              type="text"
              icon={<EditOutlined />}
              size="small"
              className="text-slate-500 hover:text-slate-900 hover:bg-slate-100"
              onClick={() => onEdit(record)}
            />
          </Tooltip>

          <Popconfirm
            title="Ngưng hoạt động sản phẩm?"
            description="Mã hàng này sẽ chuyển sang trạng thái ngưng hoạt động."
            okText="Đồng ý"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => onDelete(record.id)}
          >
            <Tooltip title="Ngưng hoạt động">
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                size="small"
                className="text-slate-400 hover:text-rose-600 hover:bg-rose-50"
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-3">
      {/* Bộ lọc và thanh công cụ dữ liệu */}
      <div className="p-3 sm:p-4 bg-white rounded-lg border border-slate-200/80 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <Input
            placeholder="Tìm kiếm mã SKU, tên hoặc barcode..."
            prefix={<SearchOutlined className="text-slate-400 text-xs" />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleApplyFilter}
            className="w-64 sm:w-72"
            allowClear
          />

          <Select
            placeholder="Tất cả nhóm hàng"
            allowClear
            value={selectedCategory}
            onChange={(v) => setSelectedCategory(v)}
            className="w-44"
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
              { label: 'Hoạt động', value: true },
              { label: 'Tạm ngưng', value: false },
            ]}
          />

          <Button type="primary" onClick={handleApplyFilter} className="text-xs">
            Tìm kiếm
          </Button>

          <Button onClick={handleResetFilter} className="text-xs text-slate-600">
            Xóa lọc
          </Button>
        </div>

        <div className="flex items-center gap-2">
          <Button
            icon={<DownloadOutlined className="text-slate-500" />}
            onClick={handleExportExcel}
            disabled={products.length === 0}
            className="text-xs text-slate-700"
          >
            Xuất Excel
          </Button>
        </div>
      </div>

      {/* Bảng dữ liệu DataTable */}
      <div className="bg-white rounded-lg border border-slate-200/80 overflow-hidden">
        <Table
          dataSource={products}
          columns={columns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1050 }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (totalCount) => (
              <span className="text-xs text-slate-500">
                Tổng cộng <strong className="text-slate-800 font-semibold">{totalCount}</strong> mã SKU
              </span>
            ),
            onChange: (p, ps) => onPageChange(p - 1, ps),
          }}
          size="middle"
        />
      </div>
    </div>
  )
}
