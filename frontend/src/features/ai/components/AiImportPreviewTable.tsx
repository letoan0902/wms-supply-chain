import { DeleteOutlined } from '@ant-design/icons'
import { Button, Input, Select, Table, Tag, Tooltip } from 'antd'
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
import { fieldWarnings, ROW_STATUS_META } from '../aiImportStatus'
import type { RowAssessment } from '../aiImportStatus'
import type { CellValue, ExtractionColumn } from '../types'

interface EditableCellProps {
  column: ExtractionColumn
  row: PreviewRow
  error: string | undefined
  /** Ô mã trùng với một dòng phía trên đang được chọn */
  duplicate: string | undefined
  disabled: boolean
  onCommit: (value: CellValue) => void
}

/** Ô sửa được: bấm (hoặc Enter khi đang chọn ô) để sửa, Enter hoặc rời ô để lưu, Esc để bỏ. */
function EditableCell({ column, row, error, duplicate, disabled, onCommit }: EditableCellProps) {
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
  if (duplicate) tooltipLines.push(duplicate)
  if (low) {
    tooltipLines.push(...fieldWarnings(row.warnings, column.label))
    tooltipLines.push(`Độ tin cậy ${formatConfidence(row.confidence[column.field]) ?? ''}: hãy kiểm tra lại ô này`)
  }
  if (edited) tooltipLines.push('Bạn đã sửa ô này')

  const tone = error
    ? 'bg-red-50 ring-1 ring-red-300'
    : duplicate
      ? 'bg-purple-50 ring-1 ring-purple-300'
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

interface StatusCellProps {
  row: PreviewRow
  assessment: RowAssessment | undefined
}

/** Thẻ trạng thái đầu dòng: màu và nhãn theo bảng màu chung, rê chuột hoặc Tab tới để đọc lý do. */
function StatusCell({ row, assessment }: StatusCellProps) {
  if (!assessment) return null
  const meta = ROW_STATUS_META[assessment.status]
  const lines: string[] = []
  if (assessment.status === 'excluded') {
    lines.push('Bạn đã bỏ chọn dòng này: không gửi đi khi nhập. Tích lại ô chọn để nhập.')
    if (assessment.problem !== 'ready') lines.push(...assessment.reasons)
  } else if (assessment.status === 'ready') {
    lines.push('Đủ thông tin, AI đọc chắc chắn: sẵn sàng nhập.')
  } else {
    lines.push(...assessment.reasons)
    if (assessment.status === 'review') lines.push('Vẫn nhập được; hãy so lại với file gốc.')
    else lines.push('Phải sửa hoặc bỏ chọn dòng này trước khi nhập.')
  }
  return (
    <Tooltip
      trigger={['hover', 'focus']}
      title={
        <div className="space-y-0.5">
          {lines.map((line, index) => (
            <div key={index}>{line}</div>
          ))}
        </div>
      }
    >
      <div
        tabIndex={0}
        aria-label={`Dòng ${row.row}: ${meta.label}. ${lines.join(' ')}`}
        className="cursor-help rounded leading-tight focus-visible:outline-2 focus-visible:outline-blue-500"
      >
        <Tag color={meta.tagColor} className="m-0 whitespace-nowrap">
          {meta.label}
        </Tag>
        {assessment.shortReason && (
          <div className="mt-1 line-clamp-2 text-[11px] text-slate-500">{assessment.shortReason}</div>
        )}
      </div>
    </Tooltip>
  )
}

interface AiImportPreviewTableProps {
  columns: ExtractionColumn[]
  rows: PreviewRow[]
  /** Lỗi theo dòng (số dòng xem trước) và trường */
  errorsByRow: Map<number, Record<string, string>>
  /** Trạng thái từng dòng (aiImportStatus.assessRows) */
  assessments: Map<number, RowAssessment>
  /** Trường mã để đánh dấu ô trùng */
  identityField: string
  /** Trường đang ẩn (cột trống) */
  hiddenFields: ReadonlySet<string>
  selectedKeys: number[]
  method: string
  disabled: boolean
  onSelectionChange: (keys: number[]) => void
  onCellChange: (row: number, field: string, value: CellValue) => void
  onDeleteRow: (row: number) => void
}

/**
 * Bảng xem trước sửa được: mỗi cột một trường của API nhập hàng loạt. Cột đầu là thẻ trạng thái của dòng; ô độ tin
 * cậy thấp nền vàng, ô lỗi viền đỏ, ô mã trùng viền tím. Ô chọn ở đầu dòng quyết định dòng có được gửi đi hay không;
 * dòng bỏ chọn vẫn nằm trong bảng.
 */
export default function AiImportPreviewTable({
  columns,
  rows,
  errorsByRow,
  assessments,
  identityField,
  hiddenFields,
  selectedKeys,
  method,
  disabled,
  onSelectionChange,
  onCellChange,
  onDeleteRow,
}: AiImportPreviewTableProps) {
  const tableColumns: ColumnsType<PreviewRow> = [
    {
      title: 'Trạng thái',
      key: 'status',
      width: 168,
      className: 'ai-preview-status-cell',
      fixed: 'left',
      render: (_, row) => <StatusCell row={row} assessment={assessments.get(row.row)} />,
    },
    {
      title: 'Dòng',
      key: 'row',
      width: 76,
      render: (_, row) => (
        <div className="leading-tight">
          <div className="font-medium">{row.row}</div>
          {row.sourceRow !== null && <div className="text-[11px] text-slate-400">Dòng file {row.sourceRow}</div>}
        </div>
      ),
    },
    ...columns
      .filter((column) => !hiddenFields.has(column.field))
      .map<ColumnsType<PreviewRow>[number]>((column) => ({
        title: <ColumnTitle column={column} method={method} />,
        key: column.field,
        width: isNumericColumn(column) ? 130 : column.maxLength && column.maxLength > 200 ? 240 : 170,
        render: (_: unknown, row: PreviewRow) => {
          const assessment = assessments.get(row.row)
          const duplicate =
            column.field === identityField && assessment?.duplicateOf != null
              ? `Mã trùng với dòng ${assessment.duplicateOf}: mỗi mã chỉ nhập được một dòng. Sửa mã hoặc bỏ chọn một trong hai dòng.`
              : undefined
          return (
            <EditableCell
              column={column}
              row={row}
              error={errorsByRow.get(row.row)?.[column.field]}
              duplicate={duplicate}
              disabled={disabled}
              onCommit={(value) => onCellChange(row.row, column.field, value)}
            />
          )
        },
      })),
    {
      title: '',
      key: 'actions',
      width: 56,
      fixed: 'right',
      render: (_, row) => (
        <Tooltip title="Xóa hẳn dòng khỏi bảng (muốn giữ lại thì chỉ bỏ chọn)">
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
      className="ai-preview-table"
      columns={tableColumns}
      dataSource={rows}
      scroll={{ x: 'max-content' }}
      rowClassName={(row) => (assessments.get(row.row)?.status === 'excluded' ? 'ai-preview-row-excluded' : '')}
      pagination={{ defaultPageSize: 50, showSizeChanger: true, pageSizeOptions: [20, 50, 100, 200], hideOnSinglePage: false }}
      locale={{ emptyText: 'Không có dòng nào trong nhóm này' }}
      rowSelection={{
        selectedRowKeys: selectedKeys,
        preserveSelectedRowKeys: true,
        fixed: true,
        getCheckboxProps: (row) => ({
          disabled,
          'aria-label': `Nhập dòng ${row.row}`,
        }),
        onChange: (keys) => onSelectionChange(keys.map((key) => Number(key))),
      }}
    />
  )
}
