import {
  AppstoreOutlined,
  DeleteOutlined,
  PlusOutlined,
  PrinterOutlined,
  TableOutlined,
} from '@ant-design/icons'
import {
  Button,
  Col,
  Empty,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import type { BinLocation, Warehouse } from '../types'
import { useCan } from '../../auth/useCan'

interface Props {
  warehouses: Warehouse[]
  selectedWarehouseId: number | undefined
  zones: string[]
  locations: BinLocation[]
  loading: boolean
  onSelectWarehouse: (id: number) => void
  onAddSingleLocation: () => void
  onBulkCreateLocations: () => void
  onPrintBarcode: (location: BinLocation) => void
  onDeleteLocation: (location: BinLocation) => void
}

export default function BinManagementView({
  warehouses,
  selectedWarehouseId,
  zones,
  locations,
  loading,
  onSelectWarehouse,
  onAddSingleLocation,
  onBulkCreateLocations,
  onPrintBarcode,
  onDeleteLocation,
}: Props) {
  // Tạo, xóa vị trí: ADMIN hoặc Quản lý kho được gán kho đang xem
  const canWriteLocations = useCan()('location.write', selectedWarehouseId)
  const [selectedZone, setSelectedZone] = useState<string>('ALL')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [viewMode, setViewMode] = useState<'grid' | 'table'>('grid')

  const currentWarehouse = warehouses.find((w) => w.id === selectedWarehouseId)

  // Lọc theo zone và status
  const filteredLocations = useMemo(() => {
    return locations.filter((loc) => {
      const matchZone = selectedZone === 'ALL' || loc.zone === selectedZone
      const matchStatus = statusFilter === 'ALL' || loc.status === statusFilter
      return matchZone && matchStatus
    })
  }, [locations, selectedZone, statusFilter])

  // Nhóm theo Kệ (Rack) để vẽ ma trận sơ đồ trực quan
  const groupedByRack = useMemo(() => {
    const map = new Map<string, BinLocation[]>()
    for (const loc of filteredLocations) {
      const rackKey = `${loc.zone}-${loc.rack}`
      if (!map.has(rackKey)) {
        map.set(rackKey, [])
      }
      map.get(rackKey)!.push(loc)
    }
    // Sắp xếp các ô trong kệ theo bin asc
    map.forEach((binList) => {
      binList.sort((a, b) => a.bin.localeCompare(b.bin))
    })
    return Array.from(map.entries())
  }, [filteredLocations])

  const columns: ColumnsType<BinLocation> = [
    {
      title: 'Mã vị trí',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => (
        <span className="text-xs font-medium text-slate-800 bg-slate-100 px-2 py-0.5 rounded border border-slate-200/60 tabular-nums">
          {code}
        </span>
      ),
    },
    {
      title: 'Khu (Zone)',
      dataIndex: 'zone',
      key: 'zone',
      align: 'center',
      render: (zone: string) => (
        <span className="text-xs font-medium text-slate-700 bg-slate-100 px-1.5 py-0.5 rounded">
          {zone}
        </span>
      ),
    },
    {
      title: 'Kệ (Rack)',
      dataIndex: 'rack',
      key: 'rack',
      align: 'center',
      render: (rack: string) => (
        <span className="text-xs text-slate-700">{rack}</span>
      ),
    },
    {
      title: 'Ô (Bin)',
      dataIndex: 'bin',
      key: 'bin',
      align: 'center',
      render: (bin: string) => (
        <span className="text-xs font-medium text-slate-900">{bin}</span>
      ),
    },
    {
      title: 'Tải trọng tối đa',
      dataIndex: 'maxWeight',
      key: 'maxWeight',
      align: 'right',
      render: (weight?: number) => (
        <span className="text-xs text-slate-700 tabular-nums">
          {(weight || 1000).toLocaleString('vi-VN')} kg
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (status: string) => {
        if (status === 'OCCUPIED') {
          return (
            <span className="inline-flex items-center gap-1.5 text-xs text-slate-700">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
              Đang có hàng
            </span>
          )
        }
        if (status === 'MAINTENANCE') {
          return (
            <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
              <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
              Bảo trì/Khóa
            </span>
          )
        }
        return (
          <span className="inline-flex items-center gap-1.5 text-xs text-slate-700">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            Trống
          </span>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      align: 'center',
      render: (_, record) => (
        <Space size={2}>
          <Tooltip title="In tem mã vạch">
            <Button
              type="text"
              size="small"
              icon={<PrinterOutlined className="text-slate-600" />}
              onClick={() => onPrintBarcode(record)}
            />
          </Tooltip>
          {canWriteLocations && (
          <Tooltip title="Xóa vị trí">
            <Popconfirm
              title="Xác nhận xóa vị trí ô/kệ này?"
              description="Chỉ được phép xóa khi vị trí không chứa hàng."
              onConfirm={() => onDeleteLocation(record)}
              okText="Xóa"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
            >
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="bg-white border border-slate-200/80 rounded-lg p-5">
      {/* Thanh công cụ chọn kho và bộ lọc */}
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-5 pb-4 border-b border-slate-100">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-slate-700">Kho bãi:</span>
          <Select
            value={selectedWarehouseId}
            onChange={onSelectWarehouse}
            className="w-64"
            options={warehouses.map((w) => ({
              label: `${w.code} - ${w.name}`,
              value: w.id,
            }))}
          />

          {zones.length > 0 && (
            <div className="flex items-center gap-1.5 ml-2">
              <span className="text-xs text-slate-500">Khu vực:</span>
              <Radio.Group
                value={selectedZone}
                onChange={(e) => setSelectedZone(e.target.value)}
                size="small"
              >
                <Radio.Button value="ALL">Tất cả ({locations.length})</Radio.Button>
                {zones.map((z) => (
                  <Radio.Button key={z} value={z}>
                    Khu {z}
                  </Radio.Button>
                ))}
              </Radio.Group>
            </div>
          )}

          <Select
            value={statusFilter}
            onChange={setStatusFilter}
            size="small"
            className="w-36"
            options={[
              { value: 'ALL', label: 'Mọi trạng thái' },
              { value: 'EMPTY', label: 'Ô trống' },
              { value: 'OCCUPIED', label: 'Đang có hàng' },
              { value: 'MAINTENANCE', label: 'Đang bảo trì' },
            ]}
          />
        </div>

        <div className="flex items-center gap-2 w-full lg:w-auto justify-end">
          <Radio.Group
            value={viewMode}
            onChange={(e) => setViewMode(e.target.value)}
            size="small"
          >
            <Radio.Button value="grid">
              <AppstoreOutlined /> Sơ đồ lưới
            </Radio.Button>
            <Radio.Button value="table">
              <TableOutlined /> Danh sách
            </Radio.Button>
          </Radio.Group>

          {canWriteLocations && (
            <Button
              icon={<PlusOutlined />}
              onClick={onAddSingleLocation}
              size="small"
            >
              Thêm 1 ô
            </Button>
          )}

          {canWriteLocations && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={onBulkCreateLocations}
              size="small"
            >
              Sinh hàng loạt
            </Button>
          )}
        </div>
      </div>

      {/* Chú thích màu sắc sơ đồ trực quan (Legend) */}
      <div className="flex flex-wrap items-center gap-6 mb-4 text-xs text-slate-600 bg-slate-50/80 px-3 py-2 rounded-md border border-slate-200/60">
        <span className="font-medium text-slate-700">Chú thích trạng thái:</span>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block" />
          <span>Ô trống (Sẵn sàng)</span>
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-blue-500 inline-block" />
          <span>Đang có hàng tồn</span>
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-slate-400 inline-block" />
          <span>Tạm khóa / Bảo trì</span>
        </span>
        <span className="ml-auto text-slate-500">
          Kho: <strong className="text-slate-700 font-semibold">{currentWarehouse?.name}</strong> | Tổng: <strong className="text-slate-700 font-semibold">{filteredLocations.length}</strong> vị trí
        </span>
      </div>

      {loading ? (
        <div className="py-16 flex justify-center items-center">
          <Spin tip="Đang tải sơ đồ vị trí ô/kệ..." />
        </div>
      ) : filteredLocations.length === 0 ? (
        <Empty
          description={
            <span>
              Chưa có vị trí ô/kệ nào trong kho này.{' '}
              {canWriteLocations && (
                <a onClick={onBulkCreateLocations} className="text-blue-600 font-medium" data-write-action>
                  Sinh tự động ngay
                </a>
              )}
            </span>
          }
          className="my-12"
        />
      ) : viewMode === 'table' ? (
        <Table
          columns={columns}
          dataSource={filteredLocations}
          rowKey="id"
          pagination={{ pageSize: 15, showSizeChanger: true }}
        />
      ) : (
        /* Sơ đồ trực quan dạng ma trận Kệ - Ô */
        <div className="space-y-4">
          {groupedByRack.map(([rackTitle, binList]) => {
            const [zonePart, rackPart] = rackTitle.split('-')
            return (
              <div
                key={rackTitle}
                className="border border-slate-200/80 rounded-lg p-4 bg-slate-50/30"
              >
                <div className="flex items-center justify-between mb-3 border-b border-slate-200/60 pb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-800 text-sm">
                      Khu {zonePart} — Kệ {rackPart}
                    </span>
                    <span className="text-[11px] font-medium text-slate-600 bg-slate-100 border border-slate-200 px-1.5 py-0.5 rounded tabular-nums">
                      {binList.length} ô
                    </span>
                  </div>
                  <span className="text-xs text-slate-400">
                    {currentWarehouse?.code}-{rackTitle}
                  </span>
                </div>

                <Row gutter={[8, 8]}>
                  {binList.map((loc) => {
                    const isOccupied = loc.status === 'OCCUPIED'
                    const isMaintenance = loc.status === 'MAINTENANCE' || !loc.active

                    let cardBorder = 'border-slate-200 hover:border-emerald-500'
                    let statusIndicator = (
                      <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 font-medium">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                        Trống
                      </span>
                    )

                    if (isOccupied) {
                      cardBorder = 'border-slate-200 hover:border-blue-500'
                      statusIndicator = (
                        <span className="inline-flex items-center gap-1 text-[11px] text-blue-600 font-medium">
                          <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                          Có hàng
                        </span>
                      )
                    } else if (isMaintenance) {
                      cardBorder = 'border-slate-200 bg-slate-100/60 opacity-75'
                      statusIndicator = (
                        <span className="inline-flex items-center gap-1 text-[11px] text-slate-400">
                          <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                          Khóa
                        </span>
                      )
                    }

                    return (
                      <Col xs={12} sm={8} md={6} lg={4} xl={3} key={loc.id}>
                        <div
                          className={`bg-white border rounded-md p-2.5 transition-all text-center relative group cursor-pointer shadow-2xs ${cardBorder}`}
                        >
                          {/* Mã ô */}
                          <div className="font-semibold text-xs text-slate-900 tabular-nums">
                            {loc.bin}
                          </div>
                          <div className="text-[10px] text-slate-400 truncate mt-0.5" title={loc.code}>
                            {loc.code}
                          </div>

                          {/* Trạng thái tóm tắt */}
                          <div className="mt-1 flex items-center justify-center">
                            {statusIndicator}
                          </div>

                          {/* Nút in tem nhanh khi hover */}
                          <div className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <Tooltip title="In tem mã vạch">
                              <Button
                                type="text"
                                shape="circle"
                                size="small"
                                icon={<PrinterOutlined className="text-slate-500 text-xs" />}
                                onClick={(e) => {
                                  e.stopPropagation()
                                  onPrintBarcode(loc)
                                }}
                                className="w-5 h-5 min-w-5 flex items-center justify-center bg-slate-100 hover:bg-slate-200"
                              />
                            </Tooltip>
                          </div>
                        </div>
                      </Col>
                    )
                  })}
                </Row>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

