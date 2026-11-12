import { ReloadOutlined, RobotOutlined } from '@ant-design/icons'
import { Button, Card, Popconfirm, Skeleton, Tag, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { READ_ONLY_ALLOWED_CLASS } from '../../subscription/readOnlyGuard'
import { fetchAiSummary } from '../aiApi'
import { describeSummaryError } from '../aiErrors'
import type { SummaryErrorView } from '../aiErrors'
import { formatGeneratedAt, formatUsage } from '../aiFormat'
import type { AiSummary } from '../types'

const { Text } = Typography

interface AiSummaryCardProps {
  /** Kho đang chọn trên dashboard, `null` là "tất cả kho" (phạm vi mặc định của người xem) */
  warehouseId: number | null
  /** Bộ lọc kho của dashboard đã chọn xong (tránh tải hai lần, mỗi lần có thể tốn một lượt AI) */
  ready?: boolean
}

type LoadResult = { data: AiSummary; error: null } | { data: null; error: SummaryErrorView }

/**
 * Khối "Tóm tắt bằng AI" trên dashboard (GET /api/ai/summary): 3 đến 5 ý và 1 đến 2 gợi ý từ số liệu điều hành.
 * Tải một lần cho mỗi kho trong một lần mở trang, không gọi lại theo chu kỳ; nút "Làm mới" tạo bản mới (một lượt AI).
 * Vai trò bị 403 thì ẩn cả khối; lỗi khác báo nhẹ trong khối để dashboard vẫn dùng được.
 */
export default function AiSummaryCard({ warehouseId, ready = true }: AiSummaryCardProps) {
  const key = ready ? String(warehouseId ?? 'all') : null
  const [results, setResults] = useState<Record<string, LoadResult | undefined>>({})
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<{ key: string; message: string } | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  /** Các phạm vi kho đã gọi trong lần mở trang này */
  const requestedRef = useRef(new Set<string>())

  useEffect(() => {
    if (key === null || requestedRef.current.has(key)) return
    requestedRef.current.add(key)
    fetchAiSummary(warehouseId)
      .then((data) => setResults((prev) => ({ ...prev, [key]: { data, error: null } })))
      .catch((error: unknown) =>
        setResults((prev) => ({ ...prev, [key]: { data: null, error: describeSummaryError(error) } })),
      )
  }, [key, warehouseId, reloadToken])

  if (key === null) return <SummarySkeleton />
  const result = results[key]
  if (!result) return <SummarySkeleton />
  if (result.error?.kind === 'hidden') return null

  const retry = () => {
    requestedRef.current.delete(key)
    setResults((prev) => ({ ...prev, [key]: undefined }))
    setReloadToken((token) => token + 1)
  }

  const refresh = async () => {
    setRefreshing(true)
    setRefreshError(null)
    try {
      const data = await fetchAiSummary(warehouseId, true)
      setResults((prev) => ({ ...prev, [key]: { data, error: null } }))
    } catch (error: unknown) {
      const view = describeSummaryError(error)
      if (view.kind === 'hidden') {
        setResults((prev) => ({ ...prev, [key]: { data: null, error: view } }))
      } else {
        setRefreshError({ key, message: view.message })
      }
    } finally {
      setRefreshing(false)
    }
  }

  const title = (
    <span className="flex items-center gap-2">
      <RobotOutlined className="text-blue-600" />
      Tóm tắt bằng AI
      {result.data?.mode === 'MOCK' && (
        <Tag color="default" className="m-0 text-[11px] font-normal">
          Chế độ giả lập
        </Tag>
      )}
    </span>
  )

  if (result.error) {
    return (
      <Card size="small" title={title} className="shadow-xs">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
          <Text type="secondary">{result.error.message}</Text>
          {result.error.kind === 'planLimit' ? (
            <Link to="/subscription">Xem gói dịch vụ</Link>
          ) : (
            <Button type="link" size="small" className="p-0" onClick={retry}>
              Thử lại
            </Button>
          )}
        </div>
      </Card>
    )
  }

  const summary = result.data
  const generatedAt = formatGeneratedAt(summary.generatedAt)
  const usageText = formatUsage(summary.usage)

  return (
    <Card
      size="small"
      title={title}
      className="shadow-xs"
      extra={
        <Popconfirm
          title="Tạo lại bản tóm tắt?"
          description={`Bản mới lấy số liệu mới nhất và dùng một lượt AI của tháng${
            usageText ? ` (đã dùng ${usageText})` : ''
          }.`}
          okText="Làm mới"
          cancelText="Hủy"
          onConfirm={() => void refresh()}
          rootClassName={READ_ONLY_ALLOWED_CLASS}
        >
          <Button size="small" icon={<ReloadOutlined />} loading={refreshing}>
            Làm mới
          </Button>
        </Popconfirm>
      }
    >
      <div className="flex flex-col gap-2 text-sm text-slate-800">
        {summary.bullets.length > 0 ? (
          <ul className="m-0 list-disc space-y-1 pl-5">
            {summary.bullets.map((bullet, index) => (
              <li key={index}>{bullet}</li>
            ))}
          </ul>
        ) : (
          <div className="whitespace-pre-wrap">{summary.summary}</div>
        )}
        {summary.actions.length > 0 && (
          <div>
            <div className="text-xs font-semibold text-slate-600">Gợi ý</div>
            <ul className="m-0 mt-1 list-disc space-y-1 pl-5">
              {summary.actions.map((action, index) => (
                <li key={index}>{action}</li>
              ))}
            </ul>
          </div>
        )}
        <div className="flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-slate-500">
          {generatedAt && <span>Lập {generatedAt}</span>}
          {summary.fromCache && <span>từ bộ nhớ đệm (lưu 30 phút, không tốn lượt AI)</span>}
          {!summary.financialsIncluded && (
            <span>Chưa gồm chỉ số tài chính (chỉ Quản trị hệ thống và Kế toán được xem).</span>
          )}
        </div>
        {refreshError?.key === key && (
          <Text type="danger" className="text-xs">
            {refreshError.message}
          </Text>
        )}
      </div>
    </Card>
  )
}

function SummarySkeleton() {
  return (
    <Card size="small" title="Tóm tắt bằng AI" className="shadow-xs">
      <Skeleton active title={false} paragraph={{ rows: 3 }} />
    </Card>
  )
}
