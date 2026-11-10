import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'
import type { StockTransferStatus } from './types'

export interface TransferStatusConfigItem {
  label: string
  color: string
  icon: ReactNode
}

export const TRANSFER_STATUS_CONFIG: Record<StockTransferStatus, TransferStatusConfigItem> = {
  DRAFT: {
    label: 'Bản nháp',
    color: 'default',
    icon: <FileTextOutlined />,
  },
  IN_TRANSIT: {
    label: 'Đang vận chuyển',
    color: 'processing',
    icon: <SyncOutlined spin />,
  },
  COMPLETED: {
    label: 'Đã nhận đủ',
    color: 'success',
    icon: <CheckCircleOutlined />,
  },
  PARTIAL_RECEIVED: {
    label: 'Nhận một phần',
    color: 'warning',
    icon: <ExclamationCircleOutlined />,
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'error',
    icon: <CloseCircleOutlined />,
  },
}

