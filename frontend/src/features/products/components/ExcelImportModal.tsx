import {
  DownloadOutlined,
  FileExcelOutlined,
  InboxOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Modal,
  Progress,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd'
import { useState } from 'react'
import * as XLSX from 'xlsx'
import * as productApi from '../productApi'
import type { Category, CreateProductPayload, Uom } from '../types'

interface ExcelImportModalProps {
  open: boolean
  categories: Category[]
  uoms: Uom[]
  onClose: () => void
  onSuccess: () => void
}

const { Text } = Typography

export default function ExcelImportModal({
  open,
  categories,
  uoms,
  onClose,
  onSuccess,
}: ExcelImportModalProps) {
  const { message } = App.useApp()
  const [parsedRows, setParsedRows] = useState<CreateProductPayload[]>([])
  const [loading, setLoading] = useState(false)
  const [progress, setProgress] = useState<number>(0)

  const handleDownloadTemplate = () => {
    const templateData = [
      {
        'Mã SKU (*)': 'SKU-GAO-ST25-5KG',
        'Tên sản phẩm (*)': 'Gạo thơm ST25 Túi 5kg',
        'Mã vạch (Barcode)': '8938501234567',
        'Mã đơn vị tính (*)': uoms[0]?.code || 'PCS',
        'Mã nhóm hàng': categories[0]?.code || 'BEV',
        'Giá nhập (VNĐ)': 150000,
        'Giá bán (VNĐ)': 180000,
        'Thuế VAT (%)': 5,
        'Quản lý theo lô (1/0)': 1,
        'Quản lý hạn dùng (1/0)': 1,
        'Tồn an toàn': 50,
        'Điểm đặt lại': 100,
        'Tồn tối đa': 1000,
        'Mô tả': 'Gạo đặc sản Sóc Trăng',
      },
      {
        'Mã SKU (*)': 'BEER-TIGER-330',
        'Tên sản phẩm (*)': 'Bia Tiger Lon 330ml',
        'Mã vạch (Barcode)': '8936036010012',
        'Mã đơn vị tính (*)': uoms.find((u) => u.code === 'CAN')?.code || uoms[0]?.code || 'CAN',
        'Mã nhóm hàng': categories[0]?.code || 'BEV',
        'Giá nhập (VNĐ)': 12000,
        'Giá bán (VNĐ)': 16000,
        'Thuế VAT (%)': 10,
        'Quản lý theo lô (1/0)': 1,
        'Quản lý hạn dùng (1/0)': 1,
        'Tồn an toàn': 200,
        'Điểm đặt lại': 500,
        'Tồn tối đa': 5000,
        'Mô tả': 'Bia lon vàng 330ml',
      },
    ]

    const worksheet = XLSX.utils.json_to_sheet(templateData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Danh_muc_SKU')
    XLSX.writeFile(workbook, 'WMS_Mau_Import_SKU.xlsx')
    message.success('Đã tải xuống file Excel mẫu')
  }

  const handleFileUpload = (file: File) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target?.result as ArrayBuffer)
        const workbook = XLSX.read(data, { type: 'array' })
        const sheetName = workbook.SheetNames[0]
        const worksheet = workbook.Sheets[sheetName]
        const json: Record<string, unknown>[] = XLSX.utils.sheet_to_json<Record<string, unknown>>(worksheet)

        if (!json || json.length === 0) {
          message.warning('File Excel trống hoặc không đúng định dạng')
          return
        }

        const uomMap = new Map(uoms.map((u) => [u.code.toUpperCase(), u.id]))
        const categoryMap = new Map(categories.map((c) => [c.code.toUpperCase(), c.id]))

        const converted: CreateProductPayload[] = []
        for (const row of json) {
          const sku = String(row['Mã SKU (*)'] || row['SKU'] || '').trim().toUpperCase()
          const name = String(row['Tên sản phẩm (*)'] || row['Tên'] || '').trim()
          const uomCode = String(row['Mã đơn vị tính (*)'] || row['UOM'] || '').trim().toUpperCase()
          const catCode = String(row['Mã nhóm hàng'] || row['Category'] || '').trim().toUpperCase()

          if (!sku || !name) continue

          const uomId = uomMap.get(uomCode) || uoms[0]?.id || 1
          const categoryId = categoryMap.get(catCode)

          converted.push({
            sku,
            name,
            barcode: row['Mã vạch (Barcode)'] ? String(row['Mã vạch (Barcode)']).trim() : undefined,
            uomId,
            categoryId,
            purchasePrice: Number(row['Giá nhập (VNĐ)'] || 0),
            sellingPrice: Number(row['Giá bán (VNĐ)'] || 0),
            vatRate: Number(row['Thuế VAT (%)'] || 0),
            manageByLot: String(row['Quản lý theo lô (1/0)']) === '1',
            manageByExpiration: String(row['Quản lý hạn dùng (1/0)']) === '1',
            safetyStock: Number(row['Tồn an toàn'] || 0),
            reorderPoint: Number(row['Điểm đặt lại'] || 0),
            maxStock: Number(row['Tồn tối đa'] || 0),
            description: row['Mô tả'] ? String(row['Mô tả']).trim() : undefined,
          })
        }

        setParsedRows(converted)
        message.success(`Đã đọc ${converted.length} dòng sản phẩm hợp lệ từ file`)
      } catch (err) {
        message.error('Lỗi khi đọc file Excel: ' + String(err))
      }
    }
    reader.readAsArrayBuffer(file)
    return false // Ngăn Ant Design tự động upload HTTP
  }

  const handleImport = async () => {
    if (parsedRows.length === 0) {
      message.warning('Chưa có dữ liệu để import')
      return
    }

    setLoading(true)
    setProgress(0)
    let successCount = 0
    let errorCount = 0

    for (let i = 0; i < parsedRows.length; i++) {
      const item = parsedRows[i]
      try {
        await productApi.createProduct(item)
        successCount++
      } catch {
        errorCount++
      }
      setProgress(Math.round(((i + 1) / parsedRows.length) * 100))
    }

    setLoading(false)
    if (errorCount === 0) {
      message.success(`Đã import thành công toàn bộ ${successCount} sản phẩm!`)
    } else {
      message.warning(`Import hoàn tất: ${successCount} thành công, ${errorCount} lỗi (có thể do trùng mã SKU).`)
    }
    onSuccess()
    onClose()
  }

  const columns = [
    { title: 'Mã SKU', dataIndex: 'sku', key: 'sku', render: (t: string) => <Tag color="blue">{t}</Tag> },
    { title: 'Tên sản phẩm', dataIndex: 'name', key: 'name' },
    { title: 'Barcode', dataIndex: 'barcode', key: 'barcode', render: (t?: string) => t || '-' },
    {
      title: 'Đơn vị',
      dataIndex: 'uomId',
      key: 'uomId',
      render: (id: number) => uoms.find((u) => u.id === id)?.name || id,
    },
    {
      title: 'Giá nhập',
      dataIndex: 'purchasePrice',
      key: 'purchasePrice',
      render: (v: number) => (v ? `${v.toLocaleString('vi-VN')} đ` : '-'),
    },
    {
      title: 'Giá bán',
      dataIndex: 'sellingPrice',
      key: 'sellingPrice',
      render: (v: number) => (v ? `${v.toLocaleString('vi-VN')} đ` : '-'),
    },
    {
      title: 'Theo lô',
      dataIndex: 'manageByLot',
      key: 'manageByLot',
      render: (v: boolean) => (v ? <Tag color="green">Có</Tag> : 'Không'),
    },
  ]

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <FileExcelOutlined className="text-emerald-600 text-lg" />
          <span className="text-lg font-semibold">Nhập danh mục SKU từ file Excel (FR05)</span>
        </div>
      }
      open={open}
      onCancel={onClose}
      width={850}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={loading}>
          Đóng
        </Button>,
        <Button
          key="import"
          type="primary"
          onClick={handleImport}
          loading={loading}
          disabled={parsedRows.length === 0}
        >
          Tiến hành Import ({parsedRows.length} SKU)
        </Button>,
      ]}
      destroyOnClose
    >
      <div className="space-y-4">
        <Alert
          type="info"
          showIcon
          message="Hướng dẫn nhập file"
          description={
            <div className="flex items-center justify-between">
              <span>
                Vui lòng tải file mẫu bên dưới để đảm bảo dữ liệu cột khớp với cấu trúc hệ thống.
              </span>
              <Button
                type="link"
                icon={<DownloadOutlined />}
                onClick={handleDownloadTemplate}
                className="font-medium"
              >
                Tải file mẫu (.xlsx)
              </Button>
            </div>
          }
        />

        <Upload.Dragger
          name="file"
          accept=".xlsx, .xls, .csv"
          beforeUpload={handleFileUpload}
          showUploadList={false}
          className="p-4"
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined className="text-blue-500 text-4xl" />
          </p>
          <p className="ant-upload-text font-medium">Kéo thả file Excel vào đây hoặc nhấp để chọn file</p>
          <p className="ant-upload-hint text-xs text-slate-400">
            Hỗ trợ định dạng .xlsx, .xls, .csv chứa danh sách mã SKU và quy cách
          </p>
        </Upload.Dragger>

        {loading && (
          <div className="space-y-2">
            <Text className="text-xs text-slate-500">Đang lưu dữ liệu vào hệ thống...</Text>
            <Progress percent={progress} status="active" />
          </div>
        )}

        {parsedRows.length > 0 && (
          <div>
            <div className="text-sm font-semibold text-slate-700 mb-2">
              Dữ liệu xem trước ({parsedRows.length} sản phẩm):
            </div>
            <Table
              dataSource={parsedRows}
              columns={columns}
              rowKey="sku"
              size="small"
              pagination={{ pageSize: 5 }}
              bordered
            />
          </div>
        )}
      </div>
    </Modal>
  )
}
