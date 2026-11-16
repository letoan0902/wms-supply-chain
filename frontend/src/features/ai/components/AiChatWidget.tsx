import { CloseOutlined } from '@ant-design/icons'
import { Tooltip } from 'antd'
import { useState } from 'react'
import { READ_ONLY_ALLOWED_CLASS } from '../../subscription/readOnlyGuard'
import { useSubscription } from '../../subscription/subscriptionStore'
import { useAiChat } from '../useAiChat'
import AiChatPanel from './AiChatPanel'
import '../ai.css'

/**
 * Biểu tượng trợ lý thân thiện, mỉm cười nhẹ nhàng (Friendly Assistant Icon)
 */
export function FriendlyChatIcon({ className = 'w-6 h-6' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
      <circle cx="9" cy="11.5" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="15" cy="11.5" r="1.1" fill="currentColor" stroke="none" />
      <path d="M9.5 14.5c.8.9 2.2.9 3 0" />
    </svg>
  )
}

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
        <Tooltip title={open ? 'Đóng trợ lý' : 'Hỏi Trợ lý ảo WMS'} placement="left">
          <button
            type="button"
            onClick={open ? closePanel : openPanel}
            aria-label={open ? 'Đóng trợ lý AI' : 'Mở trợ lý AI'}
            aria-expanded={open}
            className={`relative flex items-center justify-center w-[52px] h-[52px] rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-indigo-500 text-white shadow-[0_8px_20px_-4px_rgba(79,70,229,0.4)] hover:shadow-[0_12px_28px_-2px_rgba(79,70,229,0.5)] hover:scale-105 active:scale-95 transition-all duration-200 cursor-pointer focus:outline-hidden ${
              open ? 'rotate-90' : ''
            }`}
          >
            {open ? (
              <CloseOutlined className="text-xl" />
            ) : (
              <FriendlyChatIcon className="w-6 h-6" />
            )}

            {hasUnread && (
              <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-rose-500 border-2 border-white" />
              </span>
            )}
          </button>
        </Tooltip>
      </div>
    </div>
  )
}
