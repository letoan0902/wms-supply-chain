import {
  BarcodeOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CompassOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  DatePicker,
  Divider,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect } from 'react'
import type { BinLocation } from '../../warehouses/types'

const { Text } = Typography

export interface LotExpiryPutawayFormData {
  batchNumber: string
  mfgDate?: string
  expDate?: string
  locationId?: number
  locationCode?: string
  note?: string
}

export interface LotExpiryPutawayModalItem {
  index: number
  productId: number
  skuCode: string
  productName: string
  uom: string
  acceptedQuantity: number
  batchNumber?: string
  mfgDate?: string
  expDate?: string
  locationId?: number
  locationCode?: string
  note?: string
  manageByLot?: boolean
  manageByExpiration?: boolean
}

interface LotExpiryPutawayModalProps {
  open: boolean
  item: LotExpiryPutawayModalItem | null
  binLocations: BinLocation[]
  receiptDate?: string
  onClose: () => void
  onSave: (index: number, data: LotExpiryPutawayFormData) => void
}

export default function LotExpiryPutawayModal({
  open,
  item,
  binLocations,
  receiptDate,
  onClose,
  onSave,
}: LotExpiryPutawayModalProps) {
  const [form] = Form.useForm<LotExpiryPutawayFormData>()
  const mfgDateWatch = Form.useWatch('mfgDate', form)
  const expDateWatch = Form.useWatch('expDate', form)

  const mfgDateVal = mfgDateWatch ? dayjs(mfgDateWatch) : null
  const expDateVal = expDateWatch ? dayjs(expDateWatch) : null

  const isDateManaged = Boolean(item?.manageByExpiration || item?.manageByLot)
  const effectiveReceiptDate = receiptDate ? dayjs(receiptDate) : dayjs()

  useEffect(() => {
    if (open && item) {
      form.setFieldsValue({
        batchNumber: item.batchNumber || '',
        mfgDate: item.mfgDate,
        expDate: item.expDate,
        locationId: item.locationId,
        locationCode: item.locationCode,
        note: item.note || '',
      })
    } else {
      form.resetFields()
    }
  }, [open, item, form])

  // Gợi ý mã lô tự động
  const handleGenerateBatchNo = () => {
    if (!item) return
    const prefix = 'LOT'
    const dateStr = dayjs().format('YYYYMMDD')
    const cleanSku = item.skuCode.replace(/[^A-Za-z0-9]/g, '').slice(0, 8).toUpperCase()
    const gen = `${prefix}-${dateStr}-${cleanSku}`
    form.setFieldValue('batchNumber', gen)
  }

  // Phím tắt tính nhanh Hạn dùng (+N tháng từ MfgDate hoặc hôm nay)
  const handleQuickExpiry = (months: number) => {
    const base = mfgDateVal || effectiveReceiptDate
    const newExp = base.add(months, 'month')
    form.setFieldValue('expDate', newExp.format('YYYY-MM-DD'))
  }

  // Kiểm tra cảnh báo hết hạn
  const isExpBeforeMfg = Boolean(
    mfgDateVal && expDateVal && expDateVal.isBefore(mfgDateVal, 'day')
  )
  const isExpiredByReceiptDate = Boolean(
    expDateVal && !expDateVal.isAfter(effectiveReceiptDate, 'day')
  )

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (!item) return

      // Tìm tên vị trí tương ứng
      const selectedBin = binLocations.find((b) => b.id === values.locationId)
      const locationCode = selectedBin ? selectedBin.code : values.locationCode

      onSave(item.index, {
        batchNumber: values.batchNumber ? values.batchNumber.trim() : '',
        mfgDate: values.mfgDate,
        expDate: values.expDate,
        locationId: values.locationId,
        locationCode: locationCode,
        note: values.note ? values.note.trim() : '',
      })

      onClose()
    } catch {
      // Bỏ qua lỗi validate form
    }
  }

  return (
    <Modal
      open={open}
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <BarcodeOutlined className="text-emerald-600 text-lg" />
          <span>Nhập Thông Tin Lô Hàng, Hạn Sử Dụng & Vị Trí Xếp Hàng</span>
        </div>
      }
      width={650}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="save"
          type="primary"
          icon={<CheckCircleOutlined />}
          onClick={handleSubmit}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          Áp dụng cho dòng hàng
        </Button>,
      ]}
      destroyOnClose
    >
      {item && (
        <div className="space-y-4 pt-2">
          {/* Header tóm tắt thông tin sản phẩm */}
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs flex flex-wrap items-center justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <Text strong className="text-sm text-slate-800">
                  {item.productName}
                </Text>
                <Tag color="cyan" className="font-mono m-0 text-xs">
                  {item.skuCode}
                </Tag>
              </div>
              <div className="text-slate-500 mt-1">
                Số lượng nhập: <strong className="text-emerald-700">{item.acceptedQuantity}</strong> {item.uom}
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              {isDateManaged ? (
                <Tag color="orange" className="font-semibold m-0 flex items-center gap-1">
                  <ExclamationCircleOutlined /> Quản lý Lô & HSD (Bắt buộc)
                </Tag>
              ) : (
                <Tag color="default" className="m-0">
                  Hàng phổ thông (Không bắt buộc date)
                </Tag>
              )}
            </div>
          </div>

          {/* Cảnh báo nếu sản phẩm có cờ bắt buộc date */}
          {isDateManaged && (
            <Alert
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
              message="Yêu cầu quản lý Hạn sử dụng"
              description="Mặt hàng này được cấu hình bắt buộc theo dõi Hạn sử dụng / Số lô. Vui lòng ghi nhận Số lô và Hạn dùng để hệ thống phục vụ thuật toán xuất kho FEFO và tự động khóa lô hết hạn."
              className="text-xs"
            />
          )}

          <Form form={form} layout="vertical" requiredMark="optional">
            {/* 1. Số Lô (Batch / Lot Number) */}
            <div className="space-y-1">
              <Form.Item
                name="batchNumber"
                label={
                  <div className="flex items-center justify-between w-full">
                    <span>
                      Số lô hàng (Batch / Lot No){' '}
                      {isDateManaged && <span className="text-red-500 font-bold">*</span>}
                    </span>
                    <Button
                      type="link"
                      size="small"
                      icon={<ThunderboltOutlined />}
                      onClick={handleGenerateBatchNo}
                      className="p-0 text-xs text-emerald-600"
                    >
                      Sinh mã lô gợi ý
                    </Button>
                  </div>
                }
                rules={[
                  {
                    required: isDateManaged,
                    message: 'Mặt hàng quản lý date bắt buộc phải nhập Số lô!',
                  },
                  { max: 50, message: 'Số lô tối đa 50 ký tự' },
                ]}
                className="mb-3"
              >
                <Input
                  placeholder="Ví dụ: LOT-202611-COCA01"
                  className="font-mono"
                  allowClear
                />
              </Form.Item>
            </div>

            {/* 2. Ngày SX & Hạn sử dụng */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Form.Item
                name="mfgDate"
                label="Ngày sản xuất (Mfg Date)"
                getValueProps={(val: string | undefined) => ({
                  value: val ? dayjs(val) : null,
                })}
                normalize={(val: dayjs.Dayjs | null) =>
                  val ? val.format('YYYY-MM-DD') : undefined
                }
                className="mb-2"
              >
                <DatePicker
                  format="DD/MM/YYYY"
                  placeholder="Chọn ngày SX"
                  className="w-full"
                  disabledDate={(current) => current && current.isAfter(dayjs(), 'day')}
                />
              </Form.Item>

              <Form.Item
                name="expDate"
                label={
                  <span>
                    Hạn sử dụng (Exp Date){' '}
                    {isDateManaged && <span className="text-red-500 font-bold">*</span>}
                  </span>
                }
                rules={[
                  {
                    required: isDateManaged,
                    message: 'Mặt hàng quản lý date bắt buộc phải nhập Hạn sử dụng!',
                  },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value) return Promise.resolve()
                      const exp = dayjs(value)
                      const mfg = getFieldValue('mfgDate')
                      if (mfg && !exp.isAfter(dayjs(mfg), 'day')) {
                        return Promise.reject(new Error('Hạn sử dụng phải sau Ngày sản xuất!'))
                      }
                      if (effectiveReceiptDate && !exp.isAfter(effectiveReceiptDate, 'day')) {
                        return Promise.reject(
                          new Error(`Hạn sử dụng phải sau Ngày nhập kho (${effectiveReceiptDate.format('DD/MM/YYYY')})!`)
                        )
                      }
                      return Promise.resolve()
                    },
                  }),
                ]}
                getValueProps={(val: string | undefined) => ({
                  value: val ? dayjs(val) : null,
                })}
                normalize={(val: dayjs.Dayjs | null) =>
                  val ? val.format('YYYY-MM-DD') : undefined
                }
                className="mb-2"
              >
                <DatePicker
                  format="DD/MM/YYYY"
                  placeholder="Chọn HSD"
                  className="w-full"
                />
              </Form.Item>
            </div>

            {/* Phím chọn nhanh HSD */}
            <div className="mb-3">
              <div className="text-[11px] text-slate-500 mb-1 flex items-center gap-1">
                <CalendarOutlined /> Chọn nhanh Hạn sử dụng:
              </div>
              <Space wrap size="small">
                <Button size="small" onClick={() => handleQuickExpiry(3)}>
                  +3 tháng
                </Button>
                <Button size="small" onClick={() => handleQuickExpiry(6)}>
                  +6 tháng
                </Button>
                <Button size="small" onClick={() => handleQuickExpiry(12)}>
                  +1 năm
                </Button>
                <Button size="small" onClick={() => handleQuickExpiry(24)}>
                  +2 năm
                </Button>
                <Button size="small" onClick={() => handleQuickExpiry(36)}>
                  +3 năm
                </Button>
              </Space>
            </div>

            {/* Cảnh báo lỗi hạn dùng nếu có */}
            {isExpBeforeMfg && (
              <Alert
                type="error"
                showIcon
                message="Hạn sử dụng không hợp lệ"
                description="Hạn dùng không được trước Ngày sản xuất của sản phẩm!"
                className="text-xs mb-3"
              />
            )}

            {isExpiredByReceiptDate && (
              <Alert
                type="warning"
                showIcon
                message="Cảnh báo: Lô hàng đã hết hạn sử dụng!"
                description={`Hạn dùng (${expDateVal?.format('DD/MM/YYYY')}) nhỏ hơn hoặc bằng ngày nhận kho (${effectiveReceiptDate.format('DD/MM/YYYY')}). Nếu nhập hàng đạt chuẩn (Accepted > 0), hệ thống sẽ từ chối lưu kho!`}
                className="text-xs mb-3"
              />
            )}

            <Divider className="my-3" />

            {/* 3. Vị trí ô xếp hàng */}
            <Form.Item
              name="locationId"
              label={
                <div className="flex items-center gap-1">
                  <CompassOutlined className="text-blue-600" />
                  <span>Vị trí ô xếp hàng lưu cất</span>
                </div>
              }
              className="mb-3"
            >
              <Select
                placeholder="-- Chọn vị trí ô/kệ lưu trữ hàng hóa --"
                allowClear
                showSearch
                filterOption={(input, opt) =>
                  String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
                options={binLocations
                  .filter((b) => b.active)
                  .map((b) => ({
                    value: b.id,
                    label: `[${b.code}] Khu ${b.zone || '-'} / Dãy ${b.rack || '-'} / Ô ${b.bin || '-'} (${b.status || 'AVAILABLE'})`,
                  }))}
                className="w-full"
              />
            </Form.Item>

            {/* 4. Ghi chú */}
            <Form.Item name="note" label="Ghi chú thêm cho dòng hàng này" className="mb-0">
              <Input.TextArea
                rows={2}
                placeholder="Ghi chú về tình trạng bao bì, nhiệt độ bảo quản, vị trí phụ..."
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
