import axios from 'axios'
import { App } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { reportErrorMessage } from './reportAccess'
import type { ExportProgress, ExportTask } from './reportExport'
import { formatQuantity } from './reportFormat'

/**
 * Quản lý một lần tải file Excel: chống bấm hai lần, tiến độ khi tạo file trên trình duyệt, hủy giữa chừng
 * (và tự hủy khi rời màn hình), thông báo kết quả hoặc lỗi.
 */
export function useExcelExport() {
  const { message } = App.useApp()
  const [running, setRunning] = useState(false)
  const [progress, setProgress] = useState<ExportProgress | null>(null)
  const controllerRef = useRef<AbortController | null>(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  const run = useCallback(
    async (task: ExportTask) => {
      if (controllerRef.current) return
      const controller = new AbortController()
      controllerRef.current = controller
      setRunning(true)
      setProgress(null)
      try {
        const result = await task(setProgress, controller.signal)
        if (result.source === 'server') {
          message.success(`Đã tải file ${result.fileName}`)
        } else {
          message.success(
            `Đã tạo file ${result.fileName} (${formatQuantity(result.rowCount ?? 0)} dòng) từ dữ liệu báo cáo`,
          )
        }
      } catch (error) {
        if (!axios.isCancel(error)) {
          message.error(reportErrorMessage(error, 'Không tải được file Excel'), 6)
        }
      } finally {
        controllerRef.current = null
        setRunning(false)
        setProgress(null)
      }
    },
    [message],
  )

  const cancel = useCallback(() => {
    controllerRef.current?.abort()
  }, [])

  return { running, progress, run, cancel }
}
