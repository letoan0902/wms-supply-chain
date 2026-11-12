import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

interface ForbiddenResultProps {
  /** Giải thích vai trò nào được dùng màn hình này */
  description: string
}

/**
 * Trang 403 cho màn hình mà vai trò hiện tại không được đọc (menu vẫn giữ mọi mục, xem MainLayout).
 * Màn hình hiển thị trang này thay vì gọi API chắc chắn bị backend từ chối.
 */
export default function ForbiddenResult({ description }: ForbiddenResultProps) {
  const navigate = useNavigate()
  return (
    <div className="p-8 bg-white rounded-xl shadow-xs border border-slate-200">
      <Result
        status="403"
        title="403 - Quyền truy cập bị từ chối"
        subTitle={`${description} Vui lòng liên hệ quản trị viên nếu bạn cần được cấp quyền.`}
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            Quay lại Trang chủ
          </Button>
        }
      />
    </div>
  )
}
