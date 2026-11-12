import { DownloadOutlined } from '@ant-design/icons'
import { App, Button, Card, Col, Row, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DATA_TYPES, formatCell, STATUS_LABELS } from '../aiImport'
import type { MappedRowResult, PreviewRow } from '../aiImport'
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

/** Kết quả nhập: ba con số tổng kết và bảng kết quả từng dòng, đối chiếu đúng dòng người dùng đã thấy. */
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
        const status = STATUS_LABELS[item.status] ?? { label: item.status, color: 'default' }
        return <Tag color={status.color}>{status.label}</Tag>
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
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Đã gửi" value={result.total} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Đã tạo mới" value={result.created} valueStyle={{ color: '#16a34a' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Bỏ qua (mã đã có)" value={result.skipped} valueStyle={{ color: '#ca8a04' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Lỗi" value={result.failed} valueStyle={{ color: '#dc2626' }} />
          </Card>
        </Col>
      </Row>

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
