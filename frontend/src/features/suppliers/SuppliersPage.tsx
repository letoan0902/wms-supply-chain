import {
  FileExcelOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { App, Button, Space, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import { getApiErrorMessage } from '../../services/api'
import RecordTransactionModal from './components/RecordTransactionModal'
import SupplierFormModal from './components/SupplierFormModal'
import SupplierMetricsCards from './components/SupplierMetricsCards'
import SupplierTable from './components/SupplierTable'
import SupplierTransactionsModal from './components/SupplierTransactionsModal'
import * as supplierApi from './supplierApi'
import type {
  CreateSupplierPayload,
  RecordSupplierTransactionPayload,
  Supplier,
  SupplierDebtSummary,
  UpdateSupplierPayload,
} from './types'

const { Title, Text } = Typography

export default function SuppliersPage() {
  const { message } = App.useApp()

  // State danh sách
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Filters
  const [keyword, setKeyword] = useState<string | undefined>(undefined)
  const [active, setActive] = useState<boolean | undefined>(undefined)

  // Metrics Summary
  const [summary, setSummary] = useState<SupplierDebtSummary | null>(null)
  const [summaryLoading, setSummaryLoading] = useState(false)

  // Modals state
  const [isFormModalOpen, setIsFormModalOpen] = useState(false)
  const [editingSupplier, setEditingSupplier] = useState<Supplier | null>(null)
  const [formSubmitting, setFormSubmitting] = useState(false)

  const [isTransactionsModalOpen, setIsTransactionsModalOpen] = useState(false)
  const [selectedSupplierForTransactions, setSelectedSupplierForTransactions] =
    useState<Supplier | null>(null)

  const [isRecordModalOpen, setIsRecordModalOpen] = useState(false)
  const [selectedSupplierForRecord, setSelectedSupplierForRecord] = useState<Supplier | null>(null)
  const [recordSubmitting, setRecordSubmitting] = useState(false)

  // Tải danh sách nhà cung cấp
  const loadSuppliers = useCallback(async () => {
    setLoading(true)
    try {
      const data = await supplierApi.fetchSuppliers({
        keyword,
        active,
        page,
        size: pageSize,
      })
      setSuppliers(data.content || [])
      setTotal(data.totalElements || 0)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải danh sách nhà cung cấp'))
    } finally {
      setLoading(false)
    }
  }, [keyword, active, page, pageSize, message])

  // Tải tóm tắt công nợ tổng thể
  const loadSummary = useCallback(async () => {
    setSummaryLoading(true)
    try {
      const data = await supplierApi.fetchSupplierDebtSummary()
      setSummary(data)
    } catch (err) {
      console.error('Không thể tải tổng quan công nợ', err)
    } finally {
      setSummaryLoading(false)
    }
  }, [])

  useEffect(() => {
    let ignore = false
    const fetchList = async () => {
      setLoading(true)
      try {
        const data = await supplierApi.fetchSuppliers({
          keyword,
          active,
          page,
          size: pageSize,
        })
        if (!ignore) {
          setSuppliers(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách nhà cung cấp'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }
    void fetchList()
    return () => {
      ignore = true
    }
  }, [keyword, active, page, pageSize, message])

  useEffect(() => {
    let ignore = false
    const fetchSummary = async () => {
      setSummaryLoading(true)
      try {
        const data = await supplierApi.fetchSupplierDebtSummary()
        if (!ignore) {
          setSummary(data)
        }
      } catch (err) {
        console.error('Không thể tải tổng quan công nợ', err)
      } finally {
        if (!ignore) {
          setSummaryLoading(false)
        }
      }
    }
    void fetchSummary()
    return () => {
      ignore = true
    }
  }, [])

  // Tìm kiếm
  const handleSearch = (newKeyword?: string, newActive?: boolean) => {
    setKeyword(newKeyword)
    setActive(newActive)
    setPage(0)
  }

  // Thêm mới / Cập nhật NCC
  const handleOpenCreateModal = () => {
    setEditingSupplier(null)
    setIsFormModalOpen(true)
  }

  const handleOpenEditModal = (supplier: Supplier) => {
    setEditingSupplier(supplier)
    setIsFormModalOpen(true)
  }

  const handleFormSubmit = async (values: CreateSupplierPayload | UpdateSupplierPayload) => {
    setFormSubmitting(true)
    try {
      if (editingSupplier) {
        await supplierApi.updateSupplier(editingSupplier.id, values as UpdateSupplierPayload)
        message.success(`Cập nhật nhà cung cấp "${editingSupplier.code}" thành công!`)
      } else {
        const created = await supplierApi.createSupplier(values as CreateSupplierPayload)
        message.success(`Thêm mới nhà cung cấp "${created.code}" thành công!`)
      }
      setIsFormModalOpen(false)
      setEditingSupplier(null)
      void loadSuppliers()
      void loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Có lỗi xảy ra khi lưu thông tin nhà cung cấp'))
    } finally {
      setFormSubmitting(false)
    }
  }

  // Xóa mềm NCC
  const handleDeleteSupplier = async (id: number) => {
    try {
      await supplierApi.deleteSupplier(id)
      message.success('Đã chuyển nhà cung cấp sang trạng thái tạm dừng!')
      void loadSuppliers()
      void loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể ngưng hoạt động nhà cung cấp'))
    }
  }

  // Xem sổ nợ & giao dịch
  const handleOpenTransactionsModal = (supplier: Supplier) => {
    setSelectedSupplierForTransactions(supplier)
    setIsTransactionsModalOpen(true)
  }

  // Ghi nhận giao dịch công nợ
  const handleOpenRecordModal = (supplier: Supplier) => {
    setSelectedSupplierForRecord(supplier)
    setIsRecordModalOpen(true)
  }

  const handleRecordSubmit = async (payload: RecordSupplierTransactionPayload) => {
    if (!selectedSupplierForRecord) return
    setRecordSubmitting(true)
    try {
      await supplierApi.recordSupplierTransaction(selectedSupplierForRecord.id, payload)
      message.success(
        `Đã ghi nhận giao dịch thành công cho nhà cung cấp "${selectedSupplierForRecord.code}"!`
      )
      setIsRecordModalOpen(false)
      setSelectedSupplierForRecord(null)

      // Cập nhật lại số liệu nếu đang mở modal sổ nợ
      if (selectedSupplierForTransactions?.id === selectedSupplierForRecord.id) {
        const updated = await supplierApi.fetchSupplierById(selectedSupplierForRecord.id)
        setSelectedSupplierForTransactions(updated)
      }

      void loadSuppliers()
      void loadSummary()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể ghi nhận giao dịch công nợ'))
    } finally {
      setRecordSubmitting(false)
    }
  }

  // Xuất file Excel danh sách NCC & công nợ
  const handleExportExcel = () => {
    if (suppliers.length === 0) {
      message.warning('Không có dữ liệu nhà cung cấp để xuất Excel')
      return
    }

    const exportData = suppliers.map((s, index) => ({
      STT: index + 1,
      'Mã nhà cung cấp': s.code,
      'Tên nhà cung cấp': s.name,
      'Mã số thuế': s.taxCode || '',
      'Người liên hệ': s.contactName || '',
      'Số điện thoại': s.phone || '',
      Email: s.email || '',
      'Địa chỉ': s.address || '',
      'Số tài khoản': s.bankAccount || '',
      'Ngân hàng': s.bankName || '',
      'Điều khoản thanh toán': s.paymentTerms || 'NET_30',
      'Hạn mức công nợ (VNĐ)': s.creditLimit,
      'Công nợ hiện tại (VNĐ)': s.currentDebt,
      'Tình trạng nợ': s.isOverCreditLimit ? 'VƯỢT HẠN MỨC' : 'Trong hạn mức',
      'Trạng thái': s.active ? 'Đang hoạt động' : 'Tạm dừng',
    }))

    const worksheet = XLSX.utils.json_to_sheet(exportData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Nha_Cung_Cap')
    XLSX.writeFile(
      workbook,
      `WMS_Danh_sach_Nha_cung_cap_${new Date().toISOString().slice(0, 10)}.xlsx`
    )
    message.success('Đã xuất file Excel danh sách nhà cung cấp thành công!')
  }

  return (
    <div className="space-y-6">
      {/* Header trang */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1 flex items-center gap-2 text-slate-800">
            <ShopOutlined className="text-blue-600" />
            <span>Quản lý Nhà cung cấp & Công nợ</span>
          </Title>
          <Text type="secondary" className="text-sm">
            Hồ sơ đối tác cung ứng, điều khoản thanh toán và theo dõi công nợ phải trả (FR09 - WMS-08.3)
          </Text>
        </div>

        <Space wrap>
          <Button
            icon={<FileExcelOutlined />}
            onClick={handleExportExcel}
            className="hover:border-green-600 hover:text-green-600"
          >
            Xuất Excel
          </Button>

          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              void loadSuppliers()
              void loadSummary()
            }}
            loading={loading || summaryLoading}
          >
            Làm mới
          </Button>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenCreateModal}
          >
            Thêm nhà cung cấp
          </Button>
        </Space>
      </div>

      {/* 4 Cards Thống kê Tổng quan công nợ */}
      <SupplierMetricsCards summary={summary} loading={summaryLoading} />

      {/* Bảng danh sách & Bộ lọc */}
      <SupplierTable
        suppliers={suppliers}
        total={total}
        loading={loading}
        page={page}
        pageSize={pageSize}
        onPageChange={(p, ps) => {
          setPage(p)
          setPageSize(ps)
        }}
        onSearch={handleSearch}
        onEdit={handleOpenEditModal}
        onDelete={handleDeleteSupplier}
        onViewTransactions={handleOpenTransactionsModal}
        onRecordTransaction={handleOpenRecordModal}
      />

      {/* Modal Thêm / Sửa Nhà cung cấp */}
      <SupplierFormModal
        open={isFormModalOpen}
        editingSupplier={editingSupplier}
        submitting={formSubmitting}
        onCancel={() => {
          setIsFormModalOpen(false)
          setEditingSupplier(null)
        }}
        onSubmit={handleFormSubmit}
      />

      {/* Modal Sổ nợ & Lịch sử giao dịch */}
      <SupplierTransactionsModal
        open={isTransactionsModalOpen}
        supplier={selectedSupplierForTransactions}
        onClose={() => {
          setIsTransactionsModalOpen(false)
          setSelectedSupplierForTransactions(null)
        }}
        onOpenRecordModal={(sup) => {
          handleOpenRecordModal(sup)
        }}
      />

      {/* Modal Ghi nhận phát sinh giao dịch */}
      <RecordTransactionModal
        open={isRecordModalOpen}
        supplier={selectedSupplierForRecord}
        submitting={recordSubmitting}
        onCancel={() => {
          setIsRecordModalOpen(false)
          setSelectedSupplierForRecord(null)
        }}
        onSubmit={handleRecordSubmit}
      />
    </div>
  )
}
