import {
  DeleteOutlined,
  PlusOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
} from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { formatNumber } from '../../../utils/format'
import { fetchProducts } from '../../products/productApi'
import type { Product } from '../../products/types'
import { fetchLocations } from '../../warehouses/warehouseApi'
import type { BinLocation, Warehouse } from '../../warehouses/types'
import { checkStockAtp } from '../transferApi'
import type {
  CreateStockTransferPayload,
  StockTransfer,
  UpdateStockTransferPayload,
} from '../types'

interface TransferFormItem {
  key: string
  productId?: number
  skuCode?: string
  productName?: string
  uom?: string
  sourceBinLocationId?: number
  quantity?: number
  availableAtp?: number
  notes?: string
  loadingAtp?: boolean
}

interface Props {
  open: boolean
  editingTransfer: StockTransfer | null
  warehouses: Warehouse[]
  submitting: boolean
  onClose: () => void
  onSubmitCreate: (payload: CreateStockTransferPayload) => Promise<void>
  onSubmitUpdate: (id: number, payload: UpdateStockTransferPayload) => Promise<void>
}

export default function CreateTransferModal({
  open,
  editingTransfer,
  warehouses,
  submitting,
  onClose,
  onSubmitCreate,
  onSubmitUpdate,
}: Props) {
  const [form] = Form.useForm()
  const sourceWarehouseId = Form.useWatch('sourceWarehouseId', form)

  const [products, setProducts] = useState<Product[]>([])
  const [sourceBins, setSourceBins] = useState<BinLocation[]>([])
  const [items, setItems] = useState<TransferFormItem[]>(() => {
    if (editingTransfer) {
      return (editingTransfer.items || []).map((it, idx) => ({
        key: `item-${it.id || idx}`,
        productId: it.productId,
        skuCode: it.skuCode,
        productName: it.productName,
        uom: it.uom,
        sourceBinLocationId: it.sourceBinLocationId,
        quantity: it.quantity,
        notes: it.notes,
      }))
    }
    return [
      {
        key: `item-${Date.now()}`,
        quantity: 1,
      },
    ]
  })

  // Cập nhật tồn khả dụng (ATP) tại kho nguồn cho một dòng
  const updateRowAtp = useCallback(async (rowKey: string, productId: number, whId: number) => {
    setItems((prev) =>
      prev.map((it) => (it.key === rowKey ? { ...it, loadingAtp: true } : it))
    )
    try {
      const atpData = await checkStockAtp(productId, whId)
      setItems((prev) =>
        prev.map((it) =>
          it.key === rowKey
            ? { ...it, availableAtp: atpData.atpQuantity, loadingAtp: false }
            : it
        )
      )
    } catch {
      setItems((prev) =>
        prev.map((it) => (it.key === rowKey ? { ...it, availableAtp: 0, loadingAtp: false } : it))
      )
    }
  }, [])

  // Nạp danh sách sản phẩm phục vụ chọn SKU
  useEffect(() => {
    let ignore = false
    fetchProducts({ size: 200, active: true })
      .then((res) => {
        if (!ignore) {
          setProducts(res.content)
        }
      })
      .catch((err) => console.error('Lỗi khi nạp danh mục sản phẩm:', err))

    return () => {
      ignore = true
    }
  }, [])

  // Nạp vị trí ô/kệ khi thay đổi kho nguồn
  useEffect(() => {
    let ignore = false
    if (!sourceWarehouseId) {
      return
    }
    fetchLocations({ warehouseId: sourceWarehouseId, active: true })
      .then((bins) => {
        if (!ignore) {
          setSourceBins(bins)
        }
      })
      .catch((err) => console.error('Lỗi khi nạp vị trí kho nguồn:', err))

    return () => {
      ignore = true
    }
  }, [sourceWarehouseId])

  // Cập nhật ATP ban đầu nếu đang chỉnh sửa lệnh điều chuyển
  useEffect(() => {
    if (editingTransfer && editingTransfer.sourceWarehouseId) {
      const whId = editingTransfer.sourceWarehouseId
      ;(editingTransfer.items || []).forEach((row, idx) => {
        if (row.productId) {
          void updateRowAtp(`item-${row.id || idx}`, row.productId, whId)
        }
      })
    }
  }, [editingTransfer, updateRowAtp])

  // Giá trị ban đầu cho form
  const initialValues = useMemo(() => {
    if (editingTransfer) {
      return {
        sourceWarehouseId: editingTransfer.sourceWarehouseId,
        destinationWarehouseId: editingTransfer.destinationWarehouseId,
        expectedDeliveryDate: editingTransfer.expectedDeliveryDate
          ? dayjs(editingTransfer.expectedDeliveryDate)
          : undefined,
        notes: editingTransfer.notes,
      }
    }
    return undefined
  }, [editingTransfer])

  // Xử lý khi thay đổi kho nguồn
  const handleSourceWarehouseChange = (whId: number) => {
    setSourceBins([])
    // Tự động kiểm tra lại ATP cho toàn bộ các dòng đã chọn sản phẩm
    items.forEach((row) => {
      if (row.productId) {
        void updateRowAtp(row.key, row.productId, whId)
      }
    })
  }

  // Thêm một dòng sản phẩm mới
  const handleAddItem = () => {
    setItems((prev) => [
      ...prev,
      {
        key: `item-${Date.now()}`,
        quantity: 1,
      },
    ])
  }

  // Xóa một dòng sản phẩm
  const handleRemoveItem = (key: string) => {
    setItems((prev) => prev.filter((it) => it.key !== key))
  }

  // Khi chọn một sản phẩm trên dòng
  const handleProductSelect = (key: string, productId: number) => {
    const prod = products.find((p) => p.id === productId)
    if (!prod) return

    setItems((prev) =>
      prev.map((it) =>
        it.key === key
          ? {
              ...it,
              productId: prod.id,
              skuCode: prod.sku,
              productName: prod.name,
              uom: prod.uomName || prod.uomCode,
              sourceBinLocationId: undefined,
            }
          : it
      )
    )

    if (sourceWarehouseId) {
      void updateRowAtp(key, prod.id, sourceWarehouseId)
    }
  }

  // Cập nhật giá trị trên dòng
  const handleUpdateItemField = (
    key: string,
    field: keyof TransferFormItem,
    value: string | number | undefined
  ) => {
    setItems((prev) =>
      prev.map((it) => (it.key === key ? { ...it, [field]: value } : it))
    )
  }

  // Tổng hợp số lượng
  const totalItemsCount = items.filter((it) => it.productId && (it.quantity || 0) > 0).length
  const totalQuantitySum = items.reduce((acc, it) => acc + (it.quantity || 0), 0)

  // Submit form
  const handleFinish = async (values: {
    sourceWarehouseId: number
    destinationWarehouseId: number
    expectedDeliveryDate?: dayjs.Dayjs
    notes?: string
  }) => {
    if (values.sourceWarehouseId === values.destinationWarehouseId) {
      Modal.error({
        title: 'Kho không hợp lệ',
        content: 'Kho xuất nguồn và kho nhập đích không được trùng nhau.',
      })
      return
    }

    if (items.length === 0) {
      Modal.error({
        title: 'Chưa chọn mặt hàng',
        content: 'Vui lòng thêm ít nhất một mặt hàng cần điều chuyển.',
      })
      return
    }

    // Kiểm tra tính hợp lệ từng dòng
    for (let i = 0; i < items.length; i++) {
      const it = items[i]
      if (!it.productId || !it.skuCode) {
        Modal.error({
          title: 'Thông tin chưa đầy đủ',
          content: `Dòng số ${i + 1} chưa chọn sản phẩm điều chuyển.`,
        })
        return
      }
      if (!it.quantity || it.quantity <= 0) {
        Modal.error({
          title: 'Số lượng không hợp lệ',
          content: `Số lượng điều chuyển của sản phẩm ${it.skuCode} phải lớn hơn 0.`,
        })
        return
      }
      if (it.availableAtp !== undefined && it.quantity > it.availableAtp) {
        Modal.error({
          title: 'Vượt tồn khả dụng',
          content: `Sản phẩm ${it.skuCode} yêu cầu chuyển ${it.quantity}, nhưng tồn khả dụng tại kho nguồn chỉ còn ${it.availableAtp}.`,
        })
        return
      }
    }

    const payloadItems = items.map((it) => ({
      productId: it.productId!,
      skuCode: it.skuCode!,
      productName: it.productName,
      uom: it.uom,
      sourceBinLocationId: it.sourceBinLocationId,
      quantity: it.quantity!,
      notes: it.notes,
    }))

    if (editingTransfer) {
      const updatePayload: UpdateStockTransferPayload = {
        expectedDeliveryDate: values.expectedDeliveryDate
          ? values.expectedDeliveryDate.toISOString()
          : undefined,
        notes: values.notes,
        items: payloadItems,
      }
      await onSubmitUpdate(editingTransfer.id, updatePayload)
    } else {
      const createPayload: CreateStockTransferPayload = {
        sourceWarehouseId: values.sourceWarehouseId,
        destinationWarehouseId: values.destinationWarehouseId,
        expectedDeliveryDate: values.expectedDeliveryDate
          ? values.expectedDeliveryDate.toISOString()
          : undefined,
        notes: values.notes,
        items: payloadItems,
      }
      await onSubmitCreate(createPayload)
    }
  }

  const columns = [
    {
      title: 'STT',
      width: 55,
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    {
      title: 'Sản phẩm / SKU',
      key: 'product',
      width: 280,
      render: (_: unknown, record: TransferFormItem) => (
        <div>
          <Select
            showSearch
            placeholder="Tìm theo mã SKU hoặc tên sản phẩm"
            value={record.productId}
            style={{ width: '100%' }}
            optionFilterProp="children"
            onChange={(val) => handleProductSelect(record.key, val)}
            filterOption={(input, option) => {
              const label = String(option?.label || '')
              return label.toLowerCase().includes(input.toLowerCase())
            }}
            options={products.map((p) => ({
              value: p.id,
              label: `${p.sku} - ${p.name}`,
            }))}
          />
          {record.uom && (
            <span className="text-xs text-gray-400 mt-1 block">
              Đơn vị: {record.uom}
            </span>
          )}
        </div>
      ),
    },
    {
      title: 'Tồn khả dụng (ATP)',
      key: 'atp',
      width: 140,
      render: (_: unknown, record: TransferFormItem) => {
        if (!sourceWarehouseId) {
          return <span className="text-xs text-gray-400">Chọn kho nguồn</span>
        }
        if (!record.productId) {
          return <span className="text-xs text-gray-400">—</span>
        }
        if (record.loadingAtp) {
          return <span className="text-xs text-blue-500">Đang kiểm tra...</span>
        }
        const atp = record.availableAtp ?? 0
        const isOutOfStock = atp <= 0
        return (
          <div>
            <span
              className={`font-semibold ${
                isOutOfStock ? 'text-red-500' : 'text-green-600'
              }`}
            >
              {formatNumber(atp)}
            </span>{' '}
            <span className="text-xs text-gray-500">{record.uom || ''}</span>
            {isOutOfStock && (
              <span className="text-xs text-red-500 flex items-center gap-1 mt-0.5">
                <WarningOutlined /> Hết hàng
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'Vị trí xuất (Tùy chọn)',
      key: 'bin',
      width: 170,
      render: (_: unknown, record: TransferFormItem) => (
        <Select
          allowClear
          placeholder="Chọn ô/kệ"
          value={record.sourceBinLocationId}
          style={{ width: '100%' }}
          disabled={!sourceWarehouseId}
          onChange={(val) =>
            handleUpdateItemField(record.key, 'sourceBinLocationId', val)
          }
          options={sourceBins.map((b) => ({
            value: b.id,
            label: b.code,
          }))}
        />
      ),
    },
    {
      title: 'Số lượng chuyển',
      key: 'quantity',
      width: 140,
      render: (_: unknown, record: TransferFormItem) => {
        const isOver =
          record.availableAtp !== undefined && (record.quantity || 0) > record.availableAtp
        return (
          <div>
            <InputNumber
              min={0.001}
              precision={3}
              status={isOver ? 'error' : undefined}
              value={record.quantity}
              style={{ width: '100%' }}
              onChange={(val) =>
                handleUpdateItemField(record.key, 'quantity', val ?? 0)
              }
            />
            {isOver && (
              <span className="text-xs text-red-500 block mt-0.5">
                Vượt tồn khả dụng!
              </span>
            )}
          </div>
        )
      },
    },
    {
      title: 'Ghi chú',
      key: 'notes',
      render: (_: unknown, record: TransferFormItem) => (
        <Input
          placeholder="Lý do / quy cách"
          value={record.notes}
          onChange={(e) =>
            handleUpdateItemField(record.key, 'notes', e.target.value)
          }
        />
      ),
    },
    {
      title: '',
      key: 'action',
      width: 50,
      align: 'center' as const,
      render: (_: unknown, record: TransferFormItem) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleRemoveItem(record.key)}
          disabled={items.length <= 1}
        />
      ),
    },
  ]

  return (
    <Modal
      title={
        <div className="text-lg font-semibold text-gray-800">
          {editingTransfer
            ? `Chỉnh sửa lệnh điều chuyển ${editingTransfer.transferNumber}`
            : 'Lập lệnh điều chuyển hàng hóa nội bộ'}
        </div>
      }
      open={open}
      width={1000}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={submitting}>
          Đóng
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={submitting}
          onClick={() => form.submit()}
        >
          {editingTransfer ? 'Lưu thay đổi' : 'Tạo lệnh điều chuyển'}
        </Button>,
      ]}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        onFinish={handleFinish}
        className="mt-4"
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Form.Item
            name="sourceWarehouseId"
            label="Kho xuất nguồn"
            rules={[{ required: true, message: 'Vui lòng chọn kho xuất nguồn' }]}
          >
            <Select
              placeholder="Chọn kho xuất"
              disabled={!!editingTransfer}
              onChange={handleSourceWarehouseChange}
              options={warehouses.map((w) => ({
                value: w.id,
                label: `${w.name} (${w.code})`,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="destinationWarehouseId"
            label="Kho nhập đích"
            rules={[{ required: true, message: 'Vui lòng chọn kho nhập đích' }]}
          >
            <Select
              placeholder="Chọn kho đích"
              disabled={!!editingTransfer}
              options={warehouses
                .filter((w) => w.id !== sourceWarehouseId)
                .map((w) => ({
                  value: w.id,
                  label: `${w.name} (${w.code})`,
                }))}
            />
          </Form.Item>

          <Form.Item
            name="expectedDeliveryDate"
            label="Ngày giao dự kiến"
          >
            <DatePicker
              style={{ width: '100%' }}
              placeholder="Chọn ngày"
              format="DD/MM/YYYY"
            />
          </Form.Item>
        </div>

        <Form.Item name="notes" label="Ghi chú điều chuyển">
          <Input.TextArea
            rows={2}
            placeholder="Lý do điều chuyển, điều phối giữa các chi nhánh hoặc yêu cầu đóng gói vận chuyển..."
          />
        </Form.Item>

        <div className="mt-6 mb-2 flex items-center justify-between">
          <div>
            <span className="font-semibold text-gray-800 text-base">
              Danh sách mặt hàng điều chuyển
            </span>
            <span className="text-xs text-gray-500 ml-2">
              (Kiểm tra tồn khả dụng tự động tại kho xuất)
            </span>
          </div>
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={handleAddItem}
          >
            Thêm sản phẩm
          </Button>
        </div>

        {sourceWarehouseId ? null : (
          <Alert
            message="Vui lòng chọn kho xuất nguồn phía trên để hệ thống tự động kiểm tra tồn kho khả dụng (ATP)."
            type="info"
            showIcon
            className="mb-3"
          />
        )}

        <Table
          rowKey="key"
          columns={columns}
          dataSource={items}
          pagination={false}
          size="small"
          bordered
          className="mb-4"
        />

        <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg border border-gray-200">
          <Space size="large">
            <span>
              Tổng số mặt hàng: <strong>{totalItemsCount}</strong> SKU
            </span>
            <span>
              Tổng số lượng điều chuyển: <strong>{formatNumber(totalQuantitySum)}</strong>
            </span>
          </Space>
          <span className="text-xs text-gray-500">
            Lệnh sẽ được lưu ở trạng thái <strong>Bản nháp</strong> trước khi duyệt xuất kho
          </span>
        </div>
      </Form>
    </Modal>
  )
}
