import type { ReactNode } from 'react'
import type { CompanyStatus, SubscriptionPlan } from './types'

export interface StatusStyle {
  label: string
  dotClass: string
  badgeClass: string
}

export const COMPANY_STATUS_CONFIG: Record<CompanyStatus, StatusStyle> = {
  PENDING: {
    label: 'Chờ phê duyệt',
    dotClass: 'bg-amber-500',
    badgeClass: 'bg-amber-50/80 text-amber-800 border-amber-200/80',
  },
  ACTIVE: {
    label: 'Đang hoạt động',
    dotClass: 'bg-emerald-500',
    badgeClass: 'bg-emerald-50/80 text-emerald-800 border-emerald-200/80',
  },
  SUSPENDED: {
    label: 'Tạm khóa',
    dotClass: 'bg-rose-500',
    badgeClass: 'bg-rose-50/80 text-rose-800 border-rose-200/80',
  },
}

export const PLAN_CONFIG: Record<SubscriptionPlan, { label: string; badgeClass: string }> = {
  TRIAL: {
    label: 'Dùng thử (Trial)',
    badgeClass: 'bg-blue-50/80 text-blue-700 border-blue-200/80',
  },
  PRO: {
    label: 'Chuyên nghiệp (Pro)',
    badgeClass: 'bg-indigo-50/80 text-indigo-700 border-indigo-200/80',
  },
  FREE: {
    label: 'Khởi đầu (Free)',
    badgeClass: 'bg-slate-100 text-slate-700 border-slate-200',
  },
}

/**
 * Render Status Dot + Badge theo phong cách /unslop:
 * Hairline border, quiet tint, indicator dot rõ ràng không chói mắt.
 */
export function renderCompanyStatusBadge(status: CompanyStatus): ReactNode {
  const config = COMPANY_STATUS_CONFIG[status] || {
    label: status,
    dotClass: 'bg-slate-400',
    badgeClass: 'bg-slate-100 text-slate-600 border-slate-200',
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-md text-xs font-medium border ${config.badgeClass}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dotClass}`} />
      <span>{config.label}</span>
    </span>
  )
}

export function renderPlanBadge(plan?: SubscriptionPlan | null): ReactNode {
  if (!plan) return <span className="text-slate-400 text-xs font-mono">—</span>
  const config = PLAN_CONFIG[plan] || {
    label: plan,
    badgeClass: 'bg-slate-100 text-slate-600 border-slate-200',
  }
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium border ${config.badgeClass}`}
    >
      {config.label}
    </span>
  )
}
