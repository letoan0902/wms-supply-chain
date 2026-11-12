import { Alert, Input, Modal } from 'antd'
import { useState } from 'react'
import { formatQty, parseQuantityInput } from '../stocktakeLogic'

interface Props {
  open: boolean
  /** Nhãn dòng: vị trí, hàng hóa, số lô */
  title: string
  /** Số đã đếm hiện tại của dòng (của đợt này, không phải tồn hệ thống) */
  currentCounted: number | null
  onCancel: () => void
  onSubmit: (quantity: number) => void
}

/**
 * Nhập tay số đếm cuối cùng của một dòng (mode SET, ghi đè), tối đa 3 chữ số thập phân, cho phép 0.
 * Ô nhập dùng bàn phím số của điện thoại.
 */
export default function SetQuantityModal({ open, title, currentCounted, onCancel, onSubmit }: Props) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    const parsed = parseQuantityInput(text, true)
    if (!parsed.ok) {
      setError(parsed.error)
      return
    }
    onSubmit(parsed.value)
  }

  return (
    <Modal
      title="Nhập số đếm"
      open={open}
      onCancel={onCancel}
      onOk={submit}
      okText="Ghi số đếm"
      cancelText="Đóng"
      okButtonProps={{ size: 'large', 'data-write-action': 'true' }}
      cancelButtonProps={{ size: 'large' }}
      destroyOnHidden
      width={420}
    >
      <div className="flex flex-col gap-3">
        <div className="text-sm font-medium break-words">{title}</div>
        <div className="text-sm text-slate-500">
          Đã đếm trong đợt này: <span className="font-semibold">{currentCounted === null ? 'chưa đếm' : formatQty(currentCounted)}</span>.
          Số nhập sẽ thay thế số đã đếm.
        </div>
        <Input
          autoFocus
          size="large"
          inputMode="decimal"
          placeholder="Ví dụ 12 hoặc 12,5"
          value={text}
          status={error ? 'error' : undefined}
          onChange={(e) => {
            setText(e.target.value)
            setError(null)
          }}
          onPressEnter={submit}
          className="!text-2xl"
        />
        {error && <Alert type="error" showIcon message={error} />}
      </div>
    </Modal>
  )
}
