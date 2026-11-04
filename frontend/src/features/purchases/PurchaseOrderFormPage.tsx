import { ArrowLeftOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import PurchaseOrderForm from './components/PurchaseOrderForm'
import type { PurchaseOrder } from './types'

export default function PurchaseOrderFormPage() {
  const navigate = useNavigate()

  const handleSuccess = (createdOrder: PurchaseOrder) => {
    // Điều hướng về trang danh sách mua hàng sau khi tạo đơn thành công
    navigate('/purchases', {
      state: { createdPoNumber: createdOrder.poNumber },
    })
  }

  const handleCancel = () => {
    navigate('/purchases')
  }

  return (
    <div className="space-y-4">
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={handleCancel}
        className="!p-0 text-slate-600 hover:text-blue-600 flex items-center gap-1"
      >
        Quay lại Danh sách Đơn mua hàng
      </Button>

      <PurchaseOrderForm onSuccess={handleSuccess} onCancel={handleCancel} />
    </div>
  )
}
