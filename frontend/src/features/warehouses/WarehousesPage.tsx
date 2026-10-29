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

export default function WarehousesPage() {
  const { message } = App.useApp()

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

  // Nạp dữ liệu kho bãi và thẻ thống kê (WMS-05)
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

  // Nạp danh sách vị trí ô/kệ cho kho đang chọn (WMS-06)
  const loadLocations = useCallback(async (warehouseId?: number) => {
    const targetId = warehouseId || selectedWarehouseId
    if (!targetId) return

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
  }, [selectedWarehouseId, message])

  useEffect(() => {
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
  }, [message])

  useEffect(() => {
    if (!selectedWarehouseId) return
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
  }, [selectedWarehouseId, message])

  // Handlers cho Kho hàng (WMS-05)
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

  // Handlers cho Vị trí Ô/Kệ & Mã vạch (WMS-06)
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

  return (
    <div className="space-y-6">
      {/* Tiêu đề trang và nút làm mới */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">
            Quản Lý Đa Kho & Sơ Đồ Vị Trí Ô/Kệ (WMS-05 & WMS-06)
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Thiết kế danh mục đa kho, giám sát dung tích sức chứa, cấu trúc vị trí 4 cấp và in tem mã vạch giá kệ.
          </p>
        </div>

        <Button
          icon={<ReloadOutlined />}
          onClick={() => {
            loadWarehouses()
            loadSummary()
            if (selectedWarehouseId) loadLocations(selectedWarehouseId)
          }}
          loading={loadingWarehouses || loadingSummary}
        >
          Làm mới dữ liệu
        </Button>
      </div>

      {/* Thẻ tóm tắt thông số kho (WMS-05.2) */}
      <WarehouseMetricsCards summary={summary} loading={loadingSummary} />

      {/* Tabs chuyển đổi giữa Danh sách Kho và Sơ đồ Vị trí (WMS-05.3 & WMS-06.3) */}
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'warehouses' | 'locations')}
        items={[
          {
            key: 'warehouses',
            label: (
              <span className="flex items-center gap-1.5 font-medium px-1">
                <ShopOutlined /> Danh mục Đa Kho & Năng lực Chứa (WMS-05)
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
          {
            key: 'locations',
            label: (
              <span className="flex items-center gap-1.5 font-medium px-1">
                <ApartmentOutlined /> Sơ đồ Vị trí Ô/Kệ & In Tem Mã Vạch (WMS-06)
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
          },
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
        warehouses={warehouses}
        initialWarehouseId={selectedWarehouseId}
        loading={submittingLocation}
        onClose={() => setIsBulkModalOpen(false)}
        onSubmit={handleBulkCreateBins}
      />

      <SingleBinModal
        open={isSingleBinModalOpen}
        warehouses={warehouses}
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
