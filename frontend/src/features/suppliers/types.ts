/**
 * Các kiểu dữ liệu của Phân hệ Quản lý Nhà cung cấp & Công nợ (WMS-08.3 - FR09).
 */

export interface Supplier {
  id: number
  code: string
  name: string
  taxCode?: string | null
  contactName?: string | null
  email?: string | null
  phone?: string | null
  address?: string | null
  bankAccount?: string | null
  bankName?: string | null
  paymentTerms?: string | null
  creditLimit: number
  currentDebt: number
  isOverCreditLimit: boolean
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateSupplierPayload {
  code: string
  name: string
  taxCode?: string
  contactName?: string
  email?: string
  phone?: string
  address?: string
  bankAccount?: string
  bankName?: string
  paymentTerms?: string
  creditLimit?: number
  initialDebt?: number
}

export interface UpdateSupplierPayload {
  name: string
  taxCode?: string
  contactName?: string
  email?: string
  phone?: string
  address?: string
  bankAccount?: string
  bankName?: string
  paymentTerms?: string
  creditLimit?: number
  active?: boolean
}

export interface SupplierDebtSummary {
  totalSuppliers: number
  activeSuppliers: number
  overLimitSuppliers: number
  totalDebt: number
}

export type SupplierTransactionType =
  | 'PURCHASE_INVOICE'
  | 'PAYMENT'
  | 'RETURN'
  | 'ADJUSTMENT'

export interface SupplierTransaction {
  id: number
  supplierId: number
  supplierCode: string
  supplierName: string
  transactionType: SupplierTransactionType
  amount: number
  balanceAfter: number
  referenceCode?: string | null
  note?: string | null
  transactionDate: string
}

export interface RecordSupplierTransactionPayload {
  type: SupplierTransactionType
  amount: number
  referenceCode?: string
  note?: string
}
