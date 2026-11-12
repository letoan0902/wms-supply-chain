import { DeleteOutlined, WarningOutlined } from '@ant-design/icons'
import { Button, Input, Select, Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { KeyboardEvent } from 'react'
import { useState } from 'react'
import {
  cellDraft,
  coerceCellInput,
  formatCell,
  formatConfidence,
  isEmptyValue,
  isLowConfidence,
  isNumericColumn,
  LOW_CONFIDENCE_THRESHOLD,
  OPTION_LABELS,
} from '../aiImport'
import type { PreviewRow } from '../aiImport'
import type { CellValue, ExtractionColumn } from '../types'

interface EditableCellProps {
  column: ExtractionColumn
  row: PreviewRow
  error: string | undefined
  disabled: boolean
  onCommit: (value: CellValue) => void
}

/** Ô sửa được: bấm (hoặc Enter khi đang chọn ô) để sửa, Enter hoặc rời ô để lưu, Esc để bỏ. */
function EditableCell({ column, row, error, disabled, onCommit }: EditableCellProps) {
  const value = row.values[column.field]
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const low = isLowConfidence(row, column.field)
  const edited = row.edited.includes(column.field)
  const isEnum = column.type === 'ENUM' && column.options.length > 0

  const start = () => {
    if (disabled) return
    setDraft(cellDraft(value))
    setEditing(true)
  }

  const commit = () => {
    setEditing(false)
    // Không đổi gì thì giữ nguyên giá trị gốc (tránh đọc lại số như 1.125 thành 1125)
    if (draft === cellDraft(value)) return
    onCommit(coerceCellInput(column, draft))
  }

  if (editing && !disabled) {
    if (isEnum) {
      const current = typeof value === 'string' && column.options.includes(value) ? value : undefined
      return (
        <Select
          autoFocus
          defaultOpen
          size="small"
          className="w-full"
          allowClear
          value={current}
          placeholder="Chọn"
          options={column.options.map((option) => ({ value: option, label: OPTION_LABELS[option] ?? option }))}
          onChange={(next?: string) => {
            setEditing(false)
            onCommit(next ?? null)
          }}
          onBlur={() => setEditing(false)}
        />
      )
    }
    const handleKey = (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Escape') setEditing(false)
    }
    return (
      <Input
        autoFocus
        size="small"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onPressEnter={commit}
        onKeyDown={handleKey}
        placeholder={isNumericColumn(column) ? 'Ví dụ 150.000' : column.label}
        count={column.maxLength ? { max: column.maxLength, show: true } : undefined}
      />
    )
  }

  const tooltipLines: string[] = []
  if (error) tooltipLines.push(error)
  if (low) {
    tooltipLines.push(`Độ tin cậy ${formatConfidence(row.confidence[column.field]) ?? ''}: hãy kiểm tra lại ô này`)
  }
  if (edited) tooltipLines.push('Bạn đã sửa ô này')

  const tone = error
    ? 'bg-red-50 ring-1 ring-red-300'
    : low
      ? 'bg-yellow-100'
      : ''
  const display = formatCell(column, value)
  const cell = (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label={`Sửa ${column.label} của dòng ${row.row}`}
      onClick={start}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === 'F2') {
          event.preventDefault()
          start()
        }
      }}
      className={`min-h-[24px] rounded px-1.5 py-0.5 text-[13px] break-words ${tone} ${
        disabled ? 'cursor-not-allowed' : 'cursor-text hover:ring-1 hover:ring-blue-300'
      } ${edited ? 'text-blue-700' : ''}`}
    >
      {isEmptyValue(value) ? <span className="text-slate-300">trống</span> : display}
    </div>
  )
  if (tooltipLines.length === 0) return cell
  return (
    <Tooltip
      title={
        <div>
          {tooltipLines.map((line) => (
            <div key={line}>{line}</div>
          ))}
        </div>
      }
    >
      {cell}
    </Tooltip>
  )
}

function ColumnTitle({ column, method }: { column: ExtractionColumn; method: string }) {
  const mapped = column.sourceHeader !== null && column.sourceHeader !== undefined
  const lines: string[] = []
  if (mapped) {
    lines.push(
      `Lấy từ cột "${column.sourceHeader}"${column.sourceColumn ? ` (cột thứ ${column.sourceColumn})` : ''} của file`,
    )
    const confidence = formatConfidence(column.mappingConfidence)
    if (confidence) lines.push(`Độ tin cậy ghép cột: ${confidence}`)
  } else if (method === 'AI_DOCUMENT') {
    lines.push('AI đọc trực tiếp từ tài liệu, không theo cột')
  } else {
    lines.push('Không tìm thấy cột tương ứng trong file: hãy nhập bổ sung nếu cần')
  }
  if (column.required) lines.push('Trường bắt buộc')
  if (column.maxLength) lines.push(`Tối đa ${column.maxLength} ký tự`)
  if (isNumericColumn(column)) lines.push('Nhập số, ví dụ 150.000 hoặc 12,5')

  const weak =
    (mapped && typeof column.mappingConfidence === 'number' && column.mappingConfidence < LOW_CONFIDENCE_THRESHOLD) ||
    (!mapped && method !== 'AI_DOCUMENT' && column.required)

  return (
    <Tooltip
      title={
        <div>
          {lines.map((line) => (
            <div key={line}>{line}</div>
          ))}
        </div>
      }
    >
      <div className="leading-tight">
        <span className={weak ? 'text-amber-600' : ''}>
          {column.label}
          {column.required && <span className="text-red-500"> *</span>}
        </span>
        <div className="truncate text-[11px] font-normal text-slate-400">
          {mapped ? `Từ: ${column.sourceHeader}` : method === 'AI_DOCUMENT' ? 'AI đọc' : 'Không có cột'}
        </div>
      </div>
    </Tooltip>
  )
}

interface AiImportPreviewTableProps {
  columns: ExtractionColumn[]
  rows: PreviewRow[]
  /** Lỗi theo dòng (số dòng xem trước) và trường */
  errorsByRow: Map<number, Record<string, string>>
  selectedKeys: number[]
  method: string
  disabled: boolean
  onSelectionChange: (keys: number[]) => void
  onCellChange: (row: number, field: string, value: CellValue) => void
  onDeleteRow: (row: number) => void
}

/**
 * Bảng xem trước sửa được: mỗi cột một trường của API nhập hàng loạt; ô độ tin cậy thấp nền vàng, ô lỗi viền đỏ; dòng
 * còn lỗi không chọn được để nhập.
 */
export default function AiImportPreviewTable({
  columns,
  rows,
  errorsByRow,
  selectedKeys,
  method,
  disabled,
  onSelectionChange,
  onCellChange,
  onDeleteRow,
}: AiImportPreviewTableProps) {
  const tableColumns: ColumnsType<PreviewRow> = [
    {
      title: 'Dòng',
      key: 'row',
      width: 80,
      fixed: 'left',
      render: (_, row) => (
        <div className="leading-tight">
          <div className="font-medium">{row.row}</div>
          {row.sourceRow !== null && <div className="text-[11px] text-slate-400">Dòng file {row.sourceRow}</div>}
        </div>
      ),
    },
    {
      title: 'Cảnh báo',
      key: 'warnings',
      width: 90,
      render: (_, row) => {
        const errors = Object.values(errorsByRow.get(row.row) ?? {})
        if (errors.length === 0 && row.warnings.length === 0 && !row.lastError) return null
        return (
          <Tooltip
            title={
              <div className="space-y-1">
                {row.lastError && <div>Lần nhập trước lỗi: {row.lastError}</div>}
                {errors.length > 0 && (
                  <div>
                    Cần sửa trước khi nhập:
                    {errors.map((text) => (
                      <div key={text}>- {text}</div>
                    ))}
                  </div>
                )}
                {row.warnings.length > 0 && (
                  <div>
                    Cảnh báo khi đọc file:
                    {row.warnings.map((text) => (
                      <div key={text}>- {text}</div>
                    ))}
                  </div>
                )}
              </div>
            }
          >
            <span
              className={`inline-flex cursor-help items-center gap-1 text-xs ${
                errors.length > 0 || row.lastError ? 'text-red-600' : 'text-amber-600'
              }`}
            >
              <WarningOutlined />
              {errors.length + row.warnings.length + (row.lastError ? 1 : 0)}
            </span>
          </Tooltip>
        )
      },
    },
    ...columns.map<ColumnsType<PreviewRow>[number]>((column) => ({
      title: <ColumnTitle column={column} method={method} />,
      key: column.field,
      width: isNumericColumn(column) ? 130 : column.maxLength && column.maxLength > 200 ? 240 : 170,
      render: (_: unknown, row: PreviewRow) => (
        <EditableCell
          column={column}
          row={row}
          error={errorsByRow.get(row.row)?.[column.field]}
          disabled={disabled}
          onCommit={(value) => onCellChange(row.row, column.field, value)}
        />
      ),
    })),
    {
      title: '',
      key: 'actions',
      width: 56,
      fixed: 'right',
      render: (_, row) => (
        <Tooltip title="Xóa dòng khỏi bảng (không nhập dòng này)">
          {/* Xóa dòng chỉ đổi bảng trên trình duyệt, không ghi dữ liệu nên vẫn dùng được ở chế độ chỉ đọc */}
          <span data-read-only-allowed>
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              aria-label={`Xóa dòng ${row.row}`}
              disabled={disabled}
              onClick={() => onDeleteRow(row.row)}
            />
          </span>
        </Tooltip>
      ),
    },
  ]

  return (
    <Table<PreviewRow>
      rowKey="row"
      size="small"
      bordered
      columns={tableColumns}
      dataSource={rows}
      scroll={{ x: 'max-content' }}
      pagination={{ defaultPageSize: 50, showSizeChanger: true, pageSizeOptions: [20, 50, 100, 200], hideOnSinglePage: false }}
      locale={{ emptyText: 'Không có dòng nào' }}
      rowSelection={{
        selectedRowKeys: selectedKeys,
        preserveSelectedRowKeys: true,
        fixed: true,
        getCheckboxProps: (row) => ({
          disabled: disabled || Object.keys(errorsByRow.get(row.row) ?? {}).length > 0,
          title: Object.keys(errorsByRow.get(row.row) ?? {}).length > 0 ? 'Sửa lỗi của dòng trước khi chọn' : undefined,
        }),
        onChange: (keys) => onSelectionChange(keys.map((key) => Number(key))),
      }}
    />
  )
}
