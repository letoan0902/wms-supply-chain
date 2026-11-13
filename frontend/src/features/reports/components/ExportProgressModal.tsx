import { Button, Modal, Progress, Typography } from 'antd'
import type { ExportProgress } from '../reportExport'
import { formatQuantity } from '../reportFormat'

interface Props {
  open: boolean
  progress: ExportProgress | null
  onCancel: () => void
}

/** Hộp thoại tiến độ tải file Excel; khi tạo file trên trình duyệt hiển thị số dòng đã tải. */
export default function ExportProgressModal({ open, progress, onCancel }: Props) {
  const percent = progress && progress.total > 0 ? Math.min(100, Math.round((progress.loaded / progress.total) * 100)) : 0
  return (
    <Modal
      open={open}
      title="Đang chuẩn bị file Excel"
      closable={false}
      maskClosable={false}
      footer={<Button onClick={onCancel}>Hủy</Button>}
      destroyOnHidden
    >
      {progress ? (
        <>
          <Progress percent={percent} status="active" />
          <Typography.Text type="secondary">
            Máy chủ chưa hỗ trợ xuất file, đang tải dữ liệu để tạo file trên trình duyệt: {formatQuantity(progress.loaded)} /{' '}
            {formatQuantity(progress.total)} dòng.
          </Typography.Text>
        </>
      ) : (
        <>
          <Progress percent={100} status="active" showInfo={false} />
          <Typography.Text type="secondary">Đang lập báo cáo và tải file, vui lòng chờ...</Typography.Text>
        </>
      )}
    </Modal>
  )
}
