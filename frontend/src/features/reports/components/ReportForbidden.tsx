import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

/** Trang 403 cho vai trò không được xem báo cáo kho (cùng kiểu với các màn hình khác của hệ thống). */
export default function ReportForbidden() {
  const navigate = useNavigate()
  return (
    <div className="p-8 bg-white rounded-xl shadow-xs border border-slate-200">
      <Result
        status="403"
        title="403 - Quyền truy cập bị từ chối"
        subTitle="Báo cáo kho chỉ dành cho Quản trị viên, Kế toán, Quản lý kho và Nhân viên kho. Vui lòng liên hệ quản trị viên nếu bạn cần xem báo cáo."
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            Quay lại Trang chủ
          </Button>
        }
      />
    </div>
  )
}
