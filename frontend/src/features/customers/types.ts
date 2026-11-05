/**
 * Kiểu dữ liệu Khách hàng / Đại lý & Kiểm tra công nợ (FR14 - WMS-08.2).
 */

export type CustomerType = 'RETAIL' | 'WHOLESALE' | 'AGENT'

export interface Customer {
  id: number
  code: string
  name: string
  taxCode?: string
  email?: string
  phone?: string
  address?: string
  customerType: CustomerType
  creditLimit: number
  currentDebt: number
  debtLocked: boolean
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CustomerCreditCheckResponse {
  customerId: number
  customerCode: string
  customerName: string
  customerType: CustomerType
  creditLimit: number
  currentDebt: number
  availableCredit: number
  orderAmount: number
  projectedDebt: number
  isDebtLocked: boolean
  isOverLimit: boolean
  canProceed: boolean
  status: 'ALLOWED' | 'WARNING_NEAR_LIMIT' | 'BLOCKED_OVER_LIMIT' | 'BLOCKED_DEBT_LOCKED' | 'BLOCKED_INACTIVE'
  message: string
}

export interface CustomerQueryParams {
  keyword?: string
  type?: CustomerType
  active?: boolean
  debtLocked?: boolean
  page?: number
  size?: number
}
