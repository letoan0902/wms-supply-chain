import {
  AlertOutlined,
  CheckCircleOutlined,
  DollarCircleOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { Card, Col, Row, Skeleton } from 'antd'
import type { SupplierDebtSummary } from '../types'
import { formatCurrency, formatNumber } from '../../../utils/format'

interface Props {
  summary: SupplierDebtSummary | null
  loading: boolean
}

export default function SupplierMetricsCards({ summary, loading }: Props) {
  if (loading || !summary) {
    return (
      <Row gutter={[16, 16]}>
        {[1, 2, 3, 4].map((i) => (
          <Col xs={24} sm={12} lg={6} key={i}>
            <Card className="border border-slate-200/80 rounded-lg shadow-none">
              <Skeleton active paragraph={{ rows: 2 }} />
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  const inactiveCount = summary.totalSuppliers - summary.activeSuppliers
  const activePercent =
    summary.totalSuppliers > 0
      ? Math.round((summary.activeSuppliers / summary.totalSuppliers) * 100)
      : 100

  return (
    <Row gutter={[16, 16]}>
      {/* 1. Tổng số nhà cung cấp */}
      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Tổng nhà cung cấp
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {formatNumber(summary.totalSuppliers)}{' '}
                <span className="text-xs font-normal text-slate-400 font-sans">đối tác</span>
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <ShopOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 flex items-center gap-1.5 pt-2 border-t border-slate-100">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>
              <strong className="text-slate-700 font-semibold">{summary.activeSuppliers}</strong> đang giao dịch / {inactiveCount} ngưng
            </span>
          </div>
        </div>
      </Col>

      {/* 2. Tổng công nợ phải trả */}
      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Tổng nợ phải trả
              </div>
              <div className="text-xl lg:text-2xl font-semibold text-slate-900 tabular-nums truncate">
                {formatCurrency(summary.totalDebt)}
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <DollarCircleOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 pt-2 border-t border-slate-100 truncate">
            Nghĩa vụ công nợ thanh toán với toàn bộ đối tác
          </div>
        </div>
      </Col>

      {/* 3. Vượt hạn mức tín dụng */}
      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Vượt hạn mức nợ
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {summary.overLimitSuppliers}{' '}
                <span className="text-xs font-normal text-slate-400 font-sans">NCC</span>
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <AlertOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 flex items-center gap-1.5 pt-2 border-t border-slate-100">
            {summary.overLimitSuppliers > 0 ? (
              <>
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-red-500" />
                <span className="text-red-600 font-medium truncate">
                  Cần ưu tiên thanh toán đúng hạn
                </span>
              </>
            ) : (
              <>
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span className="text-emerald-600 font-medium">Trong ngưỡng an toàn</span>
              </>
            )}
          </div>
        </div>
      </Col>

      {/* 4. Tỷ lệ đối tác hoạt động */}
      <Col xs={24} sm={12} lg={6}>
        <div className="bg-white border border-slate-200/80 rounded-lg p-4 transition-all">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">
                Tỷ lệ hoạt động
              </div>
              <div className="text-2xl font-semibold text-slate-900 tabular-nums">
                {activePercent}%
              </div>
            </div>
            <div className="w-9 h-9 rounded-md bg-slate-50 border border-slate-200/60 flex items-center justify-center text-slate-600 text-base">
              <CheckCircleOutlined />
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500 pt-2 border-t border-slate-100 truncate">
            Đang duy trì quan hệ cung ứng ổn định
          </div>
        </div>
      </Col>
    </Row>
  )
}
