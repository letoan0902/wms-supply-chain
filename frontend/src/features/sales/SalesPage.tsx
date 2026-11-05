import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShoppingOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'
import { formatCurrency } from '../../utils/format'
import * as customerApi from '../customers/customerApi'
import type { Customer } from '../customers/types'
import * as warehouseApi from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import SalesOrderForm from './components/SalesOrderForm'
import * as salesApi from './salesApi'
import { SALES_STATUS_CONFIG } from './statusConfig'
import type { OrderStatus, SalesOrder, SalesOrderSummary } from './types'

const { RangePicker } = DatePicker

export default function SalesPage() {
  const { message } = App.useApp()

  const [activeTab, setActiveTab] = useState<string>('list')
  const [formResetKey, setFormResetKey] = useState<number>(1)
  const [refreshKey, setRefreshKey] = useState<number>(0)

  // Danh sách đơn bán hàng
  const [orders, setOrders] = useState<SalesOrder[]>([])
  const [totalOrders, setTotalOrders] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(false)
  const [summary, setSummary] = useState<SalesOrderSummary | null>(null)

  // Bộ lọc
  const [keyword, setKeyword] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>(undefined)
  const [customerFilter, setCustomerFilter] = useState<number | undefined>(undefined)
  const [warehouseFilter, setWarehouseFilter] = useState<number | undefined>(undefined)
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Metadata lọc
  const [customers, setCustomers] = useState<Customer[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Tải danh mục khách hàng và kho
  useEffect(() => {
    let ignore = false
    const loadFilters = async () => {
      try {
        const [cRes, wRes] = await Promise.all([
          customerApi.fetchCustomers({ active: true, size: 100 }),
          warehouseApi.fetchWarehouses({ active: true }),
        ])
        if (!ignore) {
          setCustomers(cRes.content || [])
          setWarehouses(wRes || [])
        }
      } catch (err) {
        console.error('Lỗi tải danh mục lọc', err)
      }
    }
    void loadFilters()
    return () => {
      ignore = true
    }
  }, [])

  // Tải danh sách đơn bán và báo cáo summary
  useEffect(() => {
    let ignore = false
    const fetchOrders = async () => {
      setLoading(true)
      try {
        const [orderRes, sumRes] = await Promise.all([
          salesApi.fetchSalesOrders({
            keyword: keyword.trim() || undefined,
            customerId: customerFilter,
            warehouseId: warehouseFilter,
            status: statusFilter,
            fromOrderDate: dateRange?.[0] ? dateRange[0].format('YYYY-MM-DD') : undefined,
            toOrderDate: dateRange?.[1] ? dateRange[1].format('YYYY-MM-DD') : undefined,
            page,
            size: pageSize,
          }),
          salesApi.fetchSalesOrderSummary(),
        ])

        if (!ignore) {
          setOrders(orderRes.content || [])
          setTotalOrders(orderRes.totalElements || 0)
          setSummary(sumRes)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Lỗi khi tải danh sách đơn bán hàng'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    void fetchOrders()
    return () => {
      ignore = true
    }
  }, [keyword, statusFilter, customerFilter, warehouseFilter, dateRange, page, pageSize, refreshKey, message])

  // Cột hiển thị bảng
  const columns: ColumnsType<SalesOrder> = [
    {
      title: 'Mã SO',
      dataIndex: 'soNumber',
      key: 'soNumber',
      width: 160,
      render: (val: string, record) => (
        <div>
          <span className="font-semibold text-blue-600">{val}</span>
          {record.warehouseName && (
            <div className="text-xs text-gray-500 truncate" title={record.warehouseName}>
              Kho: {record.warehouseName}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 220,
      render: (val: string, record) => (
        <div>
          <div className="font-medium text-slate-800">{val || 'Khách vãng lai'}</div>
          {record.customerCode && (
            <Tag color="geekblue" className="text-xs m-0">
              {record.customerCode}
            </Tag>
          )}
        </div>
      ),
    },
    {
      title: 'Ngày đặt',
      dataIndex: 'orderDate',
      key: 'orderDate',
      width: 110,
      render: (val: string) => dayjs(val).format('DD/MM/YYYY'),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 170,
      render: (st: OrderStatus, record) => {
        const cfg = SALES_STATUS_CONFIG[st] || { label: st, color: 'default', icon: null }
        return (
          <div>
            <Tag color={cfg.color} icon={cfg.icon}>
              {cfg.label}
            </Tag>
            {st === 'BACKORDER' && record.backorderReason && (
              <div className="text-[11px] text-amber-700 mt-1 line-clamp-1" title={record.backorderReason}>
                {record.backorderReason}
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Tổng tiền hàng',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 160,
      align: 'right',
      render: (val: number) => (
        <span className="font-bold text-slate-800">{formatCurrency(val)}</span>
      ),
    },
    {
      title: 'Số mặt hàng',
      key: 'itemCount',
      width: 110,
      align: 'center',
      render: (_val, record) => (
        <Badge count={record.items?.length || 0} overflowCount={999} style={{ backgroundColor: '#108ee9' }} />
      ),
    },
    {
      title: 'Thời gian tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (val?: string) => (val ? dayjs(val).format('DD/MM/YYYY HH:mm') : '—'),
    },
  ]

  return (
    <div className="p-4 space-y-4">
      {/* Metrics Summary Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6} lg={4}>
          <Card size="small" className="shadow-sm border-slate-200">
            <Statistic
              title="Tổng số đơn bán"
              value={summary?.totalOrders ?? totalOrders}
              prefix={<ShoppingOutlined className="text-blue-500 mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-sm border-slate-200">
            <Statistic
              title="Đơn bán chờ xử lý (Pending)"
              value={summary?.pendingSaleOrders ?? 0}
              valueStyle={{ color: '#1677ff' }}
              prefix={<ClockCircleOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-sm border-slate-200">
            <Statistic
              title="Thiếu hàng (Backorder)"
              value={summary?.backorders ?? 0}
              valueStyle={{ color: '#faad14' }}
              prefix={<WarningOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-sm border-slate-200">
            <Statistic
              title="Đã xác nhận (Confirmed)"
              value={summary?.confirmedOrders ?? 0}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined className="mr-1" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6} lg={5}>
          <Card size="small" className="shadow-sm border-slate-200">
            <Statistic
              title="Doanh thu xác nhận"
              value={summary?.totalRevenue ?? 0}
              formatter={(v) => formatCurrency(Number(v))}
              valueStyle={{ color: '#0958d9', fontSize: '1.15rem' }}
              prefix={<DollarOutlined className="mr-1" />}
            />
          </Card>
        </Col>
      </Row>

      {/* Tabs Layout */}
      <Card className="shadow-sm border-slate-200">
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key)}
          destroyInactiveTabPane
          items={[
            {
              key: 'list',
              label: (
                <Space>
                  <ShoppingOutlined />
                  <span>Danh sách Đơn bán hàng</span>
                  <Badge count={totalOrders} overflowCount={999} style={{ backgroundColor: '#52c41a' }} />
                </Space>
              ),
              children: (
                <div className="space-y-4 pt-2">
                  {/* Filters Header */}
                  <Row gutter={[12, 12]} align="middle">
                    <Col xs={24} sm={12} md={6}>
                      <Input
                        placeholder="Tìm theo mã SO, tên khách, ghi chú..."
                        prefix={<SearchOutlined className="text-gray-400" />}
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        allowClear
                      />
                    </Col>
                    <Col xs={12} sm={6} md={4}>
                      <Select
                        placeholder="Trạng thái"
                        allowClear
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        className="w-full"
                        options={Object.entries(SALES_STATUS_CONFIG).map(([k, v]) => ({
                          value: k,
                          label: v.label,
                        }))}
                      />
                    </Col>
                    <Col xs={12} sm={6} md={5}>
                      <Select
                        placeholder="Khách hàng"
                        allowClear
                        showSearch
                        value={customerFilter}
                        onChange={(val) => setCustomerFilter(val)}
                        filterOption={(input, option) =>
                          (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                        }
                        className="w-full"
                        options={customers.map((c) => ({
                          value: c.id,
                          label: `${c.code} - ${c.name}`,
                        }))}
                      />
                    </Col>
                    <Col xs={12} sm={6} md={4}>
                      <Select
                        placeholder="Kho xuất"
                        allowClear
                        value={warehouseFilter}
                        onChange={(val) => setWarehouseFilter(val)}
                        className="w-full"
                        options={warehouses.map((w) => ({
                          value: w.id,
                          label: w.name,
                        }))}
                      />
                    </Col>
                    <Col xs={24} sm={12} md={5}>
                      <RangePicker
                        className="w-full"
                        format="DD/MM/YYYY"
                        value={dateRange}
                        onChange={(dates) => setDateRange(dates)}
                      />
                    </Col>
                  </Row>

                  <div className="flex justify-between items-center pt-2">
                    <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((k) => k + 1)}>
                      Làm mới
                    </Button>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => {
                        setFormResetKey((prev) => prev + 1)
                        setActiveTab('create')
                      }}
                      className="bg-blue-600 hover:bg-blue-700"
                    >
                      Lập đơn bán hàng mới
                    </Button>
                  </div>

                  {/* Table */}
                  <Table
                    rowKey="id"
                    columns={columns}
                    dataSource={orders}
                    loading={loading}
                    pagination={{
                      current: page + 1,
                      pageSize,
                      total: totalOrders,
                      showSizeChanger: true,
                      pageSizeOptions: ['10', '20', '50'],
                      onChange: (p, ps) => {
                        setPage(p - 1)
                        setPageSize(ps)
                      },
                    }}
                    scroll={{ x: 1000 }}
                  />
                </div>
              ),
            },
            {
              key: 'create',
              label: (
                <Space>
                  <FileDoneOutlined />
                  <span>Lập Đơn Bán Hàng (Form SO)</span>
                </Space>
              ),
              children: (
                <div className="pt-2">
                  <SalesOrderForm
                    key={formResetKey}
                    onSuccess={() => {
                      setFormResetKey((prev) => prev + 1)
                      setActiveTab('list')
                      setRefreshKey((k) => k + 1)
                    }}
                    onCancel={() => {
                      setActiveTab('list')
                    }}
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
