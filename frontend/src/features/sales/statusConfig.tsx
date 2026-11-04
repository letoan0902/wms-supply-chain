import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileTextOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'
import type { OrderStatus } from './types'

export interface StatusConfigItem {
  label: string
  color: string
  icon: ReactNode
}

export const SALES_STATUS_CONFIG: Record<OrderStatus, StatusConfigItem> = {
  DRAFT: {
    label: 'Bản nháp',
    color: 'default',
    icon: <FileTextOutlined />,
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    color: 'success',
    icon: <CheckCircleOutlined />,
  },
  BACKORDER: {
    label: 'Thiếu hàng (Backorder)',
    color: 'warning',
    icon: <WarningOutlined />,
  },
  COMPLETED: {
    label: 'Đã hoàn thành',
    color: 'purple',
    icon: <SyncOutlined />,
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'default',
    icon: <CloseCircleOutlined />,
  },
}
