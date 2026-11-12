import { Alert, Button, Card, Empty, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import type { CanFn } from '../../auth/useCan'
import { formatDateTime, formatNumber } from '../../../utils/format'
import type { RecentStockTransaction, RecentStockTransactions } from '../types'
import type { DashboardRequest } from '../useDashboardRequest'

/** Tên loại chứng từ của sổ kho. */
const DOCUMENT_LABELS: Record<string, string> = {
  GOODS_RECEIPT: 'Phiếu nhập',
  SALES_ORDER: 'Đơn bán',
  STOCK_TRANSFER: 'Phiếu điều chuyển',
  STOCKTAKE: 'Phiếu điều chỉnh kiểm kê',
  OTHER: 'Chứng từ khác',
}

/**
 * Màn hình đang có của chứng từ, chỉ khi vai trò được mở màn hình đó. Phiếu nhập, đơn bán, phiếu điều chuyển chưa có
 * trang chi tiết riêng nên dẫn tới màn hình danh sách; phiếu điều chỉnh mở trang của đợt kiểm kê.
 */
function documentLink(row: RecentStockTransaction, can: CanFn): { to: string; title: string } | null {
  switch (row.documentType) {
    case 'GOODS_RECEIPT':
      return can('goodsReceipt.read') ? { to: '/purchases', title: 'Mở màn hình Mua hàng và nhập kho' } : null
    case 'SALES_ORDER':
      return can('salesOrder.read') ? { to: '/sales', title: 'Mở màn hình Bán hàng và xuất kho' } : null
    case 'STOCK_TRANSFER':
      return can('transfer.read') ? { to: '/transfers', title: 'Mở màn hình Điều chuyển' } : null
    case 'STOCKTAKE':
      return row.stocktakeId !== null && can('stocktake.read')
        ? { to: `/stocktakes/${row.stocktakeId}`, title: 'Mở đợt kiểm kê của phiếu điều chỉnh' }
        : null
    default:
      return null
  }
}

function warehouseLabel(name: string | null, code: string | null, id: number | null): string {
  if (name) return code ? `${name} (${code})` : name
  return id !== null ? `Kho #${id}` : '—'
}

interface Props {
  request: DashboardRequest<RecentStockTransactions>
  can: CanFn
  onRetry: () => void
}

/** Bảng các chứng từ kho gần nhất (nhập, xuất bán, điều chuyển, điều chỉnh) trong phạm vi kho và khoảng thời gian. */
export default function RecentTransactionsCard({ request, can, onRetry }: Props) {
  const { data, error, loading } = request

  const columns: ColumnsType<RecentStockTransaction> = [
    {
      title: 'Thời gian',
      key: 'occurredAt',
      width: 140,
      render: (_, r) => <span className="text-xs text-slate-600 whitespace-nowrap">{formatDateTime(r.occurredAt)}</span>,
    },
    {
      title: 'Giao dịch',
      key: 'type',
      render: (_, r) => {
        const link = documentLink(r, can)
        const number = r.documentNumber ?? (r.documentId !== null ? `#${r.documentId}` : '—')
        return (
          <div className="min-w-0">
            <Tag color={r.direction === 'IN' ? 'green' : 'orange'} className="text-xs m-0">
              {r.movementTypeLabel}
            </Tag>
            <div className="text-[11px] text-slate-500 mt-1">
              {DOCUMENT_LABELS[r.documentType] ?? 'Chứng từ'}{' '}
              {link ? (
                <Link to={link.to} title={link.title} className="font-medium">
                  {number}
                </Link>
              ) : (
                <span className="font-medium text-slate-700">{number}</span>
              )}
            </div>
          </div>
        )
      },
    },
    {
      title: 'Kho',
      key: 'warehouse',
      responsive: ['md'],
      render: (_, r) => (
        <div className="text-xs text-slate-600">
          <div>{warehouseLabel(r.warehouseName, r.warehouseCode, r.warehouseId)}</div>
          {r.counterpartWarehouseId !== null && (
            <div className="text-[11px] text-slate-500">
              {r.direction === 'OUT' ? 'Đến ' : 'Từ '}
              {warehouseLabel(r.counterpartWarehouseName, null, r.counterpartWarehouseId)}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Số lượng',
      key: 'quantity',
      align: 'right',
      render: (_, r) => (
        <div className="text-xs tabular-nums whitespace-nowrap">
          <div className={`font-semibold ${r.direction === 'IN' ? 'text-emerald-700' : 'text-orange-700'}`}>
            {r.direction === 'IN' ? '+' : '-'}
            {formatNumber(Number(r.quantity))}
          </div>
          <div className="text-[11px] text-slate-500">{formatNumber(r.productCount)} mặt hàng</div>
        </div>
      ),
    },
    {
      title: 'Người thực hiện',
      key: 'performedBy',
      responsive: ['lg'],
      render: (_, r) => <span className="text-xs text-slate-600">{r.performedBy ?? '—'}</span>,
    },
  ]

  return (
    <Card
      variant="borderless"
      className="shadow-none border border-slate-200/80 bg-white"
      title={
        <div className="py-1">
          <div className="font-semibold text-slate-900 text-sm">Giao dịch kho gần nhất</div>
          <div className="text-[11px] font-normal text-slate-500">
            Mỗi dòng là một chứng từ tại một kho; số lượng cộng gộp các mặt hàng và đơn vị tính của chứng từ
          </div>
        </div>
      }
    >
      {error ? (
        <Alert
          type="error"
          showIcon
          message={error}
          action={
            <Button size="small" onClick={onRetry}>
              Thử lại
            </Button>
          }
        />
      ) : (
        <Table<RecentStockTransaction>
          columns={columns}
          dataSource={data?.rows ?? []}
          loading={loading}
          // Cùng khóa gom của backend (loại chứng từ, ID, số chứng từ, loại biến động, kho) nên không trùng
          rowKey={(r) =>
            `${r.documentType}:${r.documentId ?? ''}:${r.documentNumber ?? ''}:${r.movementType}:${r.warehouseId}`
          }
          pagination={false}
          size="middle"
          locale={{ emptyText: <Empty description="Chưa có giao dịch kho trong khoảng thời gian này" /> }}
        />
      )}
    </Card>
  )
}
