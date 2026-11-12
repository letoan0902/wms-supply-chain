import { Alert, Button } from 'antd'
import { describeReportError } from '../reportAccess'

interface Props {
  error: unknown
  fallback?: string
  onRetry?: () => void
}

/** Khung báo lỗi API báo cáo, diễn giải theo mã HTTP (400, 401, 403, 404, 503...) kèm nút thử lại. */
export default function ReportErrorAlert({ error, fallback, onRetry }: Props) {
  const info = describeReportError(error, fallback)
  return (
    <Alert
      type={info.status === 404 ? 'warning' : 'error'}
      showIcon
      message={info.title}
      description={info.description}
      action={
        onRetry && info.status !== 403 && info.status !== 401 ? (
          <Button size="small" onClick={onRetry}>
            Thử lại
          </Button>
        ) : undefined
      }
    />
  )
}
