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
      <div className="flex h-screen w-screen flex-col items-center justify-center bg-slate-50/90 backdrop-blur-xs">
        <Spin size="large" />
        <span className="mt-4 text-sm font-medium text-slate-500 animate-pulse">{tip}</span>
      </div>
    )
  }

  return (
    <div className="flex min-h-[50vh] w-full flex-col items-center justify-center py-20">
      <Spin size="large" />
      <span className="mt-3 text-xs font-medium text-slate-400">{tip}</span>
    </div>
  )
}
