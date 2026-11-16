import { Alert, App, Form, InputNumber, Modal, Radio } from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import type { BillingCycle } from '../../subscription/types'
import { approvePlatformCompany, type PlatformCompanySummary } from '../platformApi'
import { CYCLE_LABELS, PLAN_LABELS } from '../platformFormat'
import type { StatusTarget } from './CompanyStatusModal'

/** Số ngày dùng thử mặc định và khoảng cho phép (theo hợp đồng API). */
const DEFAULT_TRIAL_DAYS = 30
const MIN_TRIAL_DAYS = 1
const MAX_TRIAL_DAYS = 90

interface ApproveFormValues {
  plan: 'TRIAL' | 'PRO'
  trialDays?: number
  cycle?: BillingCycle
}

interface Props {
  /** Bản đăng ký đang chờ (PENDING) cần duyệt; null thì đóng */
  target: StatusTarget | null
  onCancel: () => void
  onDone: (updated: PlatformCompanySummary) => void
}

/**
 * Hộp thoại duyệt một bản đăng ký đang chờ: chọn gói Dùng thử (số ngày) hoặc Chuyên Nghiệp (chu kỳ). Duyệt xong doanh
 * nghiệp hoạt động ngay và người đăng ký đăng nhập được.
 */
export default function ApproveCompanyModal({ target, onCancel, onDone }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<ApproveFormValues>()
  const plan = Form.useWatch('plan', form) ?? 'TRIAL'
  const [submitting, setSubmitting] = useState(false)

  const handleOk = async () => {
    if (!target) return
    let values: ApproveFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSubmitting(true)
    try {
      const updated = await approvePlatformCompany(
        target.id,
        values.plan === 'PRO'
          ? { plan: 'PRO', cycle: values.cycle ?? 'MONTHLY' }
          : { plan: 'TRIAL', trialDays: values.trialDays ?? DEFAULT_TRIAL_DAYS },
      )
      message.success(`Đã duyệt doanh nghiệp ${target.name}`)
      form.resetFields()
      onDone(updated)
    } catch (error) {
      message.error(getApiErrorMessage(error, 'Không duyệt được bản đăng ký'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleCancel = () => {
    form.resetFields()
    onCancel()
  }

  return (
    <Modal
      open={target !== null}
      title="Duyệt bản đăng ký"
      okText="Duyệt"
      cancelText="Hủy bỏ"
      confirmLoading={submitting}
      onOk={handleOk}
      onCancel={handleCancel}
      width={480}
      destroyOnClose
    >
      {target && (
        <div className="flex flex-col gap-3 py-1">
          <div className="text-sm text-slate-700">
            Doanh nghiệp <strong className="text-slate-900">{target.name}</strong>{' '}
            <span className="font-mono text-xs text-slate-500">({target.code})</span>
          </div>
          <Alert
            type="info"
            showIcon
            message="Duyệt xong, doanh nghiệp hoạt động ngay với gói đã chọn và người đăng ký đăng nhập được."
            description="Người đăng ký nhận email báo đã duyệt. Thời hạn của gói tính từ lúc duyệt."
          />
          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            initialValues={{ plan: 'TRIAL', trialDays: DEFAULT_TRIAL_DAYS, cycle: 'MONTHLY' }}
          >
            <Form.Item label="Gói" name="plan" className="mb-3">
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: 'TRIAL', label: PLAN_LABELS.TRIAL },
                  { value: 'PRO', label: PLAN_LABELS.PRO },
                ]}
              />
            </Form.Item>
            {plan === 'PRO' ? (
              <>
                <Form.Item label="Chu kỳ" name="cycle" className="mb-3">
                  <Radio.Group
                    options={[
                      { value: 'MONTHLY', label: `${CYCLE_LABELS.MONTHLY} (30 ngày)` },
                      { value: 'YEARLY', label: `${CYCLE_LABELS.YEARLY} (365 ngày)` },
                    ]}
                  />
                </Form.Item>
                <Alert
                  type="warning"
                  showIcon
                  message="Gói Chuyên Nghiệp được cấp không qua thanh toán, không tạo giao dịch nào."
                />
              </>
            ) : (
              <Form.Item
                label="Số ngày dùng thử"
                name="trialDays"
                className="mb-0"
                rules={[
                  { required: true, message: 'Nhập số ngày dùng thử' },
                  {
                    type: 'number',
                    min: MIN_TRIAL_DAYS,
                    max: MAX_TRIAL_DAYS,
                    message: `Số ngày dùng thử từ ${MIN_TRIAL_DAYS} đến ${MAX_TRIAL_DAYS}`,
                  },
                ]}
              >
                <InputNumber min={MIN_TRIAL_DAYS} max={MAX_TRIAL_DAYS} precision={0} addonAfter="ngày" className="w-40" />
              </Form.Item>
            )}
          </Form>
        </div>
      )}
    </Modal>
  )
}
