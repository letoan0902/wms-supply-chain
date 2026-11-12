import { Table } from 'antd'
import type { FrozenLocation, StockShortage } from '../../../services/stockDocuments'
import { formatNumber } from '../../../utils/format'

/** Tên sản phẩm theo id, lấy từ dòng hàng của chứng từ đang thao tác (máy chủ chỉ trả id và SKU). */
export type ProductNameLookup = (productId: number | null) => string | undefined

interface ShortageTableProps {
  shortages: StockShortage[]
  productName?: ProductNameLookup
}

/** Bảng thiếu hàng khi xác nhận đơn bán: sản phẩm, số lượng cần, số lượng giữ được, phần thiếu. */
export function StockShortageTable({ shortages, productName }: ShortageTableProps) {
  return (
    <Table<StockShortage>
      size="small"
      pagination={false}
      rowKey={(r, i) => `${r.productId ?? r.skuCode}-${i}`}
      dataSource={shortages}
      columns={[
        {
          title: 'Sản phẩm',
          key: 'product',
          render: (_, r) => (
            <div>
              <div className="font-semibold text-slate-800">{productName?.(r.productId) ?? r.skuCode}</div>
              <div className="text-xs font-mono text-slate-500">{r.skuCode}</div>
            </div>
          ),
        },
        {
          title: 'Cần',
          dataIndex: 'requestedQuantity',
          align: 'right',
          width: 90,
          render: (v: number) => formatNumber(v),
        },
        {
          title: 'Giữ được',
          dataIndex: 'availableQuantity',
          align: 'right',
          width: 90,
          render: (v: number) => formatNumber(v),
        },
        {
          title: 'Thiếu',
          dataIndex: 'shortageQuantity',
          align: 'right',
          width: 90,
          render: (v: number) => <strong className="text-red-600">{formatNumber(v)}</strong>,
        },
      ]}
    />
  )
}

interface ShortageNoticeProps extends ShortageTableProps {
  /** Thông báo của máy chủ */
  message: string
}

/** Nội dung hộp thoại thiếu hàng: thông báo của máy chủ và bảng thiếu hàng. */
export function StockShortageNotice({ message, shortages, productName }: ShortageNoticeProps) {
  return (
    <div className="space-y-2">
      <div className="text-sm text-slate-700">{message}</div>
      <StockShortageTable shortages={shortages} productName={productName} />
      <div className="text-xs text-slate-500">
        Tồn ở lô bị khóa, lô hết hạn và ô đang kiểm kê không được giữ chỗ nên có thể thiếu dù tồn khả dụng trên màn hình
        đủ. Nhập thêm hàng hoặc giảm số lượng rồi xác nhận lại.
      </div>
    </div>
  )
}

interface FrozenListProps {
  locations: FrozenLocation[]
  productName?: ProductNameLookup
}

/** Danh sách ô đang đóng băng kiểm kê: mã ô, đợt kiểm kê và sản phẩm liên quan. */
export function FrozenLocationList({ locations, productName }: FrozenListProps) {
  return (
    <ul className="m-0 mt-1 pl-5 list-disc text-sm">
      {locations.map((l, i) => {
        const product = productName?.(l.productId)
        return (
          <li key={`${l.binLocationId ?? l.binLocationCode}-${l.productId ?? ''}-${i}`}>
            Ô <strong className="font-mono">{l.binLocationCode || '?'}</strong> đang trong đợt kiểm kê{' '}
            <strong className="font-mono">{l.sessionCode || '?'}</strong>
            {product ? ` (mặt hàng ${product})` : ''}
          </li>
        )
      })}
    </ul>
  )
}
