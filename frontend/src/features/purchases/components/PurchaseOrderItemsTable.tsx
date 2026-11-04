import {
  DeleteOutlined,
  PlusOutlined,
  ShoppingOutlined,
} from '@ant-design/icons'
import {
  Button,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Product } from '../../products/types'
import { calculateLineTotals } from '../poCalculations'
import type { PurchaseOrderItemPayload } from '../types'
import { formatCurrency } from '../../../utils/format'

interface Props {
  items: PurchaseOrderItemPayload[]
  onChange: (items: PurchaseOrderItemPayload[]) => void
  products: Product[]
  disabled?: boolean
}

const VAT_OPTIONS = [
  { value: 0, label: '0%' },
  { value: 5, label: '5%' },
  { value: 8, label: '8% (Ưu đãi)' },
  { value: 10, label: '10% (Chuẩn)' },
]

export default function PurchaseOrderItemsTable({
  items,
  onChange,
  products,
  disabled = false,
}: Props) {
  // Thêm một dòng sản phẩm rỗng
  const handleAddItem = () => {
    const newItem: PurchaseOrderItemPayload = {
      skuCode: '',
      productName: '',
      uom: 'Cái',
      quantity: 1,
      unitPrice: 0,
      discountPercent: 0,
      taxRate: 8, // Mặc định 8% VAT phổ biến
      note: '',
    }
    onChange([...items, newItem])
  }

  // Xóa một dòng theo index
  const handleRemoveItem = (index: number) => {
    const updated = items.filter((_, i) => i !== index)
    onChange(updated)
  }

  // Cập nhật giá trị một trường của dòng
  const handleFieldChange = <K extends keyof PurchaseOrderItemPayload>(
    index: number,
    field: K,
    value: PurchaseOrderItemPayload[K]
  ) => {
    const updated = [...items]
    updated[index] = {
      ...updated[index],
      [field]: value,
    }
    onChange(updated)
  }

  // Khi người dùng chọn SKU từ danh mục sản phẩm có sẵn
  const handleSelectProduct = (index: number, productId: number) => {
    const selected = products.find((p) => p.id === productId)
    if (!selected) return

    const updated = [...items]
    const current = updated[index]
    updated[index] = {
      ...current,
      productId: selected.id,
      skuCode: selected.sku,
      productName: selected.name,
      uom: selected.uomName || selected.uomCode || current.uom || 'Cái',
      taxRate: selected.vatRate !== undefined && selected.vatRate !== null ? selected.vatRate : current.taxRate,
      // Nếu sản phẩm đã có giá mua gợi ý thì điền vào, nếu chưa thì giữ nguyên hoặc 0
      unitPrice:
        selected.purchasePrice !== undefined && selected.purchasePrice !== null && selected.purchasePrice > 0
          ? selected.purchasePrice
          : current.unitPrice > 0
          ? current.unitPrice
          : 0,
    }
    onChange(updated)
  }

  const columns: ColumnsType<PurchaseOrderItemPayload> = [
    {
      title: '#',
      key: 'index',
      width: 45,
      align: 'center',
      render: (_, __, index) => <span className="text-slate-400 text-xs">{index + 1}</span>,
    },
    {
      title: (
        <span>
          Mặt hàng / SKU <span className="text-red-500">*</span>
        </span>
      ),
      key: 'product',
      width: 320,
      render: (_, record, index) => {
        return (
          <div className="space-y-1">
            <Select
              showSearch
              placeholder="Chọn hoặc tìm SKU / Tên sản phẩm..."
              value={record.productId || (record.skuCode ? record.skuCode : undefined)}
              disabled={disabled}
              className="w-full"
              filterOption={(input, option) => {
                const label = String(option?.label ?? '')
                return label.toLowerCase().includes(input.toLowerCase())
              }}
              options={products.map((p) => ({
                value: p.id,
                label: `[${p.sku}] ${p.name} (${p.uomName || p.uomCode || 'ĐV'})`,
              }))}
              onChange={(val) => {
                if (typeof val === 'number') {
                  handleSelectProduct(index, val)
                }
              }}
              dropdownRender={(menu) => (
                <div>
                  {menu}
                  <div className="p-2 border-t border-slate-100 text-[11px] text-slate-500 bg-slate-50">
                    💡 Hoặc nhập tên / mã SKU tự do ở ô bên dưới nếu chưa có trong danh mục.
                  </div>
                </div>
              )}
            />

            {/* Ô nhập linh hoạt cho mã SKU và Tên nếu cần chỉnh sửa riêng */}
            <div className="grid grid-cols-2 gap-1 mt-1">
              <Input
                size="small"
                placeholder="Mã SKU *"
                value={record.skuCode}
                disabled={disabled}
                onChange={(e) => handleFieldChange(index, 'skuCode', e.target.value)}
                className="font-mono text-xs"
              />
              <Input
                size="small"
                placeholder="Tên mặt hàng *"
                value={record.productName}
                disabled={disabled}
                onChange={(e) => handleFieldChange(index, 'productName', e.target.value)}
                className="text-xs"
              />
            </div>
          </div>
        )
      },
    },
    {
      title: 'ĐVT',
      key: 'uom',
      width: 90,
      render: (_, record, index) => (
        <Input
          size="middle"
          placeholder="ĐVT"
          value={record.uom ?? ''}
          disabled={disabled}
          onChange={(e) => handleFieldChange(index, 'uom', e.target.value)}
          className="text-center text-xs"
        />
      ),
    },
    {
      title: (
        <span>
          Số lượng <span className="text-red-500">*</span>
        </span>
      ),
      key: 'quantity',
      width: 120,
      align: 'right',
      render: (_, record, index) => (
        <InputNumber
          min={0.001}
          step={1}
          value={record.quantity}
          disabled={disabled}
          onChange={(val) => handleFieldChange(index, 'quantity', val ?? 0)}
          className="w-full text-right"
        />
      ),
    },
    {
      title: (
        <span>
          Đơn giá mua (đ) <span className="text-red-500">*</span>
        </span>
      ),
      key: 'unitPrice',
      width: 150,
      align: 'right',
      render: (_, record, index) => (
        <InputNumber
          min={0}
          step={1000}
          value={record.unitPrice}
          disabled={disabled}
          formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={(val) => {
            const parsed = val?.replace(/\$\s?|(,*)/g, '')
            return parsed ? Number(parsed) : 0
          }}
          onChange={(val) => handleFieldChange(index, 'unitPrice', val ?? 0)}
          className="w-full text-right font-medium"
        />
      ),
    },
    {
      title: 'Chiết khấu (%)',
      key: 'discountPercent',
      width: 125,
      align: 'right',
      render: (_, record, index) => {
        const calc = calculateLineTotals(record)
        return (
          <div className="flex flex-col items-end">
            <InputNumber
              min={0}
              max={100}
              step={1}
              value={record.discountPercent ?? 0}
              disabled={disabled}
              addonAfter="%"
              onChange={(val) => handleFieldChange(index, 'discountPercent', val ?? 0)}
              className="w-full text-right"
            />
            {calc.discount > 0 && (
              <span className="text-[11px] text-amber-600 mt-0.5 font-medium">
                -{formatCurrency(calc.discount)}
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'VAT (%)',
      key: 'taxRate',
      width: 125,
      align: 'right',
      render: (_, record, index) => {
        const calc = calculateLineTotals(record)
        return (
          <div className="flex flex-col items-end">
            <Select
              value={record.taxRate ?? 0}
              disabled={disabled}
              options={VAT_OPTIONS}
              onChange={(val) => handleFieldChange(index, 'taxRate', val)}
              className="w-full text-right"
            />
            {calc.tax > 0 && (
              <span className="text-[11px] text-blue-600 mt-0.5">
                +{formatCurrency(calc.tax)}
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'Thành tiền (VNĐ)',
      key: 'lineTotal',
      width: 160,
      align: 'right',
      render: (_, record) => {
        const calc = calculateLineTotals(record)
        return (
          <div className="flex flex-col items-end">
            <span className="font-bold text-slate-800 text-sm">
              {formatCurrency(calc.lineTotal)}
            </span>
            <span className="text-[10px] text-slate-400">
              Tiền hàng: {formatCurrency(calc.rawSubtotal)}
            </span>
          </div>
        )
      },
    },
    {
      title: 'Ghi chú dòng',
      key: 'note',
      width: 150,
      render: (_, record, index) => (
        <Input
          size="middle"
          placeholder="Quy cách, date..."
          value={record.note ?? ''}
          disabled={disabled}
          onChange={(e) => handleFieldChange(index, 'note', e.target.value)}
          className="text-xs"
        />
      ),
    },
    {
      title: '',
      key: 'action',
      width: 50,
      align: 'center',
      render: (_, __, index) => (
        <Tooltip title="Xóa dòng này">
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            disabled={disabled}
            onClick={() => handleRemoveItem(index)}
          />
        </Tooltip>
      ),
    },
  ]

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShoppingOutlined className="text-blue-600" />
          <span className="font-semibold text-slate-800 text-sm">
            Danh sách mặt hàng đặt mua ({items.length} dòng)
          </span>
        </div>

        <Space>
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={handleAddItem}
            disabled={disabled}
            className="text-blue-600 border-blue-400 hover:border-blue-600"
          >
            Thêm dòng sản phẩm
          </Button>
        </Space>
      </div>

      {items.length === 0 ? (
        <div className="border border-dashed border-slate-200 rounded-lg p-6 bg-slate-50 text-center">
          <Empty
            description={
              <span className="text-slate-500 text-sm">
                Chưa có mặt hàng nào trong đơn mua. Vui lòng bấm nút bên dưới để thêm mặt hàng.
              </span>
            }
          >
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleAddItem}
              disabled={disabled}
            >
              Thêm dòng sản phẩm đầu tiên
            </Button>
          </Empty>
        </div>
      ) : (
        <Table
          rowKey={(_, index) => String(index)}
          columns={columns}
          dataSource={items}
          pagination={false}
          size="small"
          scroll={{ x: 1200 }}
          bordered
          className="bg-white rounded-lg shadow-2xs"
        />
      )}
    </div>
  )
}
