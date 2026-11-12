import { PrinterOutlined } from '@ant-design/icons'
import { Button, Modal, Spin } from 'antd'
import type { BinBarcodeData } from '../types'

interface Props {
  open: boolean
  barcodeData: BinBarcodeData | null
  loading: boolean
  onClose: () => void
}

export default function BarcodePrintModal({
  open,
  barcodeData,
  loading,
  onClose,
}: Props) {
  const handlePrint = () => {
    window.print()
  }

  return (
    <Modal
      title="In tem mã vạch vị trí"
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="print"
          type="primary"
          icon={<PrinterOutlined />}
          onClick={handlePrint}
          disabled={!barcodeData}
        >
          In tem nhãn
        </Button>,
      ]}
      width={440}
      destroyOnClose
    >
      <div className="py-2">
        {loading || !barcodeData ? (
          <div className="flex justify-center items-center py-12">
            <Spin tip="Đang nạp mã vạch Code 128..." />
          </div>
        ) : (
          <div>
            <div className="text-xs text-slate-500 mb-3 text-center">
              Khổ in chuẩn máy in nhiệt: <strong className="text-slate-700">50 x 30 mm</strong> (Code 128)
            </div>

            {/* Khung hiển thị tem nhãn thực tế */}
            <div
              id="printable-barcode-label"
              className="border border-slate-300 p-4 rounded-lg bg-white mx-auto shadow-2xs flex flex-col items-center justify-between text-center select-none"
              style={{ width: '320px', minHeight: '180px' }}
            >
              {/* Tiêu đề kho và khu vực */}
              <div className="w-full border-b border-slate-200 pb-1.5 mb-2 flex justify-between items-center text-xs">
                <span className="font-semibold text-slate-900 tracking-wider">
                  {barcodeData.warehouseCode}
                </span>
                <span className="bg-slate-100 px-1.5 py-0.5 rounded text-[11px] font-medium text-slate-700">
                  KHU: {barcodeData.zone}
                </span>
              </div>

              {/* Tọa độ chi tiết Kệ - Ô */}
              <div className="text-sm font-semibold text-slate-800 tracking-wide mb-1">
                KỆ {barcodeData.rack} — Ô {barcodeData.bin}
              </div>

              {/* Ảnh mã vạch Code 128 sinh từ backend */}
              <div className="my-1 flex justify-center items-center w-full">
                <img
                  src={barcodeData.barcodeBase64}
                  alt={barcodeData.code}
                  className="max-w-full h-auto object-contain"
                />
              </div>

              {/* Chân tem */}
              <div className="text-[10px] text-slate-400 w-full pt-1.5 border-t border-slate-100 mt-1 flex justify-between">
                <span>WMS SUPPLY CHAIN</span>
                <span>BIN-ID: #{barcodeData.locationId}</span>
              </div>
            </div>

            {/* CSS chuyên dụng khi in máy in nhiệt */}
            <style>{`
              @media print {
                body * {
                  visibility: hidden;
                }
                #printable-barcode-label, #printable-barcode-label * {
                  visibility: visible;
                }
                #printable-barcode-label {
                  position: fixed;
                  left: 0;
                  top: 0;
                  width: 50mm !important;
                  height: 30mm !important;
                  border: none !important;
                  box-shadow: none !important;
                  padding: 2mm !important;
                  margin: 0 !important;
                }
              }
            `}</style>
          </div>
        )}
      </div>
    </Modal>
  )
}

