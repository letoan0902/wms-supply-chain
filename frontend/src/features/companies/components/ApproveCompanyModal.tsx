import { CheckCircleOutlined, InfoCircleOutlined } from '@ant-design/icons'
import { Alert, App, Form, Input, InputNumber, Modal, Radio, Select } from 'antd'
import { useState } from 'react'
import { approveCompany } from '../companyApi'
import type { AdminCompanyItem, ApproveCompanyPayload, BillingCycle, SubscriptionPlan } from '../types'
import { getApiErrorMessage } from '../../../services/api'

interface ApproveCompanyModalProps {
  company: AdminCompanyItem | null
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

interface ApproveFormValues {
  plan: SubscriptionPlan
  trialDays: number
  cycle?: BillingCycle
  note?: string
}

export default function ApproveCompanyModal({
  company,
  open,
  onClose,
  onSuccess,
}: ApproveCompanyModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<ApproveFormValues>()
  const [submitting, setSubmitting] = useState(false)
  const [selectedPlan, setSelectedPlan] = useState<SubscriptionPlan>('TRIAL')

  const handleFinish = async (values: ApproveFormValues) => {
    if (!company) return
    setSubmitting(true)
    try {
      const payload: ApproveCompanyPayload = {
        plan: values.plan,
        trialDays: values.plan === 'TRIAL' ? values.trialDays : undefined,
        cycle: values.plan === 'PRO' ? values.cycle || 'MONTHLY' : undefined,
        note: values.note?.trim() || undefined,
      }
      await approveCompany(company.id, payload)
      message.success(`Đã phê duyệt và kích hoạt doanh nghiệp ${company.name}`)
      form.resetFields()
      onSuccess()
      onClose()
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Không thể phê duyệt doanh nghiệp. Vui lòng thử lại.')
      message.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-base font-semibold text-slate-900">
          <CheckCircleOutlined className="text-emerald-600" />
          <span>Phê duyệt đơn đăng ký doanh nghiệp</span>
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
      okText="Xác nhận phê duyệt"
      cancelText="Hủy"
      confirmLoading={submitting}
      destroyOnClose
      width={560}
      okButtonProps={{
        className: 'bg-emerald-600 hover:!bg-emerald-500 font-medium',
      }}
    >
      {company && (
        <div className="pt-2">
          {/* Box tóm tắt thông tin công ty */}
          <div className="rounded-lg bg-slate-50 border border-slate-200/80 p-3.5 mb-4 text-xs space-y-1.5">
            <div className="flex justify-between">
              <span className="text-slate-500">Mã doanh nghiệp:</span>
              <span className="font-mono font-medium text-slate-800">{company.code}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Tên doanh nghiệp:</span>
              <span className="font-semibold text-slate-800">{company.name}</span>
            </div>
            {company.taxCode && (
              <div className="flex justify-between">
                <span className="text-slate-500">Mã số thuế:</span>
                <span className="font-mono text-slate-800">{company.taxCode}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-slate-500">Tài khoản đại diện:</span>
              <span className="font-mono text-slate-800">
                {company.adminUser?.username || '—'} ({company.adminUser?.email || '—'})
              </span>
            </div>
          </div>

          <Alert
            type="info"
            showIcon
            icon={<InfoCircleOutlined className="text-blue-500" />}
            message={
              <span className="text-xs text-slate-700">
                Khi phê duyệt, doanh nghiệp sẽ chuyển sang <strong>Đang hoạt động</strong>, tài khoản quản trị được mở khóa và hệ thống tự động gửi email thông báo bàn giao tài khoản.
              </span>
            }
            className="mb-4 bg-blue-50/60 border-blue-200/70"
          />

          <Form
            form={form}
            layout="vertical"
            initialValues={{
              plan: 'TRIAL',
              trialDays: 30,
              cycle: 'MONTHLY',
            }}
            onFinish={handleFinish}
            requiredMark="optional"
          >
            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Gói cấp quyền dịch vụ</span>}
              name="plan"
              rules={[{ required: true, message: 'Vui lòng chọn gói' }]}
            >
              <Radio.Group
                className="w-full grid grid-cols-2 gap-3"
                onChange={(e) => setSelectedPlan(e.target.value)}
              >
                <Radio.Button
                  value="TRIAL"
                  className="!h-auto !py-2.5 !px-3 !rounded-lg text-left"
                >
                  <div className="font-medium text-xs text-slate-900">Dùng thử 30 ngày (TRIAL)</div>
                  <div className="text-[11px] text-slate-500 mt-0.5">Trải nghiệm đầy đủ tính năng Pro</div>
                </Radio.Button>
                <Radio.Button
                  value="PRO"
                  className="!h-auto !py-2.5 !px-3 !rounded-lg text-left"
                >
                  <div className="font-medium text-xs text-slate-900">Chuyên nghiệp (PRO)</div>
                  <div className="text-[11px] text-slate-500 mt-0.5">Cấp thẳng gói thương mại</div>
                </Radio.Button>
              </Radio.Group>
            </Form.Item>

            {selectedPlan === 'TRIAL' ? (
              <Form.Item
                label={<span className="text-xs font-semibold text-slate-700">Thời hạn dùng thử (ngày)</span>}
                name="trialDays"
                rules={[
                  { required: true, message: 'Vui lòng nhập số ngày' },
                  { type: 'number', min: 1, max: 365, message: 'Số ngày từ 1 đến 365' },
                ]}
              >
                <InputNumber
                  min={1}
                  max={365}
                  className="w-full !rounded-lg text-sm"
                  addonAfter="ngày"
                />
              </Form.Item>
            ) : (
              <Form.Item
                label={<span className="text-xs font-semibold text-slate-700">Chu kỳ thanh toán</span>}
                name="cycle"
                rules={[{ required: true, message: 'Vui lòng chọn chu kỳ' }]}
              >
                <Select
                  className="w-full !rounded-lg text-sm"
                  options={[
                    { label: 'Hàng tháng (Monthly)', value: 'MONTHLY' },
                    { label: 'Hàng năm (Yearly) - Tiết kiệm 20%', value: 'YEARLY' },
                  ]}
                />
              </Form.Item>
            )}

            <Form.Item
              label={<span className="text-xs font-semibold text-slate-700">Ghi chú phê duyệt (nội bộ)</span>}
              name="note"
            >
              <Input.TextArea
                rows={2}
                placeholder="Ví dụ: Đã liên hệ xác minh qua số điện thoại đại diện"
                className="!rounded-lg text-xs"
                maxLength={255}
                showCount
              />
            </Form.Item>
          </Form>
        </div>
      )}
    </Modal>
  )
}
