import {
  ClearOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Col,
  Input,
  Modal,
  Row,
  Select,
  Space,
} from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../services/api'
import { fetchWarehouses } from '../warehouses/warehouseApi'
import type { Warehouse } from '../warehouses/types'
import CreateTransferModal from './components/CreateTransferModal'
import TransferDetailModal from './components/TransferDetailModal'
import TransferMetricsCards from './components/TransferMetricsCards'
import TransferTable from './components/TransferTable'
import { TRANSFER_STATUS_CONFIG } from './statusConfig'
import * as transferApi from './transferApi'
import type {
  CreateStockTransferPayload,
  StockTransfer,
  StockTransferFilterParams,
  StockTransferStatus,
  UpdateStockTransferPayload,
} from './types'

export default function TransfersPage() {
  const { message } = App.useApp()

  // State danh sách và phân trang
  const [transfers, setTransfers] = useState<StockTransfer[]>([])
  const [loading, setLoading] = useState<boolean>(false)
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  // Bộ lọc và phân trang
  const [keyword, setKeyword] = useState<string>('')
  const [selectedStatus, setSelectedStatus] = useState<StockTransferStatus | undefined>(undefined)
  const [sourceWarehouseId, setSourceWarehouseId] = useState<number | undefined>(undefined)
  const [destinationWarehouseId, setDestinationWarehouseId] = useState<number | undefined>(undefined)
  const [page, setPage] = useState<number>(1)
  const [pageSize, setPageSize] = useState<number>(10)
  const [totalCount, setTotalCount] = useState<number>(0)
  const [refreshKey, setRefreshKey] = useState<number>(0)

  // State các Modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState<boolean>(false)
  const [editingTransfer, setEditingTransfer] = useState<StockTransfer | null>(null)
  const [submittingTransfer, setSubmittingTransfer] = useState<boolean>(false)

  const [detailModalOpen, setDetailModalOpen] = useState<boolean>(false)
  const [selectedTransfer, setSelectedTransfer] = useState<StockTransfer | null>(null)

  // Nạp danh sách kho bãi phục vụ lọc và chọn trong form
  useEffect(() => {
    let ignore = false
    fetchWarehouses({ active: true })
      .then((data) => {
        if (!ignore) {
          setWarehouses(data)
        }
      })
      .catch((err) => console.error('Lỗi tải danh sách kho:', err))
    return () => {
      ignore = true
    }
  }, [])

  // Nạp danh sách lệnh điều chuyển
  useEffect(() => {
    let ignore = false
    const fetchTransfers = async () => {
      setLoading(true)
      try {
        const params: StockTransferFilterParams = {
          page: page - 1,
          size: pageSize,
          keyword: keyword.trim() || undefined,
          status: selectedStatus,
          sourceWarehouseId,
          destinationWarehouseId,
        }

        const data = await transferApi.fetchTransfers(params)
        if (!ignore) {
          setTransfers(data.content || [])
          setTotalCount(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách lệnh điều chuyển'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    void fetchTransfers()
    return () => {
      ignore = true
    }
  }, [
    page,
    pageSize,
    keyword,
    selectedStatus,
    sourceWarehouseId,
    destinationWarehouseId,
    refreshKey,
    message,
  ])

  // Làm mới dữ liệu
  const reloadData = () => {
    setRefreshKey((k) => k + 1)
  }

  // Xử lý tìm kiếm từ ô Input
  const handleSearch = () => {
    setPage(1)
    reloadData()
  }

  // Đặt lại toàn bộ bộ lọc
  const handleResetFilters = () => {
    setKeyword('')
    setSelectedStatus(undefined)
    setSourceWarehouseId(undefined)
    setDestinationWarehouseId(undefined)
    setPage(1)
    reloadData()
  }

  // Chuyển trang
  const handleChangePage = (newPage: number, newPageSize: number) => {
    setPage(newPage)
    setPageSize(newPageSize)
  }

  // Mở modal tạo mới
  const handleOpenCreate = () => {
    setEditingTransfer(null)
    setIsCreateModalOpen(true)
  }

  // Mở modal chỉnh sửa
  const handleOpenEdit = (transfer: StockTransfer) => {
    setEditingTransfer(transfer)
    setIsCreateModalOpen(true)
  }

  // Mở modal xem chi tiết
  const handleViewDetail = async (transfer: StockTransfer) => {
    try {
      const detailed = await transferApi.fetchTransferById(transfer.id)
      setSelectedTransfer(detailed)
      setDetailModalOpen(true)
    } catch {
      setSelectedTransfer(transfer)
      setDetailModalOpen(true)
    }
  }

  // Tạo mới lệnh điều chuyển
  const handleSubmitCreate = async (payload: CreateStockTransferPayload) => {
    setSubmittingTransfer(true)
    try {
      await transferApi.createTransfer(payload)
      message.success('Tạo lệnh điều chuyển thành công!')
      setIsCreateModalOpen(false)
      setPage(1)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tạo lệnh điều chuyển'))
    } finally {
      setSubmittingTransfer(false)
    }
  }

  // Cập nhật lệnh điều chuyển
  const handleSubmitUpdate = async (id: number, payload: UpdateStockTransferPayload) => {
    setSubmittingTransfer(true)
    try {
      await transferApi.updateTransfer(id, payload)
      message.success('Cập nhật thông tin lệnh điều chuyển thành công!')
      setIsCreateModalOpen(false)
      setEditingTransfer(null)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể cập nhật lệnh điều chuyển'))
    } finally {
      setSubmittingTransfer(false)
    }
  }

  // Duyệt xuất kho chuyển đi đường (dispatch)
  const handleDispatch = async (transfer: StockTransfer) => {
    try {
      await transferApi.dispatchTransfer(transfer.id)
      message.success(`Đã duyệt xuất kho cho lệnh ${transfer.transferNumber}. Hàng đang được vận chuyển!`)
      reloadData()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể duyệt xuất kho'))
    }
  }

  // Hủy lệnh điều chuyển
  const handleCancelTransfer = (transfer: StockTransfer) => {
    let reasonInput = ''
    Modal.confirm({
      title: 'Hủy lệnh điều chuyển',
      content: (
        <div className="mt-2">
          <p className="text-gray-600 mb-2">
            Bạn có chắc chắn muốn hủy lệnh điều chuyển <strong>{transfer.transferNumber}</strong>?
          </p>
          <Input.TextArea
            rows={3}
            placeholder="Nhập lý do hủy lệnh điều chuyển (tùy chọn)..."
            onChange={(e) => {
              reasonInput = e.target.value
            }}
          />
        </div>
      ),
      okText: 'Xác nhận hủy',
      cancelText: 'Đóng',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await transferApi.cancelTransfer(transfer.id, reasonInput)
          message.success(`Đã hủy lệnh điều chuyển ${transfer.transferNumber}`)
          reloadData()
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không thể hủy lệnh điều chuyển'))
        }
      },
    })
  }

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6">
      {/* Tiêu đề trang & Nút hành động */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">
            Điều chuyển hàng hóa nội bộ
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Lập kế hoạch, theo dõi xuất kho đi đường và tiếp nhận điều chuyển giữa các kho
          </p>
        </div>
        <Space size="middle">
          <Button
            icon={<ReloadOutlined />}
            onClick={reloadData}
            loading={loading}
          >
            Làm mới
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenCreate}
            className="shadow-sm"
          >
            Lập lệnh điều chuyển
          </Button>
        </Space>
      </div>

      {/* Thẻ số liệu thống kê */}
      <TransferMetricsCards
        transfers={transfers}
        totalCount={totalCount}
        loading={loading && transfers.length === 0}
      />

      {/* Bộ lọc và tìm kiếm */}
      <Card variant="borderless" className="shadow-xs bg-white rounded-lg">
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} md={8}>
            <Input
              placeholder="Tìm kiếm theo mã lệnh hoặc ghi chú..."
              prefix={<SearchOutlined className="text-gray-400" />}
              allowClear
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={handleSearch}
            />
          </Col>

          <Col xs={12} sm={8} md={4}>
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: '100%' }}
              value={selectedStatus}
              onChange={(val) => {
                setSelectedStatus(val)
                setPage(1)
              }}
              options={Object.entries(TRANSFER_STATUS_CONFIG).map(([statusKey, cfg]) => ({
                value: statusKey,
                label: cfg.label,
              }))}
            />
          </Col>

          <Col xs={12} sm={8} md={5}>
            <Select
              allowClear
              placeholder="Kho xuất nguồn"
              style={{ width: '100%' }}
              value={sourceWarehouseId}
              onChange={(val) => {
                setSourceWarehouseId(val)
                setPage(1)
              }}
              options={warehouses.map((w) => ({
                value: w.id,
                label: `${w.name} (${w.code})`,
              }))}
            />
          </Col>

          <Col xs={12} sm={8} md={5}>
            <Select
              allowClear
              placeholder="Kho nhập đích"
              style={{ width: '100%' }}
              value={destinationWarehouseId}
              onChange={(val) => {
                setDestinationWarehouseId(val)
                setPage(1)
              }}
              options={warehouses.map((w) => ({
                value: w.id,
                label: `${w.name} (${w.code})`,
              }))}
            />
          </Col>

          <Col xs={12} sm={24} md={2} className="flex justify-end">
            <Button
              icon={<ClearOutlined />}
              onClick={handleResetFilters}
              title="Đặt lại bộ lọc"
            >
              Xóa lọc
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Bảng danh sách lệnh điều chuyển */}
      <TransferTable
        transfers={transfers}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total: totalCount,
        }}
        onChangePage={handleChangePage}
        onViewDetail={handleViewDetail}
        onEdit={handleOpenEdit}
        onDispatch={handleDispatch}
        onCancel={handleCancelTransfer}
      />

      {/* Modal Lập / Chỉnh sửa lệnh điều chuyển */}
      {isCreateModalOpen && (
        <CreateTransferModal
          key={editingTransfer ? `edit-${editingTransfer.id}` : 'create'}
          open={isCreateModalOpen}
          editingTransfer={editingTransfer}
          warehouses={warehouses}
          submitting={submittingTransfer}
          onClose={() => {
            setIsCreateModalOpen(false)
            setEditingTransfer(null)
          }}
          onSubmitCreate={handleSubmitCreate}
          onSubmitUpdate={handleSubmitUpdate}
        />
      )}

      {/* Modal Xem chi tiết lệnh điều chuyển */}
      <TransferDetailModal
        open={detailModalOpen}
        transfer={selectedTransfer}
        onClose={() => {
          setDetailModalOpen(false)
          setSelectedTransfer(null)
        }}
      />
    </div>
  )
}
