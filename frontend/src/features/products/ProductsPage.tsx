import {
  AppstoreOutlined,
  FileExcelOutlined,
  PlusOutlined,
  ReloadOutlined,
  TagsOutlined,
} from '@ant-design/icons'
import { App, Button, Card, Col, Row, Space } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import BarcodeModal from './components/BarcodeModal'
import ExcelImportModal from './components/ExcelImportModal'
import ProductFormModal from './components/ProductFormModal'
import ProductTable from './components/ProductTable'
import UnitConversionModal from './components/UnitConversionModal'
import * as productApi from './productApi'
import type { Category, CreateProductPayload, Product, Uom, UpdateProductPayload } from './types'
import { getApiErrorMessage } from '../../services/api'

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
        message.success('Cập nhật sản phẩm thành công')
      } else {
        await productApi.createProduct(payload as CreateProductPayload)
        message.success('Thêm mới sản phẩm (SKU) thành công')
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

  // Xóa sản phẩm (Xóa mềm - ngưng hoạt động)
  const handleDeleteProduct = async (id: number) => {
    try {
      await productApi.deleteProduct(id)
      message.success('Đã chuyển sản phẩm sang trạng thái ngưng hoạt động')
      loadProducts()
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể cập nhật trạng thái sản phẩm'))
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return (
    <div className="space-y-5">
      {/* Tiêu đề & Nút thao tác đầu trang */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-1">
        <div>
          <h1 className="text-lg font-semibold text-slate-900 m-0 leading-tight">
            Danh mục Hàng hóa & Mã SKU
          </h1>
          <p className="text-xs text-slate-500 m-0 mt-1">
            Quản lý mã định danh SKU, barcode, đơn vị tính quy đổi và định mức tồn kho an toàn.
          </p>
        </div>

        <Space size={8} className="shrink-0">
          <Button
            icon={<ReloadOutlined className="text-slate-500" />}
            onClick={loadProducts}
            className="text-xs"
          >
            Làm mới
          </Button>

          <Button
            icon={<FileExcelOutlined className="text-slate-600" />}
            onClick={() => setIsImportModalOpen(true)}
            className="text-xs"
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
            className="text-xs font-medium"
          >
            Thêm sản phẩm
          </Button>
        </Space>
      </div>

      {/* Thẻ chỉ số tổng quan - Tinh gọn, không màu mè trang trí thừa */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200/80 shadow-none">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Tổng số mã SKU</div>
                <div className="text-2xl font-semibold text-slate-900 mt-1 tabular-nums">
                  {total}
                </div>
              </div>
              <AppstoreOutlined className="text-slate-300 text-2xl" />
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200/80 shadow-none">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Nhóm ngành hàng</div>
                <div className="text-2xl font-semibold text-slate-900 mt-1 tabular-nums">
                  {categories.length}
                </div>
              </div>
              <TagsOutlined className="text-slate-300 text-2xl" />
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200/80 shadow-none">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Đơn vị đo lường</div>
                <div className="text-2xl font-semibold text-slate-900 mt-1 tabular-nums">
                  {uoms.length}
                </div>
              </div>
              <span className="text-slate-300 text-xl font-bold">UoM</span>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card size="small" className="border-slate-200/80 shadow-none">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-500 font-medium">Phân trang hiển thị</div>
                <div className="text-2xl font-semibold text-slate-900 mt-1 tabular-nums">
                  {page + 1} <span className="text-sm font-normal text-slate-400">/ {totalPages}</span>
                </div>
              </div>
              <span className="text-xs text-slate-400">
                {products.length} mục
              </span>
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
        onOpenConversions={(prod) => {
          setIsFormModalOpen(false)
          setEditingProduct(null)
          setSelectedProductForConversion(prod)
          setIsConversionModalOpen(true)
        }}
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
