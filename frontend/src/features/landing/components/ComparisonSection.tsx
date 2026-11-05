import {
  CheckCircleFilled,
  CloseOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import { Tooltip } from 'antd'
import { Fragment } from 'react'
import { FEATURE_COMPARISON_CATEGORIES } from '../pricingConfig'

export default function ComparisonSection() {
  const renderCell = (val: string | boolean, isPro: boolean = false) => {
    if (typeof val === 'boolean') {
      return val ? (
        <CheckCircleFilled className={`text-base ${isPro ? 'text-blue-600' : 'text-emerald-500'}`} />
      ) : (
        <CloseOutlined className="text-xs text-slate-300" />
      )
    }
    return (
      <span className={`text-xs font-semibold ${isPro ? 'text-blue-700 font-bold' : 'text-slate-700'}`}>
        {val}
      </span>
    )
  }

  return (
    <section id="comparison" className="py-20 bg-slate-50 border-t border-slate-200">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 rounded-full bg-slate-200/80 px-3.5 py-1 text-xs font-semibold text-slate-700 mb-3">
            So Sánh Toàn Diện
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Ma trận tính năng chi tiết theo từng gói
          </h2>
          <p className="mt-4 text-base text-slate-600">
            Xem rõ từng tính năng và quyền hạn được mở khóa trong mỗi gói dịch vụ.
          </p>
        </div>

        {/* Comparison Table Container */}
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
          <table className="w-full min-w-[640px] text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-100/60 text-xs font-bold uppercase tracking-wider text-slate-600">
                <th className="py-5 px-6 w-2/5">Tính năng & Tiêu chí</th>
                <th className="py-5 px-4 text-center w-1/5">Gói Khởi Đầu (Free)</th>
                <th className="py-5 px-4 text-center w-1/5 bg-blue-50/70 text-blue-800 border-x border-blue-100">
                  Gói Chuyên Nghiệp (Pro)
                </th>
                <th className="py-5 px-4 text-center w-1/5">Gói Doanh Nghiệp (Enterprise)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {FEATURE_COMPARISON_CATEGORIES.map((cat, catIdx) => (
                <Fragment key={catIdx}>
                  {/* Category Header Row */}
                  <tr className="bg-slate-50/80">
                    <td
                      colSpan={4}
                      className="py-3 px-6 text-xs font-bold text-slate-900 uppercase tracking-wide bg-slate-100/80"
                    >
                      {cat.category}
                    </td>
                  </tr>

                  {/* Feature Rows */}
                  {cat.rows.map((row, rowIdx) => (
                    <tr
                      key={rowIdx}
                      className="hover:bg-slate-50/50 transition-colors"
                    >
                      <td className="py-3.5 px-6 text-xs text-slate-700 font-medium">
                        <div className="flex items-center gap-1.5">
                          <span>{row.name}</span>
                          {row.tooltip && (
                            <Tooltip title={row.tooltip}>
                              <InfoCircleOutlined className="text-slate-400 hover:text-slate-600 cursor-pointer text-xs" />
                            </Tooltip>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        {renderCell(row.free, false)}
                      </td>
                      <td className="py-3.5 px-4 text-center bg-blue-50/30 border-x border-blue-50">
                        {renderCell(row.pro, true)}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        {renderCell(row.enterprise, false)}
                      </td>
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

