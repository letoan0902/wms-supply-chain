import {
  ApartmentOutlined,
  ReloadOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { App, Button, Tabs } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import BarcodePrintModal from './components/BarcodePrintModal'
import BinManagementView from './components/BinManagementView'
import BulkCreateBinModal from './components/BulkCreateBinModal'
import SingleBinModal from './components/SingleBinModal'
import WarehouseFormModal from './components/WarehouseFormModal'
import WarehouseMetricsCards from './components/WarehouseMetricsCards'
import WarehouseTable from './components/WarehouseTable'
import type {
  BinBarcodeData,
  BinLocation,
  BulkCreateBinLocationsPayload,
  CreateBinLocationPayload,
  CreateWarehousePayload,
  UpdateWarehousePayload,
  Warehouse,
  WarehouseSummary,
} from './types'
import * as warehouseApi from './warehouseApi'
import { getApiErrorMessage } from '../../services/api'
import ForbiddenResult from '../../components/ForbiddenResult'
import { useCan } from '../auth/useCan'

export default function WarehousesPage() {
  const { message } = App.useApp()
  const can = useCan()
  // Vai trò ngoài ADMIN, ACCOUNTANT chưa được gán kho nào bị backend trả 403 khi đọc danh sách kho
  const canReadWarehouses = can('warehouse.read')
  // Vị trí ô kệ: chỉ ADMIN, Quản lý kho, Nhân viên kho (mua hàng, bán hàng, kế toán không được đọc)
  const canReadLocations = can('location.read')

  const [activeTab, setActiveTab] = useState<'warehouses' | 'locations'>('warehouses')
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [summary, setSummary] = useState<WarehouseSummary | null>(null)
  const [loadingWarehouses, setLoadingWarehouses] = useState<boolean>(false)
  const [loadingSummary, setLoadingSummary] = useState<boolean>(false)

  // Bin Locations State
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | undefined>(undefined)
  const [locations, setLocations] = useState<BinLocation[]>([])
  const [zones, setZones] = useState<string[]>([])
  const [loadingLocations, setLoadingLocations] = useState<boolean>(false)

  // Modals
  const [isWarehouseModalOpen, setIsWarehouseModalOpen] = useState(false)
  const [editingWarehouse, setEditingWarehouse] = useState<Warehouse | null>(null)
  const [submittingWarehouse, setSubmittingWarehouse] = useState(false)

  const [isBulkModalOpen, setIsBulkModalOpen] = useState(false)
  const [isSingleBinModalOpen, setIsSingleBinModalOpen] = useState(false)
  const [submittingLocation, setSubmittingLocation] = useState(false)

  const [isBarcodeModalOpen, setIsBarcodeModalOpen] = useState(false)
  const [barcodeData, setBarcodeData] = useState<BinBarcodeData | null>(null)
  const [loadingBarcode, setLoadingBarcode] = useState(false)

  // Nạp dữ liệu kho bãi và thẻ thống kê
  const loadWarehouses = useCallback(async (keyword?: string, active?: boolean) => {
    setLoadingWarehouses(true)
    try {
      const data = await warehouseApi.fetchWarehouses({ keyword, active })
      setWarehouses(data)
      if (data.length > 0 && !selectedWarehouseId) {
        setSelectedWarehouseId(data[0].id)
      }
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải danh sách kho'))
    } finally {
      setLoadingWarehouses(false)
    }
  }, [selectedWarehouseId, message])

  const loadSummary = useCallback(async () => {
    setLoadingSummary(true)
    try {
      const data = await warehouseApi.fetchWarehouseSummary()
      setSummary(data)
    } catch (err) {
      console.error(err)
    } finally {
      setLoadingSummary(false)
    }
  }, [])

  // Nạp danh sách vị trí ô/kệ cho kho đang chọn
  const loadLocations = useCallback(async (warehouseId?: number) => {
    const targetId = warehouseId || selectedWarehouseId
    if (!targetId || !canReadLocations) return

    setLoadingLocations(true)
    try {
      const [locList, zoneList] = await Promise.all([
        warehouseApi.fetchLocations({ warehouseId: targetId }),
        warehouseApi.fetchZones(targetId),
      ])
      setLocations(locList)
      setZones(zoneList)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải sơ đồ vị trí'))
    } finally {
      setLoadingLocations(false)
    }
  }, [selectedWarehouseId, canReadLocations, message])

  useEffect(() => {
    if (!canReadWarehouses) return
    let ignore = false
    const fetchData = async () => {
      setLoadingWarehouses(true)
      setLoadingSummary(true)
      try {
        const [wList, sData] = await Promise.all([
          warehouseApi.fetchWarehouses(),
          warehouseApi.fetchWarehouseSummary().catch(() => null),
        ])
        if (!ignore) {
          setWarehouses(wList)
          setSummary(sData)
          if (wList.length > 0) {
            setSelectedWarehouseId(wList[0].id)
          }
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách kho'))
        }
      } finally {
        if (!ignore) {
          setLoadingWarehouses(false)
          setLoadingSummary(false)
        }
      }
    }
    void fetchData()
    return () => {
      ignore = true
    }
  }, [canReadWarehouses, message])

  useEffect(() => {
    if (!selectedWarehouseId || !canReadLocations) return
    let ignore = false
    const fetchLocs = async () => {
      setLoadingLocations(true)
      try {
        const [locList, zoneList] = await Promise.all([
          warehouseApi.fetchLocations({ warehouseId: selectedWarehouseId }),
          warehouseApi.fetchZones(selectedWarehouseId),
        ])
        if (!ignore) {
          setLocations(locList)
          setZones(zoneList)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải sơ đồ vị trí'))
        }
      } finally {
        if (!ignore) {
          setLoadingLocations(false)
        }
      }
    }
    void fetchLocs()
    return () => {
      ignore = true
    }
  }, [selectedWarehouseId, canReadLocations, message])

  // Handlers cho Kho hàng
  const handleOpenCreateWarehouse = () => {
    setEditingWarehouse(null)
    setIsWarehouseModalOpen(true)
  }

  const handleOpenEditWarehouse = (warehouse: Warehouse) => {
    setEditingWarehouse(warehouse)
    setIsWarehouseModalOpen(true)
  }

  const handleSubmitWarehouse = async (
    values: CreateWarehousePayload | UpdateWarehousePayload
  ) => {
    setSubmittingWarehouse(true)
    try {
      if (editingWarehouse) {
        await warehouseApi.updateWarehouse(editingWarehouse.id, values as UpdateWarehousePayload)
        message.success('Cập nhật kho hàng thành công')
      } else {
        await warehouseApi.createWarehouse(values as CreateWarehousePayload)
        message.success('Tạo kho hàng mới thành công')
      }
      setIsWarehouseModalOpen(false)
      loadWarehouses()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi lưu kho hàng'))
    } finally {
      setSubmittingWarehouse(false)
    }
  }

  const handleToggleWarehouseStatus = async (warehouse: Warehouse) => {
    try {
      await warehouseApi.toggleWarehouseStatus(warehouse.id, !warehouse.active)
      message.success(
        warehouse.active ? `Đã tạm dừng hoạt động kho ${warehouse.name}` : `Đã kích hoạt kho ${warehouse.name}`
      )
      loadWarehouses()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể đổi trạng thái kho'))
    }
  }

  const handleDeleteWarehouse = async (warehouse: Warehouse) => {
    try {
      await warehouseApi.deleteWarehouse(warehouse.id)
      message.success(`Đã xóa kho ${warehouse.name}`)
      loadWarehouses()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể xóa kho hàng'))
    }
  }

  const handleViewBins = (warehouse: Warehouse) => {
    setSelectedWarehouseId(warehouse.id)
    setActiveTab('locations')
  }

  // Handlers cho Vị trí Ô/Kệ & Mã vạch
  const handleCreateSingleBin = async (payload: CreateBinLocationPayload) => {
    setSubmittingLocation(true)
    try {
      await warehouseApi.createLocation(payload)
      message.success('Đã thêm vị trí ô/kệ mới')
      setIsSingleBinModalOpen(false)
      loadLocations()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể thêm vị trí'))
    } finally {
      setSubmittingLocation(false)
    }
  }

  const handleBulkCreateBins = async (payload: BulkCreateBinLocationsPayload) => {
    setSubmittingLocation(true)
    try {
      const created = await warehouseApi.bulkCreateLocations(payload)
      message.success(`Đã sinh tự động ${created.length} vị trí ô/kệ thành công`)
      setIsBulkModalOpen(false)
      loadLocations()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lỗi khi sinh dải vị trí tự động'))
    } finally {
      setSubmittingLocation(false)
    }
  }

  const handleDeleteLocation = async (location: BinLocation) => {
    try {
      await warehouseApi.deleteLocation(location.id)
      message.success(`Đã xóa vị trí ${location.code}`)
      loadLocations()
      loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể xóa vị trí ô/kệ'))
    }
  }

  const handlePrintBarcode = async (location: BinLocation) => {
    setLoadingBarcode(true)
    setIsBarcodeModalOpen(true)
    try {
      const data = await warehouseApi.fetchLocationBarcode(location.id)
      setBarcodeData(data)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải mã vạch vị trí'))
      setIsBarcodeModalOpen(false)
    } finally {
      setLoadingBarcode(false)
    }
  }

  if (!canReadWarehouses) {
    return (
      <ForbiddenResult description="Tài khoản chưa được gán kho nào nên không xem được danh mục kho." />
    )
  }

  // Kho mà người dùng được tạo, xóa vị trí ô kệ (hộp thoại tạo vị trí chỉ cho chọn các kho này)
  const writableLocationWarehouses = warehouses.filter((w) => can('location.write', w.id))

  return (
    <div className="space-y-6">
      {/* Tiêu đề trang và nút làm mới */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900 tracking-tight">
            Kho và vị trí
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Quản lý hệ thống kho bãi, theo dõi sức chứa định mức và sơ đồ vị trí lưu trữ.
          </p>
        </div>

        <Button
          icon={<ReloadOutlined className="text-slate-500" />}
          onClick={() => {
            loadWarehouses()
            loadSummary()
            if (selectedWarehouseId) loadLocations(selectedWarehouseId)
          }}
          loading={loadingWarehouses || loadingSummary}
          className="text-slate-700"
        >
          Làm mới dữ liệu
        </Button>
      </div>

      {/* Thẻ tóm tắt thông số kho */}
      <WarehouseMetricsCards summary={summary} loading={loadingSummary} />

      {/* Tabs chuyển đổi giữa Danh sách Kho và Sơ đồ Vị trí */}
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'warehouses' | 'locations')}
        items={[
          {
            key: 'warehouses',
            label: (
              <span className="flex items-center gap-1.5 font-medium px-1 text-slate-700">
                <ShopOutlined /> Danh mục kho
              </span>
            ),
            children: (
              <WarehouseTable
                warehouses={warehouses}
                loading={loadingWarehouses}
                onAddWarehouse={handleOpenCreateWarehouse}
                onEditWarehouse={handleOpenEditWarehouse}
                onToggleStatus={handleToggleWarehouseStatus}
                onDeleteWarehouse={handleDeleteWarehouse}
                onViewBins={handleViewBins}
                onSearch={loadWarehouses}
              />
            ),
          },
          ...(canReadLocations ? [{
            key: 'locations',
            label: (
              <span className="flex items-center gap-1.5 font-medium px-1 text-slate-700">
                <ApartmentOutlined /> Sơ đồ vị trí ô/kệ
              </span>
            ),
            children: (
              <BinManagementView
                warehouses={warehouses}
                selectedWarehouseId={selectedWarehouseId}
                zones={zones}
                locations={locations}
                loading={loadingLocations}
                onSelectWarehouse={(id) => setSelectedWarehouseId(id)}
                onAddSingleLocation={() => setIsSingleBinModalOpen(true)}
                onBulkCreateLocations={() => setIsBulkModalOpen(true)}
                onPrintBarcode={handlePrintBarcode}
                onDeleteLocation={handleDeleteLocation}
              />
            ),
          }] : []),
        ]}
      />

      {/* Các Modals nghiệp vụ */}
      <WarehouseFormModal
        open={isWarehouseModalOpen}
        warehouse={editingWarehouse}
        loading={submittingWarehouse}
        onClose={() => setIsWarehouseModalOpen(false)}
        onSubmit={handleSubmitWarehouse}
      />

      <BulkCreateBinModal
        open={isBulkModalOpen}
        warehouses={writableLocationWarehouses}
        initialWarehouseId={selectedWarehouseId}
        loading={submittingLocation}
        onClose={() => setIsBulkModalOpen(false)}
        onSubmit={handleBulkCreateBins}
      />

      <SingleBinModal
        open={isSingleBinModalOpen}
        warehouses={writableLocationWarehouses}
        initialWarehouseId={selectedWarehouseId}
        loading={submittingLocation}
        onClose={() => setIsSingleBinModalOpen(false)}
        onSubmit={handleCreateSingleBin}
      />

      <BarcodePrintModal
        open={isBarcodeModalOpen}
        barcodeData={barcodeData}
        loading={loadingBarcode}
        onClose={() => setIsBarcodeModalOpen(false)}
      />
    </div>
  )
}
