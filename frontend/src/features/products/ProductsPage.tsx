import {
  AppstoreOutlined,
  CheckCircleOutlined,
  FileExcelOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  TagsOutlined,
} from '@ant-design/icons'
import { App, Button, Card, Col, Row, Space, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import BarcodeModal from './components/BarcodeModal'
import ExcelImportModal from './components/ExcelImportModal'
import ProductFormModal from './components/ProductFormModal'
import ProductTable from './components/ProductTable'
import UnitConversionModal from './components/UnitConversionModal'
import * as productApi from './productApi'
import type { Category, CreateProductPayload, Product, Uom, UpdateProductPayload } from './types'
import { getApiErrorMessage } from '../../services/api'

const { Title, Text } = Typography

export default function ProductsPage() {
  const { message } = App.useApp()

  // State danh sách
  const [products, setProducts] = useState<Product[]>([])
  const [total, setTotal] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(false)
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Filters
  const [keyword, setKeyword] = useState<string | undefined>(undefined)
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)
  const [active, setActive] = useState<boolean | undefined>(undefined)

  // Danh mục dùng chung
  const [categories, setCategories] = useState<Category[]>([])
  const [uoms, setUoms] = useState<Uom[]>([])

  // Modal State
  const [isFormModalOpen, setIsFormModalOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const [isConversionModalOpen, setIsConversionModalOpen] = useState(false)
  const [selectedProductForConversion, setSelectedProductForConversion] = useState<Product | null>(null)

  const [isBarcodeModalOpen, setIsBarcodeModalOpen] = useState(false)
  const [selectedProductForBarcode, setSelectedProductForBarcode] = useState<Product | null>(null)

  const [isImportModalOpen, setIsImportModalOpen] = useState(false)

  // Load danh sách SKU
  const loadProducts = useCallback(async () => {
    setLoading(true)
    try {
      const data = await productApi.fetchProducts({
        keyword,
        categoryId,
        active,
        page,
        size: pageSize,
      })
      setProducts(data.content || [])
      setTotal(data.totalElements || 0)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải danh sách sản phẩm'))
    } finally {
      setLoading(false)
    }
  }, [keyword, categoryId, active, page, pageSize, message])

  useEffect(() => {
    let ignore = false
    const init = async () => {
      try {
        const [cats, uomList] = await Promise.all([
          productApi.fetchCategories(),
          productApi.fetchUoms(),
        ])
        if (!ignore) {
          setCategories(cats)
          setUoms(uomList)
        }
      } catch (err) {
        console.error(err)
      }
    }
    void init()
    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    let ignore = false
    const fetchList = async () => {
      setLoading(true)
      try {
        const data = await productApi.fetchProducts({
          keyword,
          categoryId,
          active,
          page,
          size: pageSize,
        })
        if (!ignore) {
          setProducts(data.content || [])
          setTotal(data.totalElements || 0)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách sản phẩm'))
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
  }, [keyword, categoryId, active, page, pageSize, message])

  // Xử lý tìm kiếm / lọc
  const handleSearch = (kw?: string, catId?: number, act?: boolean) => {
    setKeyword(kw)
    setCategoryId(catId)
    setActive(act)
    setPage(0)
  }

  // Thêm mới / Cập nhật sản phẩm
  const handleFormSubmit = async (payload: CreateProductPayload | UpdateProductPayload) => {
    setSubmitting(true)
    try {
      if (editingProduct) {
        await productApi.updateProduct(editingProduct.id, payload as UpdateProductPayload)
        message.success('Cập nhật sản phẩm thành công!')
      } else {
        await productApi.createProduct(payload as CreateProductPayload)
        message.success('Thêm mới sản phẩm (SKU) thành công!')
      }
      setIsFormModalOpen(false)
      setEditingProduct(null)
      loadProducts()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Lưu sản phẩm thất bại'))
      throw err
    } finally {
      setSubmitting(false)
    }
  }

  // Xóa sản phẩm
  const handleDeleteProduct = async (id: number) => {
    try {
      await productApi.deleteProduct(id)
      message.success('Đã xóa sản phẩm thành công')
      loadProducts()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể xóa sản phẩm này'))
    }
  }

  // Thống kê nhanh
  const activeCount = products.filter((p) => p.active).length
  const lotManagedCount = products.filter((p) => p.manageByLot).length

  return (
    <div className="space-y-6">
      {/* Tiêu đề & Nút thao tác đầu trang */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1">
            Quản lý Danh mục Hàng hóa & Mã SKU
          </Title>
          <Text type="secondary">
            Quản lý mã hàng (SKU), barcode, đơn vị tính quy đổi đa cấp (FR05, FR06) và in tem nhãn (FR07).
          </Text>
        </div>

        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadProducts}>
            Làm mới
          </Button>

          <Button
            icon={<FileExcelOutlined className="text-emerald-600" />}
            onClick={() => setIsImportModalOpen(true)}
          >
            Nhập Excel
          </Button>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingProduct(null)
              setIsFormModalOpen(true)
            }}
          >
            Thêm sản phẩm mới
          </Button>
        </Space>
      </div>

      {/* Thẻ chỉ số tổng quan */}
      <Row gutter={16}>
        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Tổng số mã SKU</div>
                <div className="text-2xl font-bold text-slate-900 mt-1">{total}</div>
              </div>
              <div className="w-10 h-10 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center text-xl">
                <AppstoreOutlined />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Đang kinh doanh</div>
                <div className="text-2xl font-bold text-emerald-600 mt-1">{activeCount}</div>
              </div>
              <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center text-xl">
                <CheckCircleOutlined />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Theo dõi theo Lô (Batch)</div>
                <div className="text-2xl font-bold text-cyan-600 mt-1">{lotManagedCount}</div>
              </div>
              <div className="w-10 h-10 rounded-lg bg-cyan-50 text-cyan-600 flex items-center justify-center text-xl">
                <SafetyCertificateOutlined />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Nhóm ngành hàng</div>
                <div className="text-2xl font-bold text-purple-600 mt-1">{categories.length}</div>
              </div>
              <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center text-xl">
                <TagsOutlined />
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Bảng dữ liệu chính */}
      <ProductTable
        products={products}
        total={total}
        loading={loading}
        page={page}
        pageSize={pageSize}
        categories={categories}
        onPageChange={(p, ps) => {
          setPage(p)
          setPageSize(ps)
        }}
        onSearch={handleSearch}
        onEdit={(prod) => {
          setEditingProduct(prod)
          setIsFormModalOpen(true)
        }}
        onDelete={handleDeleteProduct}
        onOpenConversions={(prod) => {
          setSelectedProductForConversion(prod)
          setIsConversionModalOpen(true)
        }}
        onOpenBarcode={(prod) => {
          setSelectedProductForBarcode(prod)
          setIsBarcodeModalOpen(true)
        }}
      />

      {/* Modal tạo / sửa sản phẩm */}
      <ProductFormModal
        open={isFormModalOpen}
        editingProduct={editingProduct}
        categories={categories}
        uoms={uoms}
        submitting={submitting}
        onCancel={() => {
          setIsFormModalOpen(false)
          setEditingProduct(null)
        }}
        onSubmit={handleFormSubmit}
      />

      {/* Modal cấu hình quy đổi đơn vị */}
      <UnitConversionModal
        open={isConversionModalOpen}
        product={selectedProductForConversion}
        uoms={uoms}
        onClose={() => {
          setIsConversionModalOpen(false)
          setSelectedProductForConversion(null)
        }}
      />

      {/* Modal In & Xem Barcode/QR Code */}
      <BarcodeModal
        open={isBarcodeModalOpen}
        product={selectedProductForBarcode}
        onClose={() => {
          setIsBarcodeModalOpen(false)
          setSelectedProductForBarcode(null)
        }}
      />

      {/* Modal Import Excel */}
      <ExcelImportModal
        open={isImportModalOpen}
        categories={categories}
        uoms={uoms}
        onClose={() => setIsImportModalOpen(false)}
        onSuccess={() => loadProducts()}
      />
    </div>
  )
}
