import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CrownOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  UnlockOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Button, Divider, Drawer, Tag } from 'antd'
import type { AdminCompanyItem } from '../types'
import { renderCompanyStatusBadge, renderPlanBadge } from '../statusConfig'
import { formatDateTime } from '../../../utils/format'

interface CompanyDetailDrawerProps {
  company: AdminCompanyItem | null
  open: boolean
  onClose: () => void
  onApprove: (company: AdminCompanyItem) => void
  onReject: (company: AdminCompanyItem) => void
  onToggleStatus: (company: AdminCompanyItem) => void
}

export default function CompanyDetailDrawer({
  company,
  open,
  onClose,
  onApprove,
  onReject,
  onToggleStatus,
}: CompanyDetailDrawerProps) {
  if (!company) return null

  const isPending = company.status === 'PENDING'
  const isActive = company.status === 'ACTIVE'
  const isSuspended = company.status === 'SUSPENDED'

  return (
    <Drawer
      title={
        <div className="flex items-center justify-between pr-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-700">
              <ApartmentOutlined />
            </div>
            <div>
              <div className="text-sm font-semibold text-slate-900 leading-none">{company.name}</div>
              <div className="text-xs text-slate-500 font-mono mt-1">{company.code}</div>
            </div>
          </div>
          <div>{renderCompanyStatusBadge(company.status)}</div>
        </div>
      }
      open={open}
      onClose={onClose}
      width={540}
      footer={
        <div className="flex items-center justify-between py-1">
          <Button onClick={onClose} className="text-xs font-medium">
            Đóng
          </Button>
          <div className="flex items-center gap-2">
            {isPending && (
              <>
                <Button
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => {
                    onClose()
                    onReject(company)
                  }}
                  className="text-xs font-medium"
                >
                  Từ chối
                </Button>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={() => {
                    onClose()
                    onApprove(company)
                  }}
                  className="bg-emerald-600 hover:!bg-emerald-500 text-xs font-medium"
                >
                  Phê duyệt
                </Button>
              </>
            )}
            {isActive && (
              <Button
                danger
                icon={<LockOutlined />}
                onClick={() => {
                  onClose()
                  onToggleStatus(company)
                }}
                className="text-xs font-medium"
              >
                Tạm khóa doanh nghiệp
              </Button>
            )}
            {isSuspended && (
              <Button
                type="primary"
                icon={<UnlockOutlined />}
                onClick={() => {
                  onClose()
                  onToggleStatus(company)
                }}
                className="bg-emerald-600 hover:!bg-emerald-500 text-xs font-medium"
              >
                Kích hoạt lại
              </Button>
            )}
          </div>
        </div>
      }
    >
      <div className="space-y-6 text-xs">
        {/* Nhóm 1: Thông tin doanh nghiệp */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-1.5">
            <ApartmentOutlined />
            <span>Thông tin doanh nghiệp</span>
          </div>
          <div className="rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 space-y-2.5">
            <div className="flex justify-between">
              <span className="text-slate-500">Mã doanh nghiệp:</span>
              <span className="font-mono font-medium text-slate-900">{company.code}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Tên doanh nghiệp:</span>
              <span className="font-medium text-slate-900 text-right">{company.name}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Mã số thuế:</span>
              <span className="font-mono text-slate-800">{company.taxCode || '—'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Ngày nộp đơn đăng ký:</span>
              <span className="font-mono text-slate-700">{formatDateTime(company.createdAt)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Cập nhật lần cuối:</span>
              <span className="font-mono text-slate-700">{formatDateTime(company.updatedAt)}</span>
            </div>
          </div>
        </div>

        <Divider className="my-0" />

        {/* Nhóm 2: Tài khoản quản trị đại diện */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-1.5">
            <UserOutlined />
            <span>Tài khoản quản trị đại diện</span>
          </div>
          {company.adminUser ? (
            <div className="rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 space-y-2.5">
              <div className="flex justify-between">
                <span className="text-slate-500">Họ và tên:</span>
                <span className="font-medium text-slate-900">{company.adminUser.fullName || '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Tên đăng nhập:</span>
                <span className="font-mono text-slate-900 font-semibold">{company.adminUser.username}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Email liên hệ:</span>
                <span className="text-slate-900 flex items-center gap-1">
                  <MailOutlined className="text-slate-400 text-[11px]" />
                  <span>{company.adminUser.email}</span>
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Số điện thoại:</span>
                <span className="text-slate-900 flex items-center gap-1">
                  <PhoneOutlined className="text-slate-400 text-[11px]" />
                  <span>{company.adminUser.phone || '—'}</span>
                </span>
              </div>
              <div className="flex justify-between items-center pt-1 border-t border-slate-200/60">
                <span className="text-slate-500">Trạng thái đăng nhập:</span>
                {company.adminUser.enabled ? (
                  <Tag color="success" className="text-[11px] font-normal m-0">
                    Đã mở khóa
                  </Tag>
                ) : (
                  <Tag color="warning" className="text-[11px] font-normal m-0">
                    Đang khóa (Chưa duyệt)
                  </Tag>
                )}
              </div>
            </div>
          ) : (
            <div className="text-slate-400 italic">Chưa có thông tin tài khoản quản trị</div>
          )}
        </div>

        <Divider className="my-0" />

        {/* Nhóm 3: Thông tin gói SaaS & Đăng ký */}
        <div>
          <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-1.5">
            <CrownOutlined />
            <span>Gói dịch vụ & Thuê bao (SaaS Subscription)</span>
          </div>
          {company.subscription ? (
            <div className="rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 space-y-2.5">
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Gói hiện tại:</span>
                <div>{renderPlanBadge(company.subscription.plan)}</div>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Trạng thái gói:</span>
                <span className="font-medium text-slate-800">{company.subscription.status}</span>
              </div>
              {company.subscription.billingCycle && (
                <div className="flex justify-between">
                  <span className="text-slate-500">Chu kỳ thanh toán:</span>
                  <span className="font-medium text-slate-800">{company.subscription.billingCycle}</span>
                </div>
              )}
              {company.subscription.trialEndsAt && (
                <div className="flex justify-between">
                  <span className="text-slate-500">Hết hạn dùng thử:</span>
                  <span className="font-mono text-amber-700 font-medium">
                    {formatDateTime(company.subscription.trialEndsAt)}
                  </span>
                </div>
              )}
              {company.subscription.expiresAt && (
                <div className="flex justify-between">
                  <span className="text-slate-500">Hạn hiệu lực thuê bao:</span>
                  <span className="font-mono text-slate-800">
                    {formatDateTime(company.subscription.expiresAt)}
                  </span>
                </div>
              )}
            </div>
          ) : (
            <div className="text-slate-400 italic">Chưa khởi tạo thuê bao dịch vụ</div>
          )}
        </div>
      </div>
    </Drawer>
  )
}
