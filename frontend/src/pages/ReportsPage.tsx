import {
  FileExcelOutlined,
  LockOutlined,
  SearchOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Breadcrumb,
  Button,
  Card,
  Input,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as XLSX from 'xlsx'
import { fetchWarehouses } from '../features/warehouses/warehouseApi'
import type { Warehouse } from '../features/warehouses/types'
import { formatCurrency, formatNumber } from '../utils/format'
import { getStoredUser } from '../utils/storage'
import {
  inventoryReportSampleData,
  type InventoryReportRow,
} from './dashboardSampleData'

const { Title, Text } = Typography

export default function ReportsPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const currentUser = getStoredUser()
  const roles = currentUser?.roles || []

  // Phân quyền theo SRS Mục 2.5.1 & 2.5.2
  const isAdmin = roles.includes('ADMIN')
  const isAccountant = roles.includes('ACCOUNTANT')
  const isWarehouseManager = roles.includes('WAREHOUSE_MANAGER')
  const isWarehouseStaff =
    roles.includes('WAREHOUSE_STAFF') && !isAdmin && !isAccountant && !isWarehouseManager

  const isGlobalScope = isAdmin || isAccountant
  const canViewFinancials = isAdmin || isAccountant
  const canExportExcel = isAdmin || isAccountant

  // State
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | 'all'>('all')
  const [keyword, setKeyword] = useState('')
  const [loadingWarehouses, setLoadingWarehouses] = useState(true)

  // Tải danh mục Kho từ backend
  useEffect(() => {
    let mounted = true
    async function load() {
      try {
        const list = await fetchWarehouses({ active: true })
        if (!mounted) return
        setWarehouses(list)

        // Phân quyền dữ liệu theo phạm vi kho (Warehouse Scope - SRS 2.5.2):
        if (!isGlobalScope) {
          const userWarehouseIds = currentUser?.warehouseIds || []
          if (userWarehouseIds.length > 0) {
            setSelectedWarehouseId(userWarehouseIds[0])
          } else if (list.length > 0) {
            setSelectedWarehouseId(list[0].id)
          }
        }
      } catch {
        // Fallback
      } finally {
        if (mounted) setLoadingWarehouses(false)
      }
    }
    void load()
    return () => {
      mounted = false
    }
  }, [isGlobalScope, currentUser?.warehouseIds])

  // Lọc danh sách kho mà người dùng có quyền chọn
  const allowedWarehouses = useMemo(() => {
    if (isGlobalScope) return warehouses
    const userWhIds = currentUser?.warehouseIds || []
    if (userWhIds.length === 0) return warehouses.slice(0, 1)
    return warehouses.filter((w) => userWhIds.includes(w.id))
  }, [warehouses, isGlobalScope, currentUser?.warehouseIds])

  // Dữ liệu báo cáo lọc theo Kho và Từ khóa
  const filteredData = useMemo(() => {
    return inventoryReportSampleData.filter((row) => {
      const matchWarehouse =
        selectedWarehouseId === 'all' || row.warehouseId === selectedWarehouseId
      const matchKeyword =
        !keyword ||
        row.sku.toLowerCase().includes(keyword.toLowerCase()) ||
        row.productName.toLowerCase().includes(keyword.toLowerCase())
      return matchWarehouse && matchKeyword
    })
  }, [selectedWarehouseId, keyword])

  // 1. Phân quyền truy cập chức năng (SRS 2.5.1):
  // Nhân viên kho (WAREHOUSE_STAFF) không có quyền xem Báo cáo Nhập-Xuất-Tồn kho toàn diện (chỉ xem cảnh báo).
  if (isWarehouseStaff) {
    return (
      <div className="bg-white p-8 rounded-lg shadow-sm border border-slate-200">
        <Result
          status="403"
          title="403 - Giới hạn quyền truy cập"
          subTitle="Tài khoản của bạn không có quyền truy cập Sổ Báo cáo Nhập - Xuất - Tồn kho. Vui lòng liên hệ Quản lý kho hoặc Kế toán nếu bạn cần hỗ trợ."
          extra={
            <Button
              type="primary"
              onClick={() => navigate('/')}
              className="bg-blue-600 font-medium"
            >
              Về Trang Tổng quan
            </Button>
          }
        />
      </div>
    )
  }

  // Xuất file Excel báo cáo Nhập - Xuất - Tồn
  const handleExportExcel = () => {
    if (!canExportExcel) {
      message.warning('Chỉ Quản trị viên và Kế toán mới có quyền xuất báo cáo Excel.')
      return
    }

    const exportRows = filteredData.map((r, index) => ({
      STT: index + 1,
      'Mã SKU': r.sku,
      'Tên sản phẩm': r.productName,
      'Đơn vị tính': r.unit,
      'Kho bãi': r.warehouseName,
      'Tồn đầu kỳ': r.openingQty,
      'Nhập trong kỳ': r.inwardQty,
      'Xuất trong kỳ': r.outwardQty,
      'Tồn cuối kỳ': r.closingQty,
      'Ngưỡng an toàn': r.safetyStock,
      'Đơn giá vốn (VND)': canViewFinancials ? r.unitPrice : '***',
      'Tổng giá trị tồn (VND)': canViewFinancials ? r.totalValue : '***',
    }))

    const worksheet = XLSX.utils.json_to_sheet(exportRows)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Nhap_Xuat_Ton')
    const fileName = `WMS_Bao_Cao_Nhap_Xuat_Ton_${new Date().toISOString().slice(0, 10)}.xlsx`
    XLSX.writeFile(workbook, fileName)
    message.success(`Đã xuất báo cáo Nhập - Xuất - Tồn thành công: ${fileName}`)
  }

  // Cấu hình cột hiển thị
  const columns: ColumnsType<InventoryReportRow> = [
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      key: 'sku',
      width: 110,
      render: (sku: string) => <span className="text-xs font-semibold text-blue-700">{sku}</span>,
    },
    {
      title: 'Tên hàng hóa',
      dataIndex: 'productName',
      key: 'productName',
      render: (name: string, r) => (
        <div>
          <div className="font-medium text-gray-900 text-xs">{name}</div>
          <div className="text-[11px] text-gray-500">ĐVT: {r.unit} • {r.warehouseName}</div>
        </div>
      ),
    },
    {
      title: 'Tồn đầu kỳ',
      dataIndex: 'openingQty',
      key: 'openingQty',
      align: 'right',
      render: (val: number) => <span className="text-xs tabular-nums">{formatNumber(val)}</span>,
    },
    {
      title: 'Nhập trong kỳ',
      dataIndex: 'inwardQty',
      key: 'inwardQty',
      align: 'right',
      render: (val: number) => <span className="text-xs text-green-600 font-medium tabular-nums">+{formatNumber(val)}</span>,
    },
    {
      title: 'Xuất trong kỳ',
      dataIndex: 'outwardQty',
      key: 'outwardQty',
      align: 'right',
      render: (val: number) => <span className="text-xs text-red-600 font-medium tabular-nums">-{formatNumber(val)}</span>,
    },
    {
      title: 'Tồn cuối kỳ',
      dataIndex: 'closingQty',
      key: 'closingQty',
      align: 'right',
      render: (val: number, r) => {
        const isBelowSafety = val <= r.safetyStock
        return (
          <span className={`text-xs font-semibold tabular-nums ${isBelowSafety ? 'text-amber-600' : 'text-slate-900'}`}>
            {formatNumber(val)}
          </span>
        )
      },
    },
    ...(canViewFinancials
      ? [
          {
            title: 'Đơn giá vốn',
            dataIndex: 'unitPrice',
            key: 'unitPrice',
            align: 'right' as const,
            render: (val: number) => <span className="text-xs text-slate-600 tabular-nums">{formatCurrency(val)}</span>,
          },
          {
            title: 'Tổng giá trị tồn',
            dataIndex: 'totalValue',
            key: 'totalValue',
            align: 'right' as const,
            render: (val: number) => <span className="text-xs font-semibold text-slate-900 tabular-nums">{formatCurrency(val)}</span>,
          },
        ]
      : []),
    {
      title: 'Trạng thái',
      key: 'status',
      align: 'center',
      width: 120,
      render: (_, r) => {
        if (r.status === 'LOW') {
          return <Tag color="error" className="text-xs">Tồn nguy cấp</Tag>
        }
        if (r.status === 'WARNING') {
          return <Tag color="warning" className="text-xs">Cận date</Tag>
        }
        return <Tag color="success" className="text-xs">Ổn định</Tag>
      },
    },
  ]

  return (
    <div className="flex flex-col gap-5 w-full">
      {/* Header Breadcrumb */}
      <div>
        <Breadcrumb
          items={[
            { title: 'Trang chủ' },
            { title: 'Báo cáo & Thống kê' },
            { title: 'Báo cáo Nhập - Xuất - Tồn kho' },
          ]}
          className="text-xs mb-1"
        />
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <Title level={4} className="!mb-0 font-semibold text-slate-900">
              Sổ Báo Cáo Nhập - Xuất - Tồn Kho
            </Title>
            <Text type="secondary" className="text-xs">
              Theo dõi biến động hàng tồn, đối soát thẻ kho và xác định giá trị tồn kho theo thời gian thực
            </Text>
          </div>

          {/* Action Toolbar */}
          <Space wrap>
            {canExportExcel ? (
              <Button
                type="primary"
                icon={<FileExcelOutlined />}
                onClick={handleExportExcel}
                className="bg-emerald-600 hover:!bg-emerald-500 font-medium text-xs"
              >
                Xuất Excel (.xlsx)
              </Button>
            ) : (
              <Tooltip title="Chỉ Quản trị viên và Kế toán mới có quyền xuất báo cáo Excel.">
                <Button icon={<LockOutlined />} disabled className="text-xs">
                  Xuất Excel
                </Button>
              </Tooltip>
            )}
          </Space>
        </div>
      </div>

      {/* Banner thông báo phạm vi phân quyền */}
      {!isGlobalScope && (
        <Alert
          type="warning"
          showIcon
          message="Phạm vi chi nhánh phụ trách"
          description="Dữ liệu báo cáo được giới hạn theo các kho bạn được phân công quản lý."
          className="text-xs border-amber-200/80 bg-amber-50/40 rounded-lg"
        />
      )}

      {/* Bộ lọc tìm kiếm & Kho */}
      <Card variant="borderless" className="shadow-xs border border-slate-200/80">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <Space wrap size="middle">
            <Input
              placeholder="Tìm theo SKU hoặc tên hàng..."
              prefix={<SearchOutlined className="text-gray-400" />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              allowClear
              className="w-64"
            />

            <div className="flex items-center gap-1.5">
              <ShopOutlined className="text-gray-400 text-xs" />
              <Text className="text-xs text-gray-500">Kho:</Text>
              <Select
                className="w-56"
                value={selectedWarehouseId}
                loading={loadingWarehouses}
                onChange={(val) => setSelectedWarehouseId(val)}
                options={[
                  ...(isGlobalScope
                    ? [{ label: 'Tất cả các kho (Toàn hệ thống)', value: 'all' as const }]
                    : []),
                  ...allowedWarehouses.map((w) => ({
                    label: `${w.name} (${w.code})`,
                    value: w.id,
                  })),
                ]}
              />
            </div>
          </Space>

          <Text type="secondary" className="text-xs">
            Hiển thị {filteredData.length} mặt hàng
          </Text>
        </div>
      </Card>

      {/* Bảng Dữ liệu Báo cáo */}
      <Card variant="borderless" className="shadow-xs border border-slate-200/80 p-0">
        <Table<InventoryReportRow>
          columns={columns}
          dataSource={filteredData}
          rowKey="key"
          size="middle"
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Card>
    </div>
  )
}
