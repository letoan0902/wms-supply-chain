import { DownloadOutlined } from '@ant-design/icons'
import { App, Button, Col, Row, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DATA_TYPES, formatCell } from '../aiImport'
import type { MappedRowResult, PreviewRow } from '../aiImport'
import { RESULT_STATUS_META } from '../aiImportStatus'
import { ProportionBar, StatTile } from './AiImportStatusParts'
import type { ExtractionColumn, ExtractionDataType, ImportResult } from '../types'

export interface ImportOutcome {
  result: ImportResult
  /** Kết quả từng dòng đã đối chiếu về dòng xem trước */
  mapped: MappedRowResult[]
  /** Các dòng đã gửi, đúng thứ tự gửi */
  submitted: PreviewRow[]
  /** Dòng chưa vào danh mục: dòng lỗi (giữ giá trị) và dòng chưa gửi */
  remaining: PreviewRow[]
}

interface AiImportResultViewProps {
  dataType: ExtractionDataType
  columns: ExtractionColumn[]
  outcome: ImportOutcome
  onFixRemaining: () => void
  onNewFile: () => void
}

/**
 * Kết quả nhập: thanh tỉ lệ và các con số tổng kết cùng bảng màu với bước xem trước, rồi bảng kết quả từng dòng đối
 * chiếu đúng dòng người dùng đã thấy.
 */
export default function AiImportResultView({
  dataType,
  columns,
  outcome,
  onFixRemaining,
  onNewFile,
}: AiImportResultViewProps) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [downloading, setDownloading] = useState(false)
  const info = DATA_TYPES[dataType]
  const { result, mapped, submitted, remaining } = outcome
  const byRow = new Map(submitted.map((row) => [row.row, row]))
  const nameColumn = columns.find((column) => column.field === 'name')
  const failed = mapped.filter((item) => item.status === 'FAILED')
  // Dòng còn lại trong bảng mà không nằm trong lần gửi này: dòng người dùng đã bỏ chọn
  const submittedRows = new Set(submitted.map((row) => row.row))
  const notSent = remaining.filter((row) => !submittedRows.has(row.row)).length
  const counts = { CREATED: result.created, SKIPPED: result.skipped, FAILED: result.failed, NOT_SENT: notSent }
  const order = ['CREATED', 'SKIPPED', 'FAILED', 'NOT_SENT'] as const

  const downloadFailed = async () => {
    setDownloading(true)
    try {
      const XLSX = await import('xlsx')
      const sheetRows = failed.map((item) => {
        const row = item.previewRow !== null ? byRow.get(item.previewRow) : undefined
        const record: Record<string, string | number> = {
          'Dòng xem trước': item.previewRow ?? '',
          'Dòng trong file': row?.sourceRow ?? '',
        }
        for (const column of columns) {
          const value = row?.values[column.field]
          record[column.label] = value === null || value === undefined ? '' : value
        }
        record['Lý do lỗi'] = item.message ?? ''
        return record
      })
      const sheet = XLSX.utils.json_to_sheet(sheetRows)
      const book = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(book, sheet, 'Dòng lỗi')
      XLSX.writeFile(book, `dong-loi-nhap-${dataType.toLowerCase()}.xlsx`)
    } catch {
      message.error('Không tạo được file danh sách dòng lỗi.')
    } finally {
      setDownloading(false)
    }
  }

  const tableColumns: ColumnsType<MappedRowResult> = [
    {
      title: 'Dòng xem trước',
      key: 'previewRow',
      width: 110,
      render: (_, item) => item.previewRow ?? <span className="text-slate-400">Không rõ</span>,
    },
    {
      title: 'Dòng trong file',
      key: 'sourceRow',
      width: 110,
      render: (_, item) => (item.previewRow !== null ? byRow.get(item.previewRow)?.sourceRow ?? '' : ''),
    },
    {
      title: 'Mã',
      key: 'code',
      width: 140,
      render: (_, item) => <span className="font-mono">{item.code ?? ''}</span>,
    },
    ...(nameColumn
      ? [
          {
            title: nameColumn.label,
            key: 'name',
            width: 200,
            render: (_: unknown, item: MappedRowResult) =>
              item.previewRow !== null ? formatCell(nameColumn, byRow.get(item.previewRow)?.values.name) : '',
          },
        ]
      : []),
    {
      title: 'Kết quả',
      key: 'status',
      width: 150,
      render: (_, item) => {
        const meta = item.status in RESULT_STATUS_META ? RESULT_STATUS_META[item.status as keyof typeof RESULT_STATUS_META] : null
        return <Tag color={meta?.tagColor ?? 'default'}>{meta?.label ?? item.status}</Tag>
      },
    },
    {
      title: 'Chi tiết',
      key: 'message',
      render: (_, item) => item.message ?? '',
    },
  ]

  return (
    <div className="space-y-4">
      <section aria-label="Tóm tắt kết quả nhập" className="space-y-2">
        <div className="text-sm text-slate-700">
          Đã gửi <strong>{result.total}</strong> dòng: <strong>{result.created}</strong> dòng đã vào {info.catalogLabel}
          {result.skipped > 0 && `, ${result.skipped} dòng bỏ qua vì mã đã có`}
          {result.failed > 0 && `, ${result.failed} dòng lỗi`}.
        </div>
        <ProportionBar
          label="Kết quả nhập theo trạng thái"
          total={result.total + notSent}
          segments={order.map((key) => ({
            key,
            label: RESULT_STATUS_META[key].title,
            count: counts[key],
            color: RESULT_STATUS_META[key].barColor,
          }))}
        />
        <Row gutter={[8, 8]}>
          <Col xs={12} sm={8} lg={4}>
            <StatTile title="Đã gửi" value={result.total} hint="Số dòng bạn đã xác nhận nhập" />
          </Col>
          {order.map((key) => (
            <Col key={key} xs={12} sm={8} lg={5}>
              <StatTile
                title={RESULT_STATUS_META[key].title}
                value={counts[key]}
                hint={RESULT_STATUS_META[key].hint}
                color={RESULT_STATUS_META[key].barColor}
              />
            </Col>
          ))}
        </Row>
      </section>

      <div className="flex flex-wrap items-center gap-2">
        <Button type="primary" onClick={() => navigate(info.catalogPath)}>
          Mở {info.catalogLabel}
        </Button>
        {remaining.length > 0 && (
          <Button onClick={onFixRemaining}>Sửa và nhập tiếp {remaining.length} dòng chưa vào danh mục</Button>
        )}
        {failed.length > 0 && (
          <Button icon={<DownloadOutlined />} loading={downloading} onClick={downloadFailed}>
            Tải danh sách dòng lỗi
          </Button>
        )}
        <Button onClick={onNewFile}>Tải file khác</Button>
      </div>

      <Table<MappedRowResult>
        rowKey={(item) => `${item.row}`}
        size="small"
        columns={tableColumns}
        dataSource={mapped}
        scroll={{ x: 'max-content' }}
        pagination={{ defaultPageSize: 50, showSizeChanger: true, pageSizeOptions: [20, 50, 100, 200] }}
        locale={{ emptyText: 'Máy chủ không trả kết quả theo dòng' }}
      />
    </div>
  )
}
