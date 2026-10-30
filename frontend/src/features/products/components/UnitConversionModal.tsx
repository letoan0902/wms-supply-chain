import {
  CalculatorOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Table,
  Tabs,
  Tag,
} from 'antd'
import { useEffect, useState } from 'react'
import * as productApi from '../productApi'
import type { BreakdownResult, CreateUnitConversionPayload, Product, UnitConversion, Uom } from '../types'
import { getApiErrorMessage } from '../../../services/api'

interface UnitConversionModalProps {
  open: boolean
  product: Product | null
  uoms: Uom[]
  onClose: () => void
}

interface CreateConversionFormValues {
  fromUomId: number
  toUomId: number
  conversionFactor: number
  barcode?: string
  description?: string
}

export default function UnitConversionModal({
  open,
  product,
  uoms,
  onClose,
}: UnitConversionModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<CreateConversionFormValues>()

  const [conversions, setConversions] = useState<UnitConversion[]>([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Công cụ tính thử (Live Calculator)
  const [calcUomId, setCalcUomId] = useState<number | undefined>(undefined)
  const [calcQuantity, setCalcQuantity] = useState<number>(1)
  const [calcResult, setCalcResult] = useState<{ baseQuantity: number; conversionRate: number } | null>(null)
  const [calcLoading, setCalcLoading] = useState(false)

  // Phân rã tồn kho (Breakdown)
  const [breakdownBaseQty, setBreakdownBaseQty] = useState<number>(50)
  const [breakdownResult, setBreakdownResult] = useState<BreakdownResult | null>(null)
  const [breakdownLoading, setBreakdownLoading] = useState(false)

  const reloadConversions = async (productId: number) => {
    setLoading(true)
    try {
      const data = await productApi.fetchUnitConversions(productId)
      setConversions(data)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tải danh sách quy đổi đơn vị'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!open || !product) return
    let ignore = false
    const load = async () => {
      setLoading(true)
      try {
        const data = await productApi.fetchUnitConversions(product.id)
        if (!ignore) {
          setConversions(data)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể tải danh sách quy đổi đơn vị'))
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }
    void load()
    return () => {
      ignore = true
    }
  }, [open, product, message])

  const handleCreateConversion = async (values: CreateConversionFormValues) => {
    if (!product) return

    setSubmitting(true)
    try {
      const payload: CreateUnitConversionPayload = {
        fromUomId: values.fromUomId,
        toUomId: values.toUomId,
        conversionFactor: values.conversionFactor,
        barcode: values.barcode?.trim() || undefined,
        description: values.description?.trim() || undefined,
      }
      await productApi.createUnitConversion(product.id, payload)
      message.success('Đã thêm quy tắc quy đổi mới!')
      form.resetFields()
      reloadConversions(product.id)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể thêm quy tắc quy đổi'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleDeleteConversion = async (conversionId: number) => {
    if (!product) return
    try {
      await productApi.deleteUnitConversion(product.id, conversionId)
      message.success('Đã xóa quy tắc quy đổi')
      reloadConversions(product.id)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể xóa quy tắc quy đổi'))
    }
  }

  // Chạy thử quy đổi về đơn vị cơ sở
  const handleTestConvertToBase = async () => {
    if (!product || !calcUomId) {
      message.warning('Vui lòng chọn đơn vị tính để thử nghiệm')
      return
    }
    setCalcLoading(true)
    try {
      const res = await productApi.convertToBase(product.id, {
        uomId: calcUomId,
        quantity: calcQuantity || 0,
      })
      setCalcResult(res)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không tìm thấy đường quy đổi về đơn vị cơ sở'))
    } finally {
      setCalcLoading(false)
    }
  }

  // Chạy thử phân rã tồn kho
  const handleTestBreakdown = async () => {
    if (!product) return
    setBreakdownLoading(true)
    try {
      const res = await productApi.fetchBreakdown(product.id, breakdownBaseQty || 0)
      setBreakdownResult(res)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể phân rã tồn kho'))
    } finally {
      setBreakdownLoading(false)
    }
  }

  const columns = [
    {
      title: 'Đơn vị nguồn',
      dataIndex: 'fromUomName',
      key: 'fromUomName',
      render: (text: string, record: UnitConversion) => (
        <span className="font-semibold text-slate-800">
          1 {text} ({record.fromUomCode})
        </span>
      ),
    },
    {
      title: 'Tỷ lệ quy đổi trực tiếp',
      key: 'conversionFactor',
      render: (_: unknown, record: UnitConversion) => (
        <span>
          = <strong className="text-blue-600 font-mono text-base">{record.conversionFactor}</strong>{' '}
          {record.toUomName} ({record.toUomCode})
        </span>
      ),
    },
    {
      title: 'Quy đổi về ĐV cơ sở (Tự động)',
      key: 'factorToBase',
      render: (_: unknown, record: UnitConversion) => {
        if (!record.factorToBase) return <span className="text-slate-400">-</span>
        return (
          <Tag color="cyan" className="font-mono text-sm px-2 py-0.5">
            = {record.factorToBase} {product?.uomName || product?.uomCode}
          </Tag>
        )
      },
    },
    {
      title: 'Mã vạch bao bì',
      dataIndex: 'barcode',
      key: 'barcode',
      render: (text?: string) => (text ? <span className="font-mono text-xs">{text}</span> : <span className="text-slate-400">Không</span>),
    },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (text?: string) => text || '-',
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 80,
      align: 'center' as const,
      render: (_: unknown, record: UnitConversion) => (
        <Popconfirm
          title="Xác nhận xóa quy đổi này?"
          okText="Xóa"
          cancelText="Hủy"
          okButtonProps={{ danger: true }}
          onConfirm={() => handleDeleteConversion(record.id)}
        >
          <Button type="text" danger icon={<DeleteOutlined />} size="small" />
        </Popconfirm>
      ),
    },
  ]

  if (!product) return null

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold">Cấu hình Quy đổi Đơn vị tính Đa cấp</span>
          <Tag color="blue" className="font-mono">{product.sku}</Tag>
        </div>
      }
      open={open}
      onCancel={() => {
        setCalcResult(null)
        setBreakdownResult(null)
        onClose()
      }}
      footer={[
        <Button
          key="close"
          onClick={() => {
            setCalcResult(null)
            setBreakdownResult(null)
            onClose()
          }}
        >
          Đóng
        </Button>,
      ]}
      width={900}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        icon={<InfoCircleOutlined />}
        className="mb-4"
        message={
          <div>
            Đơn vị tính cơ sở (Base UoM) của sản phẩm này là:{' '}
            <strong className="text-blue-700 underline text-sm">
              {product.uomName || product.uomCode}
            </strong>
            . Mọi tồn kho trong WMS đều được quy đổi tự động về đơn vị này để tính toán.
          </div>
        }
      />

      <Tabs
        defaultActiveKey="list"
        items={[
          {
            key: 'list',
            label: `Bảng quy đổi hiện có (${conversions.length})`,
            children: (
              <div className="space-y-4">
                <Table
                  dataSource={conversions}
                  columns={columns}
                  rowKey="id"
                  loading={loading}
                  pagination={false}
                  locale={{ emptyText: 'Chưa có cấu hình quy đổi nào cho sản phẩm này.' }}
                  size="middle"
                  bordered
                />

                <Card size="small" title="Thêm quy tắc quy đổi mới" className="bg-slate-50 border-slate-200">
                  <Form form={form} layout="vertical" onFinish={handleCreateConversion}>
                    <Row gutter={12}>
                      <Col span={7}>
                        <Form.Item
                          name="fromUomId"
                          label="1 Đơn vị nguồn (Đóng gói lớn)"
                          rules={[{ required: true, message: 'Chọn đơn vị nguồn' }]}
                        >
                          <Select
                            placeholder="VD: Thùng, Lốc..."
                            options={uoms.map((u) => ({
                              label: `${u.name} (${u.code})`,
                              value: u.id,
                            }))}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={5}>
                        <Form.Item
                          name="conversionFactor"
                          label="Hệ số quy đổi"
                          rules={[{ required: true, message: 'Nhập hệ số > 0' }]}
                        >
                          <InputNumber min={0.0001} step={1} className="w-full" placeholder="VD: 4 hoặc 24" />
                        </Form.Item>
                      </Col>
                      <Col span={7}>
                        <Form.Item
                          name="toUomId"
                          label="Đơn vị đích (Quy cách con)"
                          rules={[{ required: true, message: 'Chọn đơn vị đích' }]}
                        >
                          <Select
                            placeholder="VD: Lốc hoặc Chai..."
                            options={uoms.map((u) => ({
                              label: `${u.name} (${u.code})`,
                              value: u.id,
                            }))}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={5}>
                        <Form.Item name="barcode" label="Barcode quy cách">
                          <Input placeholder="Mã vạch thùng..." maxLength={64} />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={12} className="items-end">
                      <Col span={19}>
                        <Form.Item name="description" label="Ghi chú quy cách" className="mb-0">
                          <Input placeholder="VD: 1 Thùng = 4 Lốc 6 Lon" maxLength={255} />
                        </Form.Item>
                      </Col>
                      <Col span={5} className="flex justify-end">
                        <Button
                          type="primary"
                          htmlType="submit"
                          icon={<PlusOutlined />}
                          loading={submitting}
                          className="w-full"
                        >
                          Thêm quy đổi
                        </Button>
                      </Col>
                    </Row>
                  </Form>
                </Card>
              </div>
            ),
          },
          {
            key: 'simulator',
            label: 'Công cụ tính thử tồn kho (FR06 Simulator)',
            children: (
              <div className="space-y-6">
                <Card size="small" title="1. Quy đổi số lượng đóng gói về Đơn vị cơ sở">
                  <div className="flex items-center gap-3 mb-3">
                    <span className="text-sm">Khi nhập kho số lượng:</span>
                    <InputNumber
                      min={0}
                      value={calcQuantity}
                      onChange={(v) => setCalcQuantity(v || 0)}
                      className="w-28"
                    />
                    <Select
                      placeholder="Chọn đơn vị đóng gói"
                      className="w-48"
                      value={calcUomId}
                      onChange={(v) => setCalcUomId(v)}
                      options={uoms.map((u) => ({
                        label: `${u.name} (${u.code})`,
                        value: u.id,
                      }))}
                    />
                    <Button
                      type="primary"
                      icon={<CalculatorOutlined />}
                      onClick={handleTestConvertToBase}
                      loading={calcLoading}
                    >
                      Tính toán
                    </Button>
                  </div>

                  {calcResult && (
                    <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-slate-800">
                      Kết quả: <strong>{calcQuantity}</strong> đơn vị vừa chọn ={' '}
                      <span className="text-xl font-bold text-blue-700 font-mono">
                        {calcResult.baseQuantity}
                      </span>{' '}
                      <strong>{product.uomName || product.uomCode}</strong> (Hệ số quy đổi tích lũy: x{calcResult.conversionRate})
                    </div>
                  )}
                </Card>

                <Card size="small" title="2. Phân rã số lượng tồn cơ sở sang các cấp đóng gói (Breakdown)">
                  <div className="flex items-center gap-3 mb-3">
                    <span className="text-sm">Tổng tồn kho cơ sở:</span>
                    <InputNumber
                      min={0}
                      value={breakdownBaseQty}
                      onChange={(v) => setBreakdownBaseQty(v || 0)}
                      className="w-36"
                      addonAfter={product.uomName || product.uomCode}
                    />
                    <Button
                      type="default"
                      icon={<CalculatorOutlined />}
                      onClick={handleTestBreakdown}
                      loading={breakdownLoading}
                    >
                      Phân rã đóng gói
                    </Button>
                  </div>

                  {breakdownResult && (
                    <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-slate-800">
                      <div>
                        Được đóng gói tối ưu thành:{' '}
                        <strong className="text-emerald-700 text-base">
                          {breakdownResult.summary || '0'}
                        </strong>
                      </div>
                      <div className="mt-2 text-xs text-slate-600 flex gap-2">
                        {breakdownResult.breakdown.map((item, idx) => (
                          <Tag key={idx} color="green">
                            {item.quantity} {item.uomName} (x{item.factorToBase})
                          </Tag>
                        ))}
                      </div>
                    </div>
                  )}
                </Card>
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}
