import { CheckCircleOutlined, PlayCircleOutlined, ScanOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Space } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getErrorStatus } from '../../../services/api'
import { cancelStocktake, finishStocktake, startStocktake } from '../stocktakeApi'
import { allowedActions, uncountedLinesOf, type StocktakeAbilities } from '../stocktakeLogic'
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
 * Nút thao tác của đợt kiểm kê theo trạng thái và vai trò: bắt đầu, vào màn hình đếm, kết thúc đếm, hủy.
 * Mỗi thao tác có hộp xác nhận; lỗi của máy chủ hiện kèm chi tiết trong `data` của lỗi 409.
 */
export default function StocktakeActionBar({ session, abilities, onUpdated, onReload }: Props) {
  const { modal, message } = App.useApp()
  const navigate = useNavigate()
  const [busy, setBusy] = useState<string | null>(null)
  const [cancelOpen, setCancelOpen] = useState(false)
  const actions = allowedActions(session.status, abilities)

  const showError = (title: string, error: unknown, fallback: string) => {
    modal.error({ title, content: <ActionErrorDetails error={error} fallback={fallback} />, width: 520 })
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
    </>
  )
}
