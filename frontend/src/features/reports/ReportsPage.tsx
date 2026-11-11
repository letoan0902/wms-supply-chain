import { Alert, Button, Tabs, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getStoredUser } from '../../utils/storage'
import { fetchCategories } from '../products/productApi'
import type { Category } from '../products/types'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import InOutBalanceTab from './components/InOutBalanceTab'
import ReportErrorAlert from './components/ReportErrorAlert'
import ReportForbidden from './components/ReportForbidden'
import StockCardTab from './components/StockCardTab'
import { getReportAccess } from './reportAccess'

const { Title, Text } = Typography

type TabKey = 'in-out-balance' | 'stock-card'

/** Màn hình báo cáo kho: Xuất - Nhập - Tồn theo kỳ và thẻ kho chi tiết, số liệu lấy từ sổ kho của inventory-service. */
export default function ReportsPage() {
  const access = getReportAccess(getStoredUser())
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab: TabKey = searchParams.get('tab') === 'stock-card' ? 'stock-card' : 'in-out-balance'

  // Danh sách kho: backend đã tự giới hạn theo các kho được gán với Quản lý kho, Nhân viên kho
  const [warehouseState, setWarehouseState] = useState<{ items: Warehouse[]; loading: boolean; error: unknown }>({
    items: [],
    loading: true,
    error: null,
  })
  const [categories, setCategories] = useState<Category[]>([])

  useEffect(() => {
    if (!access.canView) return
    let ignore = false
    fetchWarehouses()
      .then((items) => {
        if (!ignore) setWarehouseState({ items, loading: false, error: null })
      })
      .catch((error: unknown) => {
        if (!ignore) setWarehouseState({ items: [], loading: false, error })
      })
    fetchCategories().then((items) => {
      if (!ignore) setCategories(items)
    })
    return () => {
      ignore = true
    }
  }, [access.canView])

  if (!access.canView) {
    return <ReportForbidden />
  }

  return (
    <div className="flex flex-col gap-4 w-full">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Title level={4} className="!mb-0">
            Báo cáo kho
          </Title>
          <Text type="secondary" className="text-xs">
            Tồn đầu kỳ, nhập, xuất, tồn cuối kỳ và thẻ kho chi tiết, tính từ sổ kho
            {access.allWarehouses ? ' của mọi kho trong doanh nghiệp' : ' của các kho bạn được phân công'}.
          </Text>
        </div>
        <Button onClick={() => navigate('/reports/lot-trace')}>Truy vết theo số lô</Button>
      </div>

      {warehouseState.error ? (
        <ReportErrorAlert error={warehouseState.error} fallback="Không tải được danh sách kho." />
      ) : null}
      {!warehouseState.loading && !warehouseState.error && warehouseState.items.length === 0 && (
        <Alert type="info" showIcon message="Chưa có kho nào trong phạm vi được xem." />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setSearchParams(key === 'in-out-balance' ? {} : { tab: key }, { replace: true })}
        items={[
          {
            key: 'in-out-balance',
            label: 'Xuất - Nhập - Tồn',
            children: (
              <InOutBalanceTab
                warehouses={warehouseState.items}
                warehousesLoading={warehouseState.loading}
                categories={categories}
                allWarehouses={access.allWarehouses}
              />
            ),
          },
          {
            key: 'stock-card',
            label: 'Thẻ kho',
            children: <StockCardTab warehouses={warehouseState.items} warehousesLoading={warehouseState.loading} />,
          },
        ]}
      />
    </div>
  )
}
