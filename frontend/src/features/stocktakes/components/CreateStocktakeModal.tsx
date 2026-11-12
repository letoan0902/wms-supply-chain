import { Alert, App, DatePicker, Form, Input, Modal, Radio, Select } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../services/api'
import { fetchLocations, fetchZones } from '../../warehouses/warehouseApi'
import type { BinLocation, Warehouse } from '../../warehouses/types'
import { createStocktake } from '../stocktakeApi'
import { SCOPE_LABELS } from '../stocktakeLogic'
import type { StocktakeScopeType, StocktakeSession } from '../types'
import ActionErrorDetails from './ActionErrorDetails'
import ProductMultiSelect, { type ProductOptionItem } from './ProductMultiSelect'

interface Props {
  open: boolean
  /** Kho người dùng được lập đợt kiểm kê (đã lọc theo quyền) */
  warehouses: Warehouse[]
  onClose: () => void
  onCreated: (session: StocktakeSession) => void
}

interface FormValues {
  warehouseId?: number
  scopeType: StocktakeScopeType
  zones?: string[]
  binLocationIds?: number[]
  plannedDate?: Dayjs
  notes?: string
}

/** Giới hạn của CreateStocktakeRequest. */
const MAX_ZONES = 50
const MAX_LOCATIONS = 1000
const MAX_PRODUCTS = 200

/** Lập đợt kiểm kê ở trạng thái Mới lập: kho, phạm vi, giới hạn hàng hóa, ngày dự kiến, ghi chú. */
export default function CreateStocktakeModal({ open, warehouses, onClose, onCreated }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const warehouseId = Form.useWatch('warehouseId', form)
  const scopeType = Form.useWatch('scopeType', form)
  const [products, setProducts] = useState<ProductOptionItem[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<unknown>(null)

  const [zoneState, setZoneState] = useState<{ warehouseId: number; zones: string[]; error: string | null } | null>(null)
  const [locationState, setLocationState] = useState<{
    warehouseId: number
    items: BinLocation[]
    error: string | null
  } | null>(null)

  // Danh sách khu và vị trí của kho đã chọn (chỉ tải khi phạm vi cần)
  useEffect(() => {
    if (!open || !warehouseId || scopeType !== 'ZONES') return
    let ignore = false
    fetchZones(warehouseId)
      .then((zones) => {
        if (!ignore) setZoneState({ warehouseId, zones: zones ?? [], error: null })
      })
      .catch((err) => {
        if (!ignore) setZoneState({ warehouseId, zones: [], error: getApiErrorMessage(err, 'Không tải được danh sách khu') })
      })
    return () => {
      ignore = true
    }
  }, [open, warehouseId, scopeType])

  useEffect(() => {
    if (!open || !warehouseId || scopeType !== 'LOCATIONS') return
    let ignore = false
    fetchLocations({ warehouseId })
      .then((items) => {
        if (!ignore) setLocationState({ warehouseId, items: items ?? [], error: null })
      })
      .catch((err) => {
        if (!ignore) {
          setLocationState({ warehouseId, items: [], error: getApiErrorMessage(err, 'Không tải được danh sách vị trí') })
        }
      })
    return () => {
      ignore = true
    }
  }, [open, warehouseId, scopeType])

  const zones = zoneState && zoneState.warehouseId === warehouseId ? zoneState : null
  const locations = locationState && locationState.warehouseId === warehouseId ? locationState : null

  const handleSubmit = async () => {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    if (!values.warehouseId || !values.plannedDate) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await createStocktake({
        warehouseId: values.warehouseId,
        scopeType: values.scopeType,
        zones: values.scopeType === 'ZONES' ? values.zones : undefined,
        binLocationIds: values.scopeType === 'LOCATIONS' ? values.binLocationIds : undefined,
        productIds: products.length > 0 ? products.map((p) => p.id) : undefined,
        plannedDate: values.plannedDate.format('YYYY-MM-DD'),
        notes: values.notes?.trim() || undefined,
      })
      message.success(`Đã lập đợt kiểm kê ${created.code}`)
      form.resetFields()
      setProducts([])
      onCreated(created)
    } catch (err) {
      setSubmitError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="Tạo đợt kiểm kê"
      open={open}
      onCancel={() => {
        if (!submitting) onClose()
      }}
      onOk={() => void handleSubmit()}
      okText="Tạo đợt kiểm kê"
      cancelText="Đóng"
      confirmLoading={submitting}
      okButtonProps={{ 'data-write-action': 'true' }}
      destroyOnHidden
      width={640}
    >
      <Form<FormValues>
        form={form}
        layout="vertical"
        initialValues={{
          scopeType: 'WAREHOUSE',
          plannedDate: dayjs(),
          warehouseId: warehouses.length === 1 ? warehouses[0].id : undefined,
        }}
        onValuesChange={(changed) => {
          if ('warehouseId' in changed) form.setFieldsValue({ zones: [], binLocationIds: [] })
          setSubmitError(null)
        }}
      >
        {warehouses.length === 0 && (
          <Alert
            className="!mb-4"
            type="warning"
            showIcon
            message="Bạn chưa phụ trách kho nào đang hoạt động nên chưa lập được đợt kiểm kê."
          />
        )}
        <Form.Item name="warehouseId" label="Kho kiểm kê" rules={[{ required: true, message: 'Vui lòng chọn kho' }]}>
          <Select
            placeholder="Chọn kho"
            options={warehouses.map((w) => ({ value: w.id, label: `${w.name} (${w.code})` }))}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>

        <Form.Item name="scopeType" label="Phạm vi" rules={[{ required: true }]}>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            options={(Object.keys(SCOPE_LABELS) as StocktakeScopeType[]).map((key) => ({
              value: key,
              label: key === 'LOCATIONS' ? 'Theo vị trí chọn' : SCOPE_LABELS[key],
            }))}
          />
        </Form.Item>

        {scopeType === 'ZONES' && (
          <Form.Item
            name="zones"
            label="Khu"
            extra="Vị trí của các khu được chốt khi bắt đầu kiểm kê."
            validateStatus={zones?.error ? 'error' : undefined}
            help={zones?.error ?? undefined}
            rules={[
              { required: true, type: 'array', min: 1, message: 'Vui lòng chọn ít nhất một khu' },
              { type: 'array', max: MAX_ZONES, message: `Tối đa ${MAX_ZONES} khu trong một đợt` },
            ]}
          >
            <Select
              mode="multiple"
              placeholder={warehouseId ? 'Chọn khu' : 'Chọn kho trước'}
              disabled={!warehouseId}
              loading={Boolean(warehouseId) && !zones}
              options={(zones?.zones ?? []).map((z) => ({ value: z, label: z }))}
            />
          </Form.Item>
        )}

        {scopeType === 'LOCATIONS' && (
          <Form.Item
            name="binLocationIds"
            label="Vị trí ô/kệ"
            validateStatus={locations?.error ? 'error' : undefined}
            help={locations?.error ?? undefined}
            rules={[
              { required: true, type: 'array', min: 1, message: 'Vui lòng chọn ít nhất một vị trí' },
              { type: 'array', max: MAX_LOCATIONS, message: `Tối đa ${MAX_LOCATIONS} vị trí trong một đợt` },
            ]}
          >
            <Select
              mode="multiple"
              placeholder={warehouseId ? 'Gõ mã vị trí để tìm' : 'Chọn kho trước'}
              disabled={!warehouseId}
              loading={Boolean(warehouseId) && !locations}
              optionFilterProp="label"
              options={(locations?.items ?? []).map((l) => ({
                value: l.id,
                label: l.active ? l.code : `${l.code} (ngừng dùng)`,
              }))}
            />
          </Form.Item>
        )}

        <Form.Item label="Giới hạn hàng hóa (không bắt buộc)" extra={`Tối đa ${MAX_PRODUCTS} hàng hóa.`}>
          <ProductMultiSelect value={products} onChange={setProducts} max={MAX_PRODUCTS} />
        </Form.Item>

        <Form.Item
          name="plannedDate"
          label="Ngày dự kiến kiểm kê"
          rules={[{ required: true, message: 'Vui lòng chọn ngày dự kiến' }]}
        >
          <DatePicker className="w-full" format="DD/MM/YYYY" />
        </Form.Item>

        <Form.Item name="notes" label="Ghi chú" rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}>
          <Input.TextArea rows={2} maxLength={500} showCount placeholder="Ví dụ: Kiểm kê cuối tháng" />
        </Form.Item>

        {submitError !== null && (
          <Alert
            type="error"
            showIcon
            message="Không lập được đợt kiểm kê"
            description={<ActionErrorDetails error={submitError} fallback="Không lập được đợt kiểm kê" />}
          />
        )}
      </Form>
    </Modal>
  )
}
