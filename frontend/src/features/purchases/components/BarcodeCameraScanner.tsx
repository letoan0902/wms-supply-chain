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
  const lastScannedRef = useRef<string | null>(null)
  const lastScannedTimeRef = useRef<number>(0)

  // Luôn gọi hàm onScan mới nhất của component cha mà không phải khởi động lại camera
  const onScanRef = useRef(onScan)
  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  const elementId = 'html5-qrcode-reader'

  // Bật camera khi mở hộp thoại, tắt camera khi đóng; chỉ phụ thuộc vào `open`
  useEffect(() => {
    if (!open) {
      return
    }

    let cancelled = false
    let startPending = false
    let scanner: Html5Qrcode | null = null

    // Dừng và giải phóng đúng phiên bản scanner của lần chạy effect này
    const stopScanner = (instance: Html5Qrcode) => {
      if (scannerRef.current === instance) {
        scannerRef.current = null
      }
      if (instance.isScanning) {
        instance
          .stop()
          .then(() => {
            try {
              instance.clear()
            } catch (err) {
              console.debug('Lỗi khi giải phóng camera:', err)
            }
          })
          .catch((err) => {
            console.debug('Lỗi khi dừng camera:', err)
          })
      } else {
        try {
          instance.clear()
        } catch (err) {
          console.debug('Lỗi khi giải phóng camera:', err)
        }
      }
    }

    // Khởi tạo Html5Qrcode sau khi Modal DOM đã mount
    const timer = setTimeout(() => {
      if (cancelled) return
      setErrorMsg(null)
      setLastScanned(null)
      lastScannedRef.current = null
      lastScannedTimeRef.current = 0
      const container = document.getElementById(elementId)
      if (!container) return

      try {
        const instance = new Html5Qrcode(elementId, {
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
        scanner = instance
        scannerRef.current = instance

        const config = {
          fps: 15,
          qrbox: { width: 280, height: 160 },
          aspectRatio: 1.777778,
        }

        startPending = true
        instance
          .start(
            { facingMode: 'environment' },
            config,
            (decodedText) => {
              if (cancelled) return
              const now = Date.now()
              // Chống quét trùng lặp cùng 1 mã trong vòng 800ms
              if (
                decodedText === lastScannedRef.current &&
                now - lastScannedTimeRef.current < 800
              ) {
                return
              }
              lastScannedRef.current = decodedText
              lastScannedTimeRef.current = now
              setLastScanned(decodedText)
              onScanRef.current(decodedText)
            },
            () => {
              // Bỏ qua lỗi khung hình không có mã
            }
          )
          .then(() => {
            startPending = false
            if (cancelled) {
              // Hộp thoại đã đóng trong lúc chờ cấp quyền camera: tắt camera ngay
              stopScanner(instance)
              return
            }
            setIsScanning(true)
          })
          .catch((err) => {
            startPending = false
            if (cancelled) {
              stopScanner(instance)
              return
            }
            console.error('Không thể bật camera quét mã:', err)
            setErrorMsg(
              'Không thể khởi động camera. Vui lòng cấp quyền truy cập Camera cho trình duyệt hoặc sử dụng Súng bắn mã vạch USB.'
            )
          })
      } catch (err) {
        console.error('Lỗi khởi tạo Html5Qrcode:', err)
        setErrorMsg('Trình duyệt không hỗ trợ thư viện camera quét mã.')
      }
    }, 200)

    return () => {
      cancelled = true
      clearTimeout(timer)
      if (scanner) {
        if (startPending) {
          // start() chưa xong: bỏ tham chiếu, việc tắt camera do nhánh then/catch ở trên đảm nhận
          if (scannerRef.current === scanner) {
            scannerRef.current = null
          }
        } else {
          stopScanner(scanner)
        }
      }
      setIsScanning(false)
    }
  }, [open])

  // Việc tắt camera do phần dọn dẹp của effect đảm nhận khi `open` chuyển sang false
  const handleClose = () => {
    onClose()
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
