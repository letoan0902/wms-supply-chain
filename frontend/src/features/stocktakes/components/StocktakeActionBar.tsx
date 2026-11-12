import {
  AuditOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  PlayCircleOutlined,
  ScanOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Space, Tooltip } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getErrorStatus } from '../../../services/api'
import { getStoredUser } from '../../../utils/storage'
import {
  approveAdjustment,
  approveByManager,
  cancelStocktake,
  finishStocktake,
  rejectStocktake,
  startStocktake,
} from '../stocktakeApi'
import { allowedActions, isSameApprover, uncountedLinesOf, type StocktakeAbilities } from '../stocktakeLogic'
import type { StocktakeSession } from '../types'
import ActionErrorDetails from './ActionErrorDetails'
import ReasonModal from './ReasonModal'

interface Props {
  session: StocktakeSession
  abilities: StocktakeAbilities
  /** Nhận đợt do thao tác trả về */
  onUpdated: (session: StocktakeSession) => void
  /** Tải lại đợt (sau lỗi 409 trạng thái đã đổi) */
  onReload: () => void
}

/** Phạm vi vị trí sẽ bị đóng băng, dùng trong cảnh báo bắt đầu kiểm kê. */
function freezeScopeText(session: StocktakeSession): string {
  if (session.scopeType === 'WAREHOUSE') return `toàn bộ vị trí ô/kệ của kho ${session.warehouseName ?? ''}`.trim()
  if (session.scopeType === 'ZONES') return `mọi vị trí thuộc khu ${(session.zones ?? []).join(', ')}`
  return `${session.locations?.length ?? session.totalLocations} vị trí đã chọn`
}

/**
 * Nút thao tác của đợt kiểm kê theo trạng thái và vai trò: bắt đầu, vào màn hình đếm, kết thúc đếm, hủy, Quản lý
 * kho duyệt, Kế toán duyệt phiếu điều chỉnh, từ chối. Mỗi thao tác có hộp xác nhận; lỗi của máy chủ hiện kèm chi
 * tiết trong `data` của lỗi 409.
 */
export default function StocktakeActionBar({ session, abilities, onUpdated, onReload }: Props) {
  const { modal, message } = App.useApp()
  const navigate = useNavigate()
  const [busy, setBusy] = useState<string | null>(null)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const actions = allowedActions(session.status, abilities)
  const username = getStoredUser()?.username ?? null
  const sameApprover = isSameApprover(session.managerApprovedBy, username)

  const showError = (title: string, error: unknown, fallback: string, forbiddenHint?: string) => {
    modal.error({
      title,
      content: <ActionErrorDetails error={error} fallback={fallback} forbiddenHint={forbiddenHint} />,
      width: 560,
    })
    if (getErrorStatus(error) === 409) onReload()
  }

  const handleStart = () => {
    modal.confirm({
      title: `Bắt đầu kiểm kê ${session.code}?`,
      width: 560,
      okText: 'Bắt đầu và đóng băng vị trí',
      cancelText: 'Đóng',
      okButtonProps: { 'data-write-action': 'true' },
      content: (
        <div className="flex flex-col gap-2">
          <Alert
            type="warning"
            showIcon
            message={`Hệ thống sẽ đóng băng ${freezeScopeText(session)} và chụp lại tồn hệ thống tại thời điểm này.`}
            description="Cho tới khi đợt kết thúc (hoàn tất, bị từ chối hoặc bị hủy): lập phiếu nhập kho vào các vị trí này, xuất kho chỉ định các vị trí này, duyệt xuất và nhận hàng điều chuyển qua các vị trí này đều bị chặn. Giữ chỗ cho đơn mới và xuất kho không chỉ định ô sẽ lấy hàng ở vị trí khác."
          />
          <span className="text-sm">Mỗi vị trí chỉ thuộc một đợt kiểm kê đang mở tại một thời điểm.</span>
        </div>
      ),
      onOk: async () => {
        setBusy('start')
        try {
          const updated = await startStocktake(session.id)
          onUpdated(updated)
          message.success(`Đã bắt đầu kiểm kê ${updated.code}: ${updated.totalLocations} vị trí, ${updated.totalLines} dòng cần đếm`)
        } catch (error) {
          showError('Không bắt đầu được đợt kiểm kê', error, 'Không bắt đầu được đợt kiểm kê')
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const runFinish = async (confirmUncountedAsZero: boolean) => {
    setBusy('finish')
    try {
      const updated = await finishStocktake(session.id, confirmUncountedAsZero)
      onUpdated(updated)
      message.success(`Đã kết thúc đếm ${updated.code}, chênh lệch đã được tính`)
    } catch (error) {
      const uncounted = uncountedLinesOf(error)
      if (uncounted !== null && !confirmUncountedAsZero) {
        // Máy chủ báo còn dòng chưa đếm: hỏi xác nhận rõ ràng trước khi tính các dòng đó bằng 0
        modal.confirm({
          title: `Còn ${uncounted} dòng chưa đếm`,
          width: 540,
          okText: `Tính ${uncounted} dòng bằng 0 và kết thúc`,
          cancelText: 'Quay lại đếm tiếp',
          okButtonProps: { danger: true, 'data-write-action': 'true' },
          content: (
            <div className="flex flex-col gap-2">
              <span>
                Các dòng chưa đếm sẽ được ghi số đếm bằng 0. Nếu thực tế vẫn còn hàng ở các dòng này, chênh lệch sẽ ra
                thiếu toàn bộ số tồn hệ thống của dòng.
              </span>
              <span className="text-xs text-slate-500">
                Có thể xem danh sách ở tab Dòng đếm với bộ lọc "Chỉ dòng chưa đếm".
              </span>
            </div>
          ),
          onOk: () => runFinish(true),
        })
        onReload()
        return
      }
      showError('Không kết thúc được đợt đếm', error, 'Không kết thúc được đợt đếm')
    } finally {
      setBusy(null)
    }
  }

  const handleFinish = () => {
    modal.confirm({
      title: `Kết thúc đếm ${session.code}?`,
      width: 520,
      okText: 'Kết thúc đếm',
      cancelText: 'Đóng',
      content:
        'Sau khi kết thúc, nhân viên không ghi thêm số đếm được và hệ thống tính chênh lệch (số đếm trừ tồn hệ thống) cho mọi dòng. Vị trí vẫn đóng băng cho tới khi duyệt xong.',
      onOk: () => runFinish(false),
    })
  }

  const handleCancel = async (reason: string) => {
    try {
      const updated = await cancelStocktake(session.id, reason)
      onUpdated(updated)
      setCancelOpen(false)
      message.success(`Đã hủy đợt kiểm kê ${updated.code}`)
    } catch (error) {
      showError('Không hủy được đợt kiểm kê', error, 'Không hủy được đợt kiểm kê')
      throw error
    }
  }

  const handleApprove = async (notes: string) => {
    try {
      const updated = await approveByManager(session.id, notes)
      onUpdated(updated)
      setApproveOpen(false)
      message.success(
        updated.status === 'COMPLETED'
          ? `Đã duyệt ${updated.code}: không có chênh lệch, đợt hoàn tất`
          : `Đã duyệt ${updated.code} và lập phiếu điều chỉnh ${updated.adjustmentCode ?? ''}, chờ Kế toán duyệt`,
      )
    } catch (error) {
      showError('Không duyệt được kết quả kiểm kê', error, 'Không duyệt được kết quả kiểm kê')
      throw error
    }
  }

  const handleApproveAdjustment = () => {
    modal.confirm({
      title: `Duyệt phiếu điều chỉnh ${session.adjustmentCode ?? ''}?`,
      width: 560,
      okText: 'Duyệt và cập nhật tồn kho',
      cancelText: 'Đóng',
      content: (
        <div className="flex flex-col gap-2">
          <span>
            Tồn kho của các dòng chênh lệch được cập nhật ngay (tồn mới bằng tồn hiện tại cộng chênh lệch), vị trí được
            mở đóng băng và đợt chuyển Hoàn tất. Không hoàn tác được.
          </span>
          <span className="text-xs text-slate-500">
            Người duyệt cấp Kế toán phải khác người đã duyệt cấp Quản lý kho ({session.managerApprovedBy ?? '—'}).
          </span>
        </div>
      ),
      onOk: async () => {
        setBusy('approveAdjustment')
        try {
          const updated = await approveAdjustment(session.id)
          onUpdated(updated)
          message.success(`Đã duyệt phiếu điều chỉnh, đợt ${updated.code} hoàn tất và tồn kho đã cập nhật`)
        } catch (error) {
          showError(
            'Không duyệt được phiếu điều chỉnh',
            error,
            'Không duyệt được phiếu điều chỉnh',
            `Kế toán duyệt phải là người khác với người đã duyệt cấp Quản lý kho (${session.managerApprovedBy ?? '—'}). Nếu bạn vừa duyệt cấp Quản lý kho, hãy nhờ một Kế toán hoặc Quản trị viên khác duyệt phiếu này.`,
          )
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const handleReject = async (reason: string) => {
    try {
      const updated = await rejectStocktake(session.id, reason)
      onUpdated(updated)
      setRejectOpen(false)
      message.success(`Đã từ chối đợt kiểm kê ${updated.code}`)
    } catch (error) {
      showError('Không từ chối được', error, 'Không từ chối được đợt kiểm kê')
      throw error
    }
  }

  if (actions.length === 0) return null

  return (
    <>
      <Space wrap>
        {actions.includes('count') && (
          <Button
            type="primary"
            icon={<ScanOutlined />}
            onClick={() => navigate(`/stocktakes/${session.id}/count`)}
            data-write-action
          >
            Màn hình đếm
          </Button>
        )}
        {actions.includes('start') && (
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={busy === 'start'}
            disabled={busy !== null}
            onClick={handleStart}
            data-write-action
          >
            Bắt đầu kiểm kê
          </Button>
        )}
        {actions.includes('finish') && (
          <Button
            icon={<CheckCircleOutlined />}
            loading={busy === 'finish'}
            disabled={busy !== null}
            onClick={handleFinish}
          >
            Kết thúc đếm
          </Button>
        )}
        {actions.includes('approve') && (
          <Button type="primary" icon={<CheckCircleOutlined />} disabled={busy !== null} onClick={() => setApproveOpen(true)}>
            Quản lý kho duyệt
          </Button>
        )}
        {actions.includes('approveAdjustment') && (
          <Tooltip
            title={
              sameApprover
                ? `Bạn đã duyệt cấp Quản lý kho cho đợt này; Kế toán duyệt phải là người khác (máy chủ sẽ từ chối)`
                : undefined
            }
          >
            <Button
              type="primary"
              icon={<AuditOutlined />}
              loading={busy === 'approveAdjustment'}
              disabled={busy !== null || sameApprover}
              onClick={handleApproveAdjustment}
              data-write-action
            >
              Kế toán duyệt phiếu điều chỉnh
            </Button>
          </Tooltip>
        )}
        {actions.includes('reject') && (
          <Button danger icon={<CloseCircleOutlined />} disabled={busy !== null} onClick={() => setRejectOpen(true)}>
            Từ chối
          </Button>
        )}
        {actions.includes('cancel') && (
          <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setCancelOpen(true)}>
            Hủy đợt
          </Button>
        )}
      </Space>

      <ReasonModal
        open={cancelOpen}
        title={`Hủy đợt kiểm kê ${session.code}`}
        description={
          <span>
            Hủy đợt sẽ mở đóng băng các vị trí, tồn kho giữ nguyên, số đã đếm không được dùng. Không hoàn tác được.
          </span>
        }
        label="Lý do hủy"
        required={false}
        okText="Xác nhận hủy"
        danger
        placeholder="Không bắt buộc"
        onCancel={() => setCancelOpen(false)}
        onSubmit={handleCancel}
      />
      <ReasonModal
        open={approveOpen}
        title={`Quản lý kho duyệt kết quả kiểm kê ${session.code}`}
        description={
          <div className="flex flex-col gap-1">
            <span>
              Không có chênh lệch: đợt hoàn tất ngay, vị trí được mở đóng băng. Có chênh lệch: hệ thống lập phiếu điều
              chỉnh chờ Kế toán duyệt, tồn kho chưa đổi và vị trí vẫn đóng băng.
            </span>
            <span className="text-xs text-slate-500">
              Dòng có số đếm nhỏ hơn số đang giữ chỗ cho đơn sẽ chặn việc duyệt; xem tab Chênh lệch trước khi duyệt.
            </span>
          </div>
        }
        label="Ghi chú"
        required={false}
        okText="Duyệt"
        placeholder="Không bắt buộc, ghi vào phiếu điều chỉnh nếu có"
        onCancel={() => setApproveOpen(false)}
        onSubmit={handleApprove}
      />
      <ReasonModal
        open={rejectOpen}
        title={`Từ chối đợt kiểm kê ${session.code}`}
        description={
          <span>
            {session.status === 'PENDING_ACCOUNTING'
              ? `Phiếu điều chỉnh ${session.adjustmentCode ?? ''} bị từ chối, tồn kho giữ nguyên. `
              : 'Kết quả kiểm kê bị từ chối, tồn kho giữ nguyên. '}
            Vị trí được mở đóng băng. Không hoàn tác được.
          </span>
        }
        label="Lý do từ chối"
        required
        okText="Xác nhận từ chối"
        danger
        onCancel={() => setRejectOpen(false)}
        onSubmit={handleReject}
      />
    </>
  )
}
