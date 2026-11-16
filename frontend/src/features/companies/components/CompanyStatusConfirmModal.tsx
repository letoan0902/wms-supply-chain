import { ExclamationCircleOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { Alert, App, Modal } from 'antd'
import { useState } from 'react'
import { updateCompanyStatus } from '../companyApi'
import type { AdminCompanyItem } from '../types'
import { getApiErrorMessage } from '../../../services/api'

interface CompanyStatusConfirmModalProps {
  company: AdminCompanyItem | null
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

export default function CompanyStatusConfirmModal({
  company,
  open,
  onClose,
  onSuccess,
}: CompanyStatusConfirmModalProps) {
  const { message } = App.useApp()
  const [submitting, setSubmitting] = useState(false)

  if (!company) return null

  const isCurrentActive = company.status === 'ACTIVE'
  const targetStatus = isCurrentActive ? 'SUSPENDED' : 'ACTIVE'
  const actionText = isCurrentActive ? 'Tạm khóa doanh nghiệp' : 'Mở khóa kích hoạt lại'

  const handleConfirm = async () => {
    setSubmitting(true)
    try {
      await updateCompanyStatus(company.id, { status: targetStatus })
      message.success(`Đã cập nhật trạng thái doanh nghiệp ${company.name} thành công`)
      onSuccess()
      onClose()
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Không thể thay đổi trạng thái doanh nghiệp. Vui lòng thử lại.')
      message.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-slate-900">
          {isCurrentActive ? (
            <LockOutlined className="text-rose-600" />
          ) : (
            <UnlockOutlined className="text-emerald-600" />
          )}
          <span>{actionText}</span>
        </div>
      }
      open={open}
      onCancel={() => {
        if (!submitting) onClose()
      }}
      onOk={handleConfirm}
      okText={actionText}
      cancelText="Hủy"
      confirmLoading={submitting}
      destroyOnClose
      width={480}
      okButtonProps={{
        danger: isCurrentActive,
        className: isCurrentActive ? 'font-medium' : 'bg-emerald-600 hover:!bg-emerald-500 font-medium',
      }}
    >
      <div className="pt-2">
        <p className="text-xs text-slate-600 mb-3">
          Bạn có chắc chắn muốn {isCurrentActive ? 'tạm khóa' : 'mở khóa kích hoạt lại'} doanh nghiệp{' '}
          <strong className="text-slate-900">{company.name}</strong> (Mã: <code className="text-slate-800">{company.code}</code>)?
        </p>

        <Alert
          type={isCurrentActive ? 'warning' : 'info'}
          showIcon
          icon={<ExclamationCircleOutlined />}
          message={
            <span className="text-xs text-slate-700">
              {isCurrentActive
                ? 'Khi tạm khóa, mọi người dùng thuộc công ty này sẽ bị chặn đăng nhập vào hệ thống ngay lập tức.'
                : 'Khi mở khóa, tài khoản công ty sẽ hoạt động trở lại theo trạng thái gói thuê bao tương ứng.'}
            </span>
          }
          className="bg-slate-50 border-slate-200"
        />
      </div>
    </Modal>
  )
}
