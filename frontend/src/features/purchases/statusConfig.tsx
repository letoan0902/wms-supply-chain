import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  FileTextOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'
import type { OrderStatus } from './types'

export interface StatusConfigItem {
  label: string
  color: string
  icon: ReactNode
}

export const STATUS_CONFIG: Record<OrderStatus, StatusConfigItem> = {
  DRAFT: {
    label: 'Bản nháp',
    color: 'default',
    icon: <FileTextOutlined />,
  },
  PENDING: {
    label: 'Chờ duyệt',
    color: 'processing',
    icon: <ClockCircleOutlined />,
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    color: 'blue',
    icon: <SyncOutlined />,
  },
  APPROVED: {
    label: 'Đã phê duyệt',
    color: 'success',
    icon: <CheckCircleOutlined />,
  },
  PARTIALLY_RECEIVED: {
    label: 'Nhập một phần',
    color: 'cyan',
    icon: <SyncOutlined />,
  },
  REJECTED: {
    label: 'Đã từ chối',
    color: 'error',
    icon: <CloseCircleOutlined />,
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'default',
    icon: <CloseCircleOutlined />,
  },
  COMPLETED: {
    label: 'Hoàn thành',
    color: 'purple',
    icon: <CheckCircleOutlined />,
  },
}
