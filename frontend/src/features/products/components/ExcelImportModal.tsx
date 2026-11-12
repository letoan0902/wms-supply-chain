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
import { getApiErrorMessage } from '../../../services/api'
import * as productApi from '../productApi'
import type { BulkProductItem, BulkRowStatus, Category, Uom } from '../types'

interface ExcelImportModalProps {
  open: boolean
  categories: Category[]
  uoms: Uom[]
  onClose: () => void
  onSuccess: () => void
}

/** Một dòng đọc từ file: dòng trên Excel, dữ liệu gửi đi, lỗi phát hiện trước khi gửi và kết quả từ máy chủ. */
interface ImportRow {
  key: number
  /** Số dòng trên bảng tính (dòng 1 là tiêu đề) */
  excelRow: number
  item: BulkProductItem
  /** Lỗi dữ liệu phát hiện khi đọc file: dòng này không được gửi lên máy chủ */
  localError?: string
  status?: BulkRowStatus
  message?: string
}

interface ImportSummary {
  created: number
  skipped: number
  failed: number
  /** Số dòng chưa có kết quả vì một lần gọi bị lỗi (mất mạng, hết hạn gói...) */
  notSent: number
}

const STATUS_TAG: Record<BulkRowStatus, { color: string; label: string }> = {
  CREATED: { color: 'green', label: 'Đã tạo' },
  SKIPPED: { color: 'default', label: 'Bỏ qua' },
  FAILED: { color: 'red', label: 'Lỗi' },
}

const { Text } = Typography

function cellText(row: Record<string, unknown>, ...headers: string[]): string {
  for (const header of headers) {
    const value = row[header]
    if (value !== undefined && value !== null && String(value).trim() !== '') return String(value).trim()
  }
  return ''
}

/** Ô số: để trống thì không gửi; không phải số thì trả lỗi để báo trên dòng đó thay vì tự đổi thành 0. */
function cellNumber(
  row: Record<string, unknown>,
  label: string,
  errors: string[],
  ...headers: string[]
): number | undefined {
  const text = cellText(row, ...headers)
  if (!text) return undefined
  const value = Number(text.replace(/\s/g, ''))
  if (!Number.isFinite(value)) {
    errors.push(`${label} "${text}" không phải là số`)
    return undefined
  }
  return value
}

export default function ExcelImportModal({
  open,
  categories,
  uoms,
  onClose,
  onSuccess,
}: ExcelImportModalProps) {
  const { message } = App.useApp()
  const [rows, setRows] = useState<ImportRow[]>([])
  const [loading, setLoading] = useState(false)
  const [progress, setProgress] = useState<number>(0)
  const [summary, setSummary] = useState<ImportSummary | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)

  const handleDownloadTemplate = () => {
    const templateData = [
      {
        'Mã SKU (*)': 'SKU-GAO-ST25-5KG',
        'Tên sản phẩm (*)': 'Gạo thơm ST25 Túi 5kg',
        'Mã vạch (Barcode)': '8938501234567',
        'Đơn vị tính (*)': uoms[0]?.name || 'Túi',
        'Nhóm hàng': categories[0]?.name || 'Lương thực',
        'Giá nhập (VNĐ)': 150000,
        'Giá bán (VNĐ)': 180000,
        'Thuế VAT (%)': 5,
        'Tồn an toàn': 50,
        'Điểm đặt lại': 100,
        'Tồn tối đa': 1000,
        'Mô tả': 'Gạo đặc sản Sóc Trăng',
      },
      {
        'Mã SKU (*)': 'BEER-TIGER-330',
        'Tên sản phẩm (*)': 'Bia Tiger Lon 330ml',
        'Mã vạch (Barcode)': '8936036010012',
        'Đơn vị tính (*)': uoms.find((u) => u.code === 'CAN')?.name || uoms[0]?.name || 'Lon',
        'Nhóm hàng': categories[0]?.name || 'Đồ uống',
        'Giá nhập (VNĐ)': 12000,
        'Giá bán (VNĐ)': 16000,
        'Thuế VAT (%)': 10,
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

        // Mỗi dòng đều được gửi lên máy chủ để kiểm tra đúng như khi tạo từng hàng hóa (thiếu SKU, tên, đơn vị
        // tính sẽ nhận lỗi theo dòng). Đơn vị tính và nhóm hàng gửi bằng tên (file mẫu cũ ghi mã cũng được: máy chủ
        // tìm theo tên rồi theo mã, không có thì tạo mới cho doanh nghiệp).
        const converted: ImportRow[] = json.map((row, index) => {
          const errors: string[] = []
          const item: BulkProductItem = {
            sku: cellText(row, 'Mã SKU (*)', 'SKU').toUpperCase(),
            name: cellText(row, 'Tên sản phẩm (*)', 'Tên'),
            barcode: cellText(row, 'Mã vạch (Barcode)') || undefined,
            uomName: cellText(row, 'Đơn vị tính (*)', 'Mã đơn vị tính (*)', 'UOM'),
            categoryName: cellText(row, 'Nhóm hàng', 'Mã nhóm hàng', 'Category') || undefined,
            purchasePrice: cellNumber(row, 'Giá nhập', errors, 'Giá nhập (VNĐ)'),
            sellingPrice: cellNumber(row, 'Giá bán', errors, 'Giá bán (VNĐ)'),
            vatRate: cellNumber(row, 'Thuế VAT', errors, 'Thuế VAT (%)'),
            safetyStock: cellNumber(row, 'Tồn an toàn', errors, 'Tồn an toàn'),
            reorderPoint: cellNumber(row, 'Điểm đặt lại', errors, 'Điểm đặt lại'),
            maxStock: cellNumber(row, 'Tồn tối đa', errors, 'Tồn tối đa'),
            description: cellText(row, 'Mô tả') || undefined,
          }
          return {
            key: index,
            // sheet_to_json bỏ dòng tiêu đề và dòng trống; __rowNum__ là chỉ số dòng (từ 0) trên bảng tính
            excelRow: typeof row.__rowNum__ === 'number' ? row.__rowNum__ + 1 : index + 2,
            item,
            localError: errors.length > 0 ? errors.join('; ') : undefined,
          }
        })

        setRows(converted)
        setSummary(null)
        setRequestError(null)
        setProgress(0)
        message.success(`Đã đọc ${converted.length} dòng sản phẩm từ file`)
      } catch (err) {
        message.error('Lỗi khi đọc file Excel: ' + String(err))
      }
    }
    reader.readAsArrayBuffer(file)
    return false // Ngăn Ant Design tự động upload HTTP
  }

  const handleImport = async () => {
    const sendable = rows.filter((r) => !r.localError)
    if (sendable.length === 0) {
      message.warning('Không có dòng hợp lệ để nhập')
      return
    }

    setLoading(true)
    setProgress(0)
    setRequestError(null)
    const outcome = new Map<number, { status: BulkRowStatus; message?: string }>()
    let created = 0
    let skipped = 0
    let failed = 0
    let requestFailed = false
    let error = ''

    // Tách file lớn thành nhiều lần gọi, mỗi lần tối đa 500 dòng; `row` trong kết quả đếm từ 1 trong từng lần gọi
    const chunkSize = productApi.PRODUCT_BULK_MAX_ROWS
    for (let start = 0; start < sendable.length; start += chunkSize) {
      const chunk = sendable.slice(start, start + chunkSize)
      try {
        const result = await productApi.bulkImportProducts(chunk.map((r) => r.item))
        created += result.created
        skipped += result.skipped
        failed += result.failed
        for (const rowResult of result.results) {
          const source = chunk[rowResult.row - 1]
          if (source) {
            outcome.set(source.key, { status: rowResult.status, message: rowResult.message ?? undefined })
          }
        }
      } catch (err) {
        // Chuỗi rỗng: lỗi đã được hộp thoại gói dịch vụ hiển thị (hết hạn, vượt giới hạn gói)
        requestFailed = true
        error = getApiErrorMessage(err, 'Không gửi được dữ liệu nhập hàng hóa')
        break
      }
      setProgress(Math.round((Math.min(start + chunkSize, sendable.length) / sendable.length) * 100))
    }

    const localFailed = rows.length - sendable.length
    const updated = rows.map((r) => {
      if (r.localError) return { ...r, status: 'FAILED' as const, message: r.localError }
      const res = outcome.get(r.key)
      return res ? { ...r, status: res.status, message: res.message } : r
    })
    setRows(updated)
    setSummary({
      created,
      skipped,
      failed: failed + localFailed,
      notSent: sendable.length - outcome.size,
    })
    setRequestError(
      requestFailed ? error || 'Máy chủ từ chối yêu cầu nhập, xem thông báo về gói dịch vụ.' : null,
    )
    setLoading(false)

    if (created > 0) {
      onSuccess()
    }
    if (requestFailed) {
      if (error) message.error(error)
    } else if (failed + localFailed === 0) {
      message.success(`Nhập xong: ${created} hàng hóa mới, ${skipped} SKU đã có được bỏ qua`)
    } else {
      message.warning(`Nhập xong: ${created} tạo mới, ${skipped} bỏ qua, ${failed + localFailed} dòng lỗi`)
    }
  }

  const imported = summary !== null
  const sendableCount = rows.filter((r) => !r.localError).length

  const columns = [
    { title: 'Dòng', dataIndex: 'excelRow', key: 'excelRow', width: 64 },
    {
      title: 'Mã SKU',
      key: 'sku',
      render: (_: unknown, r: ImportRow) => (r.item.sku ? <Tag color="blue">{r.item.sku}</Tag> : '-'),
    },
    { title: 'Tên sản phẩm', key: 'name', render: (_: unknown, r: ImportRow) => r.item.name || '-' },
    { title: 'Đơn vị', key: 'uomName', render: (_: unknown, r: ImportRow) => r.item.uomName || '-' },
    { title: 'Nhóm hàng', key: 'categoryName', render: (_: unknown, r: ImportRow) => r.item.categoryName || '-' },
    {
      title: 'Giá nhập',
      key: 'purchasePrice',
      render: (_: unknown, r: ImportRow) =>
        r.item.purchasePrice !== undefined ? `${r.item.purchasePrice.toLocaleString('vi-VN')} đ` : '-',
    },
    {
      title: 'Giá bán',
      key: 'sellingPrice',
      render: (_: unknown, r: ImportRow) =>
        r.item.sellingPrice !== undefined ? `${r.item.sellingPrice.toLocaleString('vi-VN')} đ` : '-',
    },
    {
      title: 'Kết quả',
      key: 'result',
      width: 260,
      render: (_: unknown, r: ImportRow) => {
        if (r.status) {
          const tag = STATUS_TAG[r.status]
          return (
            <div className="space-y-1">
              <Tag color={tag.color}>{tag.label}</Tag>
              {r.message ? <div className="text-xs text-slate-500">{r.message}</div> : null}
            </div>
          )
        }
        if (r.localError) {
          return (
            <div className="space-y-1">
              <Tag color="red">Lỗi dữ liệu</Tag>
              <div className="text-xs text-slate-500">{r.localError}</div>
            </div>
          )
        }
        return imported ? <Tag>Chưa gửi</Tag> : <Tag>Chờ nhập</Tag>
      },
    },
  ]

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <FileExcelOutlined className="text-emerald-600 text-lg" />
          <span className="text-lg font-semibold">Nhập danh mục SKU từ file Excel</span>
        </div>
      }
      open={open}
      onCancel={onClose}
      width={1000}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={loading}>
          Đóng
        </Button>,
        <Button
          key="import"
          type="primary"
          data-write-action
          onClick={handleImport}
          loading={loading}
          disabled={sendableCount === 0 || imported}
        >
          Tiến hành Import ({sendableCount} SKU)
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
            <div className="space-y-1">
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
              <div className="text-xs text-slate-500">
                Đơn vị tính và nhóm hàng ghi bằng tên; tên chưa có sẽ được tạo mới. SKU đã có trong danh mục được bỏ
                qua, không ghi đè. Quản lý theo lô và hạn dùng bật trong màn hình sửa hàng hóa sau khi nhập.
              </div>
            </div>
          }
        />

        <Upload.Dragger
          name="file"
          accept=".xlsx, .xls, .csv"
          beforeUpload={handleFileUpload}
          showUploadList={false}
          disabled={loading}
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

        {summary && (
          <Alert
            type={requestError || summary.failed > 0 ? 'warning' : 'success'}
            showIcon
            message={`Đã tạo ${summary.created}, bỏ qua ${summary.skipped} SKU đã có, ${summary.failed} dòng lỗi${
              summary.notSent > 0 ? `, ${summary.notSent} dòng chưa gửi được` : ''
            }`}
            description={requestError || undefined}
          />
        )}

        {rows.length > 0 && (
          <div>
            <div className="text-sm font-semibold text-slate-700 mb-2">
              {imported ? 'Kết quả theo dòng' : 'Dữ liệu xem trước'} ({rows.length} dòng):
            </div>
            <Table
              dataSource={rows}
              columns={columns}
              rowKey="key"
              size="small"
              pagination={{ pageSize: 10 }}
              bordered
              scroll={{ x: 900 }}
            />
          </div>
        )}
      </div>
    </Modal>
  )
}
