import {
  CheckCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Button,
  Empty,
  Input,
  InputNumber,
  Select,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { formatCurrency, formatNumber } from '../../../utils/format'
import type { Product } from '../../products/types'
import { calculateLineTotals } from '../soCalculations'
import type { SalesOrderItemPayload } from '../types'

const { Text } = Typography

interface Props {
  items: SalesOrderItemPayload[]
  products: Product[]
  loadingProducts?: boolean
  warehouseId?: number
  onSearchProduct: (keyword: string) => void
  onItemChange: (key: string, field: keyof SalesOrderItemPayload, value: unknown) => void
  onProductSelect: (key: string, product: Product) => void
  onAddItem: () => void
  onRemoveItem: (key: string) => void
}

export default function SalesOrderItemsTable({
  items,
  products,
  loadingProducts,
  onSearchProduct,
  onItemChange,
  onProductSelect,
  onAddItem,
  onRemoveItem,
}: Props) {
  const columns: ColumnsType<SalesOrderItemPayload> = [
    {
      title: '#',
      key: 'index',
      width: 48,
      align: 'center',
      render: (_val, _record, index) => <Text type="secondary">{index + 1}</Text>,
    },
    {
      title: 'Mặt hàng (SKU)',
      key: 'product',
      width: 280,
      render: (_val, record) => (
        <div className="space-y-1">
          <Select
            showSearch
            placeholder="Tìm theo mã SKU hoặc tên sản phẩm..."
            value={record.productId ? String(record.productId) : undefined}
            loading={loadingProducts}
            filterOption={false}
            onSearch={onSearchProduct}
            onChange={(val) => {
              const prod = products.find((p) => p.id === Number(val))
              if (prod) {
                onProductSelect(record._key, prod)
              }
            }}
            className="w-full"
            options={products.map((p) => ({
              value: String(p.id),
              label: (
                <div className="flex items-center justify-between py-0.5">
                  <div>
                    <span className="font-semibold text-blue-600 mr-2">{p.sku}</span>
                    <span>{p.name}</span>
                  </div>
                  <Tag color="cyan" className="ml-1 text-xs">
                    {p.uomName || p.uomCode || 'Cái'}
                  </Tag>
                </div>
              ),
            }))}
          />
          {record.productName && (
            <div className="text-xs text-gray-500 truncate" title={record.productName}>
              {record.productName}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'ĐVT',
      dataIndex: 'uom',
      key: 'uom',
      width: 90,
      render: (val, record) => (
        <Input
          size="small"
          value={val || 'Cái'}
          onChange={(e) => onItemChange(record._key, 'uom', e.target.value)}
          placeholder="ĐVT"
        />
      ),
    },
    {
      title: 'Tồn khả dụng (ATP)',
      key: 'atp',
      width: 170,
      render: (_val, record) => {
        if (!record.productId) {
          return <Text type="secondary" className="text-xs">Chưa chọn SKU</Text>
        }

        const atp = record.atpQuantity ?? 0
        const onHand = record.onHandQuantity ?? 0
        const reserved = record.reservedQuantity ?? 0
        const qty = Number(record.quantity) || 0
        const isShortage = qty > atp

        return (
          <div className="text-xs space-y-1">
            <div className="flex items-center space-x-1">
              <span className="text-gray-500">Khả dụng:</span>
              <span className={`font-bold ${isShortage ? 'text-red-600' : 'text-emerald-600'}`}>
                {formatNumber(atp)} {record.uom || ''}
              </span>
            </div>
            <div className="text-[11px] text-gray-400">
              Tồn: {formatNumber(onHand)} | Giữ: {formatNumber(reserved)}
            </div>
            {isShortage ? (
              <Tooltip title={`Tồn khả dụng (${atp}) không đủ cho số lượng đặt (${qty}). Đơn sẽ chuyển sang trạng thái BACKORDER.`}>
                <Tag color="warning" icon={<WarningOutlined />} className="text-[11px] m-0">
                  Thiếu {formatNumber(qty - atp)} (Backorder)
                </Tag>
              </Tooltip>
            ) : (
              <Tag color="success" icon={<CheckCircleOutlined />} className="text-[11px] m-0">
                Đủ tồn kho
              </Tag>
            )}
          </div>
        )
      },
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 110,
      render: (val, record) => (
        <InputNumber
          size="small"
          min={0.001}
          step={1}
          value={val}
          onChange={(newVal) => onItemChange(record._key, 'quantity', newVal ?? 1)}
          className="w-full"
        />
      ),
    },
    {
      title: 'Đơn giá bán',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 140,
      render: (val, record) => (
        <InputNumber
          size="small"
          min={0}
          step={1000}
          value={val}
          formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={(v) => (v ? Number(v.replace(/\$\s?|(,*)/g, '')) : 0)}
          onChange={(newVal) => onItemChange(record._key, 'unitPrice', newVal ?? 0)}
          className="w-full"
        />
      ),
    },
    {
      title: 'Chiết khấu (%)',
      dataIndex: 'discountPercent',
      key: 'discountPercent',
      width: 110,
      render: (val, record) => (
        <InputNumber
          size="small"
          min={0}
          max={100}
          step={1}
          value={val ?? 0}
          onChange={(newVal) => onItemChange(record._key, 'discountPercent', newVal ?? 0)}
          className="w-full"
        />
      ),
    },
    {
      title: 'VAT (%)',
      dataIndex: 'taxRate',
      key: 'taxRate',
      width: 95,
      render: (val, record) => (
        <Select
          size="small"
          value={val ?? 8}
          onChange={(newVal) => onItemChange(record._key, 'taxRate', newVal)}
          className="w-full"
          options={[
            { label: '0%', value: 0 },
            { label: '5%', value: 5 },
            { label: '8%', value: 8 },
            { label: '10%', value: 10 },
          ]}
        />
      ),
    },
    {
      title: 'Thành tiền',
      key: 'lineTotal',
      width: 140,
      align: 'right',
      render: (_val, record) => {
        const { lineTotal } = calculateLineTotals(record)
        return (
          <div className="font-semibold text-slate-800">
            {formatCurrency(lineTotal)}
          </div>
        )
      },
    },
    {
      title: '',
      key: 'action',
      width: 50,
      align: 'center',
      render: (_val, record) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => onRemoveItem(record._key)}
          disabled={items.length <= 1}
        />
      ),
    },
  ]

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Text strong className="text-base text-slate-700">
          Danh sách mặt hàng đặt bán ({items.length})
        </Text>
        <Button
          type="dashed"
          icon={<PlusOutlined />}
          onClick={onAddItem}
          size="small"
        >
          Thêm dòng mặt hàng
        </Button>
      </div>

      <Table
        rowKey="_key"
        columns={columns}
        dataSource={items}
        pagination={false}
        size="small"
        bordered
        locale={{
          emptyText: <Empty description="Chưa có dòng sản phẩm nào" />,
        }}
        scroll={{ x: 1100 }}
      />
    </div>
  )
}
