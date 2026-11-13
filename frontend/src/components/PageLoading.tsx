import { Spin } from 'antd'

interface PageLoadingProps {
  tip?: string
  fullScreen?: boolean
}

/**
 * Hiệu ứng Spinner tải trang chuẩn Ant Design, hỗ trợ hiển thị toàn màn hình (fullScreen)
 * hoặc cục bộ trong khung nội dung (content area).
 */
export default function PageLoading({
  tip = 'Đang tải dữ liệu...',
  fullScreen = false,
}: PageLoadingProps) {
  if (fullScreen) {
    return (
      <div className="flex h-screen w-screen flex-col items-center justify-center bg-slate-50/95">
        <Spin size="large" />
        <span className="mt-3 text-xs font-normal text-slate-500">{tip}</span>
      </div>
    )
  }

  return (
    <div className="flex min-h-[50vh] w-full flex-col items-center justify-center py-20">
      <Spin size="default" />
      <span className="mt-3 text-xs font-normal text-slate-500">{tip}</span>
    </div>
  )
}
