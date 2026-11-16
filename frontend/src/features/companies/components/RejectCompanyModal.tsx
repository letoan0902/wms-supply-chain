import { CloseCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { Alert, App, Form, Input, Modal } from 'antd'
import { useState } from 'react'
import { rejectCompany } from '../companyApi'
import type { AdminCompanyItem, RejectCompanyPayload } from '../types'
import { getApiErrorMessage } from '../../../services/api'

interface RejectCompanyModalProps {
  company: AdminCompanyItem | null
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

interface RejectFormValues {
  reason: string
}

export default function RejectCompanyModal({
  company,
  open,
  onClose,
  onSuccess,
}: RejectCompanyModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<RejectFormValues>()
  const [submitting, setSubmitting] = useState(false)

  const handleFinish = async (values: RejectFormValues) => {
    if (!company) return
    setSubmitting(true)
    try {
      const payload: RejectCompanyPayload = {
        reason: values.reason.trim(),
      }
      await rejectCompany(company.id, payload)
      message.success(`Đã từ chối yêu cầu đăng ký của ${company.name}`)
      form.resetFields()
      onSuccess()
      onClose()
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Không thể từ chối yêu cầu đăng ký. Vui lòng thử lại.')
      message.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-slate-900">
          <CloseCircleOutlined className="text-rose-600" />
          <span>Từ chối yêu cầu đăng ký doanh nghiệp</span>
        </div>
      }
      open={open}
      onCancel={() => {
        if (!submitting) {
          form.resetFields()
          onClose()
        }
      }}
      onOk={() => form.submit()}
      okText="Xác nhận từ chối"
      cancelText="Hủy"
      confirmLoading={submitting}
      destroyOnClose
      width={520}
      okButtonProps={{
        danger: true,
        className: 'font-medium',
      }}
    >
      {company && (
        <div className="pt-2">
          <div className="rounded-lg bg-slate-50 border border-slate-200/80 p-3.5 mb-4 text-xs space-y-1.5">
            <div className="flex justify-between">
              <span className="text-slate-500">Tên doanh nghiệp:</span>
              <span className="font-semibold text-slate-800">{company.name}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Mã doanh nghiệp:</span>
              <span className="font-mono text-slate-800">{company.code}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Email đại diện:</span>
              <span className="font-medium text-slate-800">{company.adminUser?.email || '—'}</span>
            </div>
          </div>

          <Alert
            type="warning"
            showIcon
            icon={<ExclamationCircleOutlined className="text-amber-600" />}
            message={
              <span className="text-xs text-slate-700">
                Doanh nghiệp sẽ chuyển sang trạng thái <strong>Tạm khóa (SUSPENDED)</strong>. Nội dung lý do từ chối sẽ được gửi trực tiếp đến email người đăng ký.
              </span>
            }
            className="mb-4 bg-amber-50/60 border-amber-200/70"
          />

          <Form
            form={form}
            layout="vertical"
            onFinish={handleFinish}
          >
            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Lý do từ chối</span>}
              name="reason"
              rules={[
                { required: true, message: 'Vui lòng nhập lý do từ chối' },
                { min: 5, message: 'Lý do từ chối tối thiểu 5 ký tự' },
              ]}
            >
              <Input.TextArea
                rows={3}
                placeholder="Ví dụ: Thông tin doanh nghiệp hoặc mã số thuế không trùng khớp với cơ sở dữ liệu quốc gia"
                className="!rounded-lg text-xs"
                maxLength={500}
                showCount
              />
            </Form.Item>
          </Form>
        </div>
      )}
    </Modal>
  )
}
