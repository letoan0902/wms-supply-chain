import { CloseOutlined, RobotOutlined } from '@ant-design/icons'
import { Badge, Button, Tooltip } from 'antd'
import { useState } from 'react'
import { READ_ONLY_ALLOWED_CLASS } from '../../subscription/readOnlyGuard'
import { useSubscription } from '../../subscription/subscriptionStore'
import { useAiChat } from '../useAiChat'
import AiChatPanel from './AiChatPanel'
import '../ai.css'

/**
 * Nút trợ lý AI nổi ở góc phải dưới của mọi trang trong MainLayout, mở khung chat (không phải hộp thoại toàn trang).
 * Hội thoại và mã hội thoại giữ trong bộ nhớ khi chuyển trang hay đóng khung, mất khi tải lại trang hoặc đăng xuất.
 * Cả vùng mang lớp READ_ONLY_ALLOWED_CLASS: hỏi trợ lý không phải thao tác ghi nên chế độ chỉ đọc không chặn bấm.
 */
export default function AiChatWidget() {
  const [open, setOpen] = useState(false)
  const [seenAnswers, setSeenAnswers] = useState(0)
  const { readOnly } = useSubscription()
  const chat = useAiChat()
  const { answerCount, loadUsage } = chat
  // Có câu trả lời đến trong lúc khung đang đóng
  const hasUnread = !open && answerCount > seenAnswers

  const openPanel = () => {
    setOpen(true)
    setSeenAnswers(answerCount)
    void loadUsage()
  }

  const closePanel = () => {
    setOpen(false)
    setSeenAnswers(answerCount)
  }

  return (
    <div className={READ_ONLY_ALLOWED_CLASS}>
      {open && (
        <AiChatPanel
          messages={chat.messages}
          pending={chat.pending}
          error={chat.error}
          usage={chat.usage}
          readOnly={readOnly}
          onAsk={chat.ask}
          onReset={chat.reset}
          onClose={closePanel}
        />
      )}
      <div className={`ai-chat-fab${open ? ' ai-chat-fab-open' : ''}`}>
        <Tooltip title={open ? 'Đóng trợ lý AI' : 'Trợ lý AI'} placement="left">
          <Badge dot={hasUnread} offset={[-6, 6]}>
            <Button
              type="primary"
              shape="circle"
              size="large"
              className="shadow-lg"
              style={{ width: 52, height: 52 }}
              icon={open ? <CloseOutlined /> : <RobotOutlined style={{ fontSize: 22 }} />}
              onClick={open ? closePanel : openPanel}
              aria-label={open ? 'Đóng trợ lý AI' : 'Mở trợ lý AI'}
              aria-expanded={open}
            />
          </Badge>
        </Tooltip>
      </div>
    </div>
  )
}
