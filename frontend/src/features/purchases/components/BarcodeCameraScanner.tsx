import { CameraOutlined, CheckCircleOutlined, CloseOutlined } from '@ant-design/icons'
import { Alert, Button, Modal, Space, Typography } from 'antd'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { useEffect, useRef, useState } from 'react'

const { Text } = Typography

interface BarcodeCameraScannerProps {
  open: boolean
  onClose: () => void
  onScan: (decodedText: string) => void
}

export default function BarcodeCameraScanner({
  open,
  onClose,
  onScan,
}: BarcodeCameraScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null)
  const [isScanning, setIsScanning] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [lastScanned, setLastScanned] = useState<string | null>(null)
  const lastScannedTimeRef = useRef<number>(0)

  const elementId = 'html5-qrcode-reader'

  useEffect(() => {
    if (!open) {
      return
    }

    let isMounted = true

    // Khởi tạo Html5Qrcode sau khi Modal DOM đã mount
    const timer = setTimeout(() => {
      setErrorMsg(null)
      setLastScanned(null)
      const container = document.getElementById(elementId)
      if (!container || !isMounted) return

      try {
        const scanner = new Html5Qrcode(elementId, {
          formatsToSupport: [
            Html5QrcodeSupportedFormats.CODE_128,
            Html5QrcodeSupportedFormats.EAN_13,
            Html5QrcodeSupportedFormats.EAN_8,
            Html5QrcodeSupportedFormats.CODE_39,
            Html5QrcodeSupportedFormats.UPC_A,
            Html5QrcodeSupportedFormats.UPC_E,
            Html5QrcodeSupportedFormats.QR_CODE,
          ],
          verbose: false,
        })
        scannerRef.current = scanner

        const config = {
          fps: 15,
          qrbox: { width: 280, height: 160 },
          aspectRatio: 1.777778,
        }

        scanner
          .start(
            { facingMode: 'environment' },
            config,
            (decodedText) => {
              const now = Date.now()
              // Chống quét trùng lặp cùng 1 mã trong vòng 800ms
              if (
                decodedText === lastScanned &&
                now - lastScannedTimeRef.current < 800
              ) {
                return
              }
              lastScannedTimeRef.current = now
              setLastScanned(decodedText)
              onScan(decodedText)
            },
            () => {
              // Bỏ qua lỗi khung hình không có mã
            }
          )
          .then(() => {
            if (isMounted) {
              setIsScanning(true)
            }
          })
          .catch((err) => {
            if (isMounted) {
              console.error('Không thể bật camera quét mã:', err)
              setErrorMsg(
                'Không thể khởi động camera. Vui lòng cấp quyền truy cập Camera cho trình duyệt hoặc sử dụng Súng bắn mã vạch USB.'
              )
            }
          })
      } catch (err) {
        console.error('Lỗi khởi tạo Html5Qrcode:', err)
        setErrorMsg('Trình duyệt không hỗ trợ thư viện camera quét mã.')
      }
    }, 200)

    return () => {
      isMounted = false
      clearTimeout(timer)
      if (scannerRef.current) {
        if (scannerRef.current.isScanning) {
          scannerRef.current
            .stop()
            .then(() => {
              scannerRef.current?.clear()
              scannerRef.current = null
            })
            .catch((err) => {
              console.debug('Lỗi khi dừng camera:', err)
              scannerRef.current = null
            })
        } else {
          scannerRef.current.clear()
          scannerRef.current = null
        }
      }
      setIsScanning(false)
    }
  }, [open, onScan, lastScanned])

  const handleClose = () => {
    if (scannerRef.current && scannerRef.current.isScanning) {
      scannerRef.current
        .stop()
        .then(() => {
          scannerRef.current?.clear()
          scannerRef.current = null
          onClose()
        })
        .catch(() => {
          onClose()
        })
    } else {
      onClose()
    }
  }

  return (
    <Modal
      title={
        <div className="flex items-center gap-2 text-slate-800">
          <CameraOutlined className="text-blue-600 text-lg" />
          <span>Camera Quét Mã Vạch / Barcode (html5-qrcode)</span>
        </div>
      }
      open={open}
      onCancel={handleClose}
      footer={[
        <Button key="close" onClick={handleClose} icon={<CloseOutlined />}>
          Đóng Camera
        </Button>,
      ]}
      width={520}
      destroyOnClose
    >
      <div className="space-y-3 py-2">
        <Alert
          type="info"
          showIcon
          message="Hướng camera vào mã Barcode hoặc QR Code trên sản phẩm để tự động nhận diện và cộng dồn số lượng (< 100ms)."
        />

        {errorMsg && (
          <Alert
            type="error"
            showIcon
            message={errorMsg}
          />
        )}

        {/* Khung video render của html5-qrcode */}
        <div className="relative rounded-lg overflow-hidden border border-slate-300 bg-black min-h-[260px] flex items-center justify-center">
          <div id={elementId} className="w-full" />
          {!isScanning && !errorMsg && (
            <div className="absolute inset-0 flex flex-col items-center justify-center text-white bg-slate-900/80 p-4">
              <CameraOutlined className="text-3xl text-blue-400 animate-pulse mb-2" />
              <Text className="text-white text-sm">Đang kích hoạt camera...</Text>
            </div>
          )}
        </div>

        {lastScanned && (
          <div className="flex items-center justify-between p-2.5 bg-emerald-50 border border-emerald-200 rounded-md">
            <Space>
              <CheckCircleOutlined className="text-emerald-600 text-base" />
              <span className="text-xs text-emerald-800">Mã vừa quét:</span>
              <strong className="text-emerald-900 font-mono text-sm">{lastScanned}</strong>
            </Space>
            <span className="text-xs text-emerald-600 animate-pulse">✓ Đã cộng dồn</span>
          </div>
        )}
      </div>
    </Modal>
  )
}
