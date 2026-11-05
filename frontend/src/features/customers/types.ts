/**
 * Kiểu dữ liệu Khách hàng / Đại lý & Kiểm tra công nợ (FR14, FR18 - WMS-08.2, WMS-11.4).
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

/**
 * Loại giao dịch công nợ khách hàng (FR14, FR18).
 */
export type CustomerTransactionType = 'SALES_INVOICE' | 'PAYMENT' | 'RETURN' | 'ADJUSTMENT'

/**
 * Bản ghi biến động công nợ / Hóa đơn bán hàng (FR18 - Recent Invoices & Receivables).
 */
export interface CustomerTransaction {
  id: number
  customerId: number
  customerName?: string
  transactionType: CustomerTransactionType
  referenceCode?: string
  amount: number
  balanceAfter: number
  note?: string
  transactionDate?: string
  createdAt?: string
  createdBy?: string
}

/**
 * Payload ghi nhận giao dịch công nợ / Hóa đơn bán hàng (FR18).
 */
export interface RecordCustomerTransactionPayload {
  type: CustomerTransactionType
  amount: number
  referenceCode?: string
  note?: string
}
