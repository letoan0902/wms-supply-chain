import {
  AppstoreOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  InboxOutlined,
  LockOutlined,
  PlusOutlined,
  PrinterOutlined,
  TableOutlined,
} from '@ant-design/icons'
import {
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import type { BinLocation, Warehouse } from '../types'

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

  // Nhóm theo Kệ (Rack) để vẽ ma trận sơ đồ trực quan (WMS-06.3)
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
      title: 'Mã vị trí (Bin Code)',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => (
        <span className="font-mono font-bold text-gray-800 bg-gray-100 px-2 py-0.5 rounded border border-gray-300">
          {code}
        </span>
      ),
    },
    {
      title: 'Khu (Zone)',
      dataIndex: 'zone',
      key: 'zone',
      align: 'center',
      render: (zone: string) => <Tag color="blue">{zone}</Tag>,
    },
    {
      title: 'Kệ (Rack)',
      dataIndex: 'rack',
      key: 'rack',
      align: 'center',
    },
    {
      title: 'Ô (Bin)',
      dataIndex: 'bin',
      key: 'bin',
      align: 'center',
    },
    {
      title: 'Tải trọng tối đa',
      dataIndex: 'maxWeight',
      key: 'maxWeight',
      render: (weight?: number) => `${weight || 1000} kg`,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (status: string) => {
        if (status === 'OCCUPIED') return <Tag color="processing">Đang có hàng</Tag>
        if (status === 'MAINTENANCE') return <Tag color="default">Bảo trì/Khóa</Tag>
        return <Tag color="success">Trống</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Xem & In tem mã vạch (WMS-06.4)">
            <Button
              type="text"
              size="small"
              icon={<PrinterOutlined className="text-blue-600" />}
              onClick={() => onPrintBarcode(record)}
            />
          </Tooltip>
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
        </Space>
      ),
    },
  ]

  return (
    <Card variant="borderless" className="shadow-xs">
      {/* Thanh công cụ chọn kho và bộ lọc */}
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-5 pb-4 border-b border-gray-100">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-semibold text-gray-700">Chọn kho bãi:</span>
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
              <span className="text-xs text-gray-500">Khu vực:</span>
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
            className="w-32"
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

          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={onAddSingleLocation}
            size="small"
          >
            Thêm 1 ô
          </Button>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={onBulkCreateLocations}
            className="bg-blue-600"
            size="small"
          >
            Sinh hàng loạt (Bulk)
          </Button>
        </div>
      </div>

      {/* Chú thích màu sắc sơ đồ trực quan (Legend) */}
      <div className="flex items-center gap-6 mb-4 text-xs text-gray-600 bg-gray-50 p-2.5 rounded-lg border border-gray-100">
        <span className="font-semibold text-gray-800">Chú thích sơ đồ:</span>
        <span className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-emerald-100 border border-emerald-400 inline-block" />
          <span>Ô trống (Sẵn sàng xếp hàng)</span>
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-blue-100 border border-blue-400 inline-block" />
          <span>Đang có hàng tồn</span>
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-gray-200 border border-gray-400 inline-block" />
          <span>Tạm khóa / Bảo trì</span>
        </span>
        <span className="ml-auto text-gray-400">
          Kho: <strong>{currentWarehouse?.name}</strong> | Tổng: <strong>{filteredLocations.length}</strong> ô
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
              <a onClick={onBulkCreateLocations} className="text-blue-600 font-medium">
                Sinh tự động ngay
              </a>
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
        /* Sơ đồ trực quan dạng ma trận Kệ - Ô (WMS-06.3) */
        <div className="space-y-6">
          {groupedByRack.map(([rackTitle, binList]) => {
            const [zonePart, rackPart] = rackTitle.split('-')
            return (
              <div
                key={rackTitle}
                className="border border-gray-200 rounded-xl p-4 bg-white shadow-2xs"
              >
                <div className="flex items-center justify-between mb-3 border-b border-gray-100 pb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-gray-900 text-sm">
                      Khu vực {zonePart} — Kệ {rackPart}
                    </span>
                    <Badge count={binList.length} style={{ backgroundColor: '#108ee9' }} />
                  </div>
                  <span className="text-xs text-gray-400 font-mono">
                    {currentWarehouse?.code}-{rackTitle}
                  </span>
                </div>

                <Row gutter={[10, 10]}>
                  {binList.map((loc) => {
                    const isOccupied = loc.status === 'OCCUPIED'
                    const isMaintenance = loc.status === 'MAINTENANCE' || !loc.active

                    let bgClass = 'bg-emerald-50/70 border-emerald-300 hover:border-emerald-500'
                    let badgeColor = 'text-emerald-700'
                    if (isOccupied) {
                      bgClass = 'bg-blue-50/70 border-blue-300 hover:border-blue-500'
                      badgeColor = 'text-blue-700'
                    } else if (isMaintenance) {
                      bgClass = 'bg-gray-100 border-gray-300 hover:border-gray-400'
                      badgeColor = 'text-gray-500'
                    }

                    return (
                      <Col xs={12} sm={8} md={6} lg={4} xl={3} key={loc.id}>
                        <div
                          className={`border rounded-lg p-2.5 transition-all text-center relative group cursor-pointer ${bgClass}`}
                        >
                          {/* Mã ô */}
                          <div className={`font-mono font-bold text-xs ${badgeColor}`}>
                            {loc.bin}
                          </div>
                          <div className="text-[10px] text-gray-500 truncate mt-0.5" title={loc.code}>
                            {loc.code}
                          </div>

                          {/* Trạng thái tóm tắt */}
                          <div className="mt-1 flex items-center justify-center gap-1 text-[11px]">
                            {isOccupied ? (
                              <span className="text-blue-600 font-semibold flex items-center gap-0.5">
                                <InboxOutlined /> Có hàng
                              </span>
                            ) : isMaintenance ? (
                              <span className="text-gray-500 flex items-center gap-0.5">
                                <LockOutlined /> Khóa
                              </span>
                            ) : (
                              <span className="text-emerald-600 flex items-center gap-0.5">
                                <CheckCircleOutlined /> Trống
                              </span>
                            )}
                          </div>

                          {/* Nút in tem nhanh khi hover */}
                          <div className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <Tooltip title="In tem mã vạch">
                              <Button
                                type="primary"
                                shape="circle"
                                size="small"
                                icon={<PrinterOutlined className="text-[11px]" />}
                                onClick={(e) => {
                                  e.stopPropagation()
                                  onPrintBarcode(loc)
                                }}
                                className="bg-blue-600 w-5 h-5 min-w-5"
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
    </Card>
  )
}

