import {
  DownloadOutlined,
  PrinterOutlined,
  QrcodeOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import * as productApi from '../productApi'
import type { BarcodeData, BarcodeType, BatchBarcodeResult, Product } from '../types'
import { getApiErrorMessage } from '../../../services/api'

interface BarcodeModalProps {
  open: boolean
  product: Product | null
  onClose: () => void
}

interface BatchFormValues {
  batchNumber: string
  expirationDate: dayjs.Dayjs
  type: BarcodeType
}

export default function BarcodeModal({ open, product, onClose }: BarcodeModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<BatchFormValues>()

  const [barcodeType, setBarcodeType] = useState<BarcodeType>('CODE_128')
  const [barcodeData, setBarcodeData] = useState<BarcodeData | null>(null)
  const [loading, setLoading] = useState(false)

  // Batch Barcode State
  const [batchData, setBatchData] = useState<BatchBarcodeResult | null>(null)
  const [loadingBatch, setLoadingBatch] = useState(false)

  useEffect(() => {
    if (!open || !product) {
      return
    }
    let ignore = false
    const fetchBarcode = async () => {
      setLoading(true)
      try {
        const data = await productApi.fetchProductBarcode(product.id, {
          type: barcodeType,
          width: barcodeType === 'QR_CODE' ? 220 : 300,
          height: barcodeType === 'QR_CODE' ? 220 : 90,
          includeLabel: true,
        })
        if (!ignore) {
          setBarcodeData(data)
        }
      } catch (err) {
        if (!ignore) {
          message.error(getApiErrorMessage(err, 'Không thể sinh mã vạch cho sản phẩm'))
          setBarcodeData(null)
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }
    void fetchBarcode()
    return () => {
      ignore = true
    }
  }, [open, product, barcodeType, message])

  const handleGenerateBatchBarcode = async (values: BatchFormValues) => {
    if (!product) return
    setLoadingBatch(true)
    try {
      const res = await productApi.generateBatchBarcode(product.id, {
        batchNumber: values.batchNumber,
        expirationDate: values.expirationDate.format('YYYY-MM-DD'),
        type: values.type,
      })
      setBatchData(res)
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể tạo tem nhãn lô hàng'))
    } finally {
      setLoadingBatch(false)
    }
  }

  const handlePrint = (imageSrc: string, title: string) => {
    const printWindow = window.open('', '_blank')
    if (!printWindow) {
      message.error('Trình duyệt đã chặn cửa sổ in')
      return
    }

    printWindow.document.write(`
      <html>
        <head>
          <title>In tem nhãn - ${title}</title>
          <style>
            @page {
              size: 50mm 30mm;
              margin: 2mm;
            }
            body {
              font-family: monospace;
              margin: 0;
              padding: 4px;
              text-align: center;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
            }
            .product-name {
              font-size: 11px;
              font-weight: bold;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              max-width: 48mm;
              margin-bottom: 2px;
            }
            .barcode-img {
              max-width: 46mm;
              max-height: 20mm;
              object-fit: contain;
            }
          </style>
        </head>
        <body onload="window.print(); window.close();">
          <div class="product-name">${product?.name || ''}</div>
          <img class="barcode-img" src="${imageSrc}" alt="barcode" />
        </body>
      </html>
    `)
    printWindow.document.close()
  }

  const handleDownload = (imageSrc: string, filename: string) => {
    const a = document.createElement('a')
    a.href = imageSrc
    a.download = `${filename}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  }

  if (!product) return null

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold">In & Xem trước Tem Mã Vạch (ZXing)</span>
          <Tag color="blue" className="font-mono">{product.sku}</Tag>
        </div>
      }
      open={open}
      onCancel={() => {
        setBatchData(null)
        onClose()
      }}
      footer={[
        <Button
          key="close"
          onClick={() => {
            setBatchData(null)
            onClose()
          }}
        >
          Đóng
        </Button>,
      ]}
      width={680}
      destroyOnClose
    >
      <Tabs
        defaultActiveKey="skuBarcode"
        items={[
          {
            key: 'skuBarcode',
            label: '1. Mã vạch SKU chuẩn',
            children: (
              <div className="flex flex-col items-center py-4">
                <div className="mb-4">
                  <Radio.Group
                    value={barcodeType}
                    onChange={(e) => setBarcodeType(e.target.value)}
                    buttonStyle="solid"
                  >
                    <Radio.Button value="CODE_128">Code 128 (Chuẩn SKU)</Radio.Button>
                    <Radio.Button value="EAN_13" disabled={!product.barcode}>
                      EAN-13 {!product.barcode && '(Chưa có mã vạch số)'}
                    </Radio.Button>
                    <Radio.Button value="QR_CODE">QR Code (2D)</Radio.Button>
                  </Radio.Group>
                </div>

                <div className="w-full max-w-sm p-4 bg-white border border-slate-200 rounded-lg shadow-sm flex flex-col items-center min-h-[160px] justify-center">
                  <div className="text-xs font-semibold text-slate-800 text-center mb-2 max-w-xs truncate">
                    {product.name}
                  </div>

                  {loading ? (
                    <Spin />
                  ) : barcodeData ? (
                    <img
                      src={barcodeData.base64Image}
                      alt="barcode preview"
                      className="max-h-28 object-contain"
                    />
                  ) : (
                    <div className="text-xs text-slate-400">Không có dữ liệu mã vạch</div>
                  )}
                </div>

                {barcodeData && (
                  <Space className="mt-5">
                    <Button
                      type="primary"
                      icon={<PrinterOutlined />}
                      onClick={() => handlePrint(barcodeData.base64Image, product.sku)}
                    >
                      In tem nhiệt (50x30mm)
                    </Button>
                    <Button
                      icon={<DownloadOutlined />}
                      onClick={() => handleDownload(barcodeData.base64Image, product.sku)}
                    >
                      Tải ảnh PNG
                    </Button>
                  </Space>
                )}
              </div>
            ),
          },
          {
            key: 'batchBarcode',
            label: '2. Tem nhãn Lô & Hạn dùng (FR07)',
            children: (
              <div className="py-2">
                <Form
                  form={form}
                  layout="vertical"
                  initialValues={{
                    batchNumber: `LOT-${dayjs().format('YYYYMMDD')}-01`,
                    expirationDate: dayjs().add(1, 'year'),
                    type: 'QR_CODE',
                  }}
                  onFinish={handleGenerateBatchBarcode}
                >
                  <Row gutter={12}>
                    <Col span={10}>
                      <Form.Item
                        name="batchNumber"
                        label="Số lô hàng (Batch/Lot)"
                        rules={[{ required: true, message: 'Nhập số lô' }]}
                      >
                        <Input placeholder="VD: LOT-20261028-01" />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item
                        name="expirationDate"
                        label="Hạn sử dụng (Exp Date)"
                        rules={[{ required: true, message: 'Chọn hạn dùng' }]}
                      >
                        <DatePicker className="w-full" format="DD/MM/YYYY" />
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item name="type" label="Định dạng mã">
                        <Select
                          options={[
                            { label: 'QR Code', value: 'QR_CODE' },
                            { label: 'Code 128', value: 'CODE_128' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                  </Row>

                  <div className="flex justify-end mb-4">
                    <Button type="primary" htmlType="submit" icon={<QrcodeOutlined />} loading={loadingBatch}>
                      Tạo tem nhãn kết hợp
                    </Button>
                  </div>
                </Form>

                {batchData && (
                  <div className="flex flex-col items-center p-4 bg-slate-50 border border-slate-200 rounded-lg">
                    <div className="text-xs text-slate-500 mb-2 font-mono">
                      Nội dung mã hóa: {batchData.encodedContent}
                    </div>
                    <img src={batchData.base64Image} alt="batch barcode" className="max-h-36 object-contain" />
                    <Space className="mt-3">
                      <Button
                        type="primary"
                        icon={<PrinterOutlined />}
                        onClick={() => handlePrint(batchData.base64Image, `${product.sku}-${batchData.batchNumber}`)}
                      >
                        In tem nhãn lô
                      </Button>
                      <Button
                        icon={<DownloadOutlined />}
                        onClick={() =>
                          handleDownload(batchData.base64Image, `${product.sku}-${batchData.batchNumber}`)
                        }
                      >
                        Tải ảnh
                      </Button>
                    </Space>
                  </div>
                )}
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}
