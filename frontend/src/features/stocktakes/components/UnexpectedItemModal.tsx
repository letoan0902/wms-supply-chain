import { Alert, Form, Input, Modal, Select } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { useDebouncedValue } from '../../inventory/useDebouncedValue'
import { fetchProducts } from '../../products/productApi'
import type { Product } from '../../products/types'
import { parseQuantityInput } from '../stocktakeLogic'

interface Props {
  open: boolean
  binLocationCode: string
  /** Giới hạn sản phẩm của đợt (rỗng: mọi sản phẩm) */
  productScope: readonly number[]
  onCancel: () => void
  onSubmit: (value: { product: Product; lotNo: string | null; quantity: number }) => void
}

interface FormValues {
  productId?: number
  lotNo?: string
  quantity?: string
}

/**
 * Ghi hàng có thực tế tại vị trí nhưng không có trong danh sách cần đếm (hàng phát sinh). Máy chủ cộng vào dòng nếu
 * đã có dòng cùng sản phẩm và số lô, ngược lại tạo dòng hàng phát sinh.
 */
export default function UnexpectedItemModal({ open, binLocationCode, productScope, onCancel, onSubmit }: Props) {
  const [form] = Form.useForm<FormValues>()
  const productId = Form.useWatch('productId', form)
  const [search, setSearch] = useState('')
  const keyword = useDebouncedValue(search.trim(), 400)
  const [result, setResult] = useState<{ key: string; items: Product[]; error: string | null } | null>(null)
  const [known, setKnown] = useState<Map<number, Product>>(new Map())

  useEffect(() => {
    if (!open) return
    let ignore = false
    fetchProducts({ keyword: keyword || undefined, active: true, page: 0, size: 20 })
      .then((page) => {
        if (ignore) return
        const items = page.content ?? []
        setResult({ key: keyword, items, error: null })
        setKnown((prev) => {
          const next = new Map(prev)
          items.forEach((p) => next.set(p.id, p))
          return next
        })
      })
      .catch((err) => {
        if (!ignore) setResult({ key: keyword, items: [], error: getApiErrorMessage(err, 'Không tải được hàng hóa') })
      })
    return () => {
      ignore = true
    }
  }, [open, keyword])

  const selected = productId !== undefined ? (known.get(productId) ?? null) : null
  const loading = result?.key !== keyword || search.trim() !== keyword
  const options = useMemo(
    () =>
      (result?.items ?? []).map((p) => {
        const outOfScope = productScope.length > 0 && !productScope.includes(p.id)
        return {
          value: p.id,
          label: `${p.sku} - ${p.name}${outOfScope ? ' (ngoài phạm vi đợt)' : ''}`,
          disabled: outOfScope,
        }
      }),
    [result, productScope],
  )

  const handleOk = async () => {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    if (!selected) return
    const parsed = parseQuantityInput(values.quantity ?? '', false)
    if (!parsed.ok) return
    onSubmit({ product: selected, lotNo: selected.manageByLot ? (values.lotNo?.trim() ?? null) : null, quantity: parsed.value })
  }

  return (
    <Modal
      title={`Thêm hàng ngoài danh sách tại ${binLocationCode}`}
      open={open}
      onCancel={onCancel}
      onOk={() => void handleOk()}
      okText="Ghi số đếm"
      cancelText="Đóng"
      okButtonProps={{ size: 'large', 'data-write-action': 'true' }}
      cancelButtonProps={{ size: 'large' }}
      destroyOnHidden
      width={480}
    >
      <Alert
        className="!mb-3"
        type="info"
        showIcon
        message="Dùng khi thấy hàng tại vị trí này mà danh sách không có. Nếu đã có dòng cùng hàng và số lô, số lượng được cộng vào dòng đó."
      />
      <Form form={form} layout="vertical">
        <Form.Item name="productId" label="Hàng hóa" rules={[{ required: true, message: 'Vui lòng chọn hàng hóa' }]}>
          <Select
            showSearch
            size="large"
            placeholder="Gõ SKU, tên hoặc mã vạch"
            filterOption={false}
            onSearch={setSearch}
            loading={loading}
            notFoundContent={loading ? 'Đang tìm...' : (result?.error ?? 'Không có hàng hóa phù hợp')}
            options={options}
          />
        </Form.Item>
        {selected?.manageByLot && (
          <Form.Item
            name="lotNo"
            label="Số lô"
            rules={[
              { required: true, whitespace: true, message: 'Hàng quản lý theo lô: vui lòng nhập số lô' },
              { max: 50, message: 'Số lô tối đa 50 ký tự' },
            ]}
          >
            <Input size="large" maxLength={50} />
          </Form.Item>
        )}
        <Form.Item
          name="quantity"
          label="Số lượng đếm được"
          rules={[
            {
              validator: (_, value: string | undefined) => {
                const parsed = parseQuantityInput(value ?? '', false)
                return parsed.ok ? Promise.resolve() : Promise.reject(new Error(parsed.error))
              },
            },
          ]}
        >
          <Input size="large" inputMode="decimal" placeholder="Ví dụ 3 hoặc 2,5" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
