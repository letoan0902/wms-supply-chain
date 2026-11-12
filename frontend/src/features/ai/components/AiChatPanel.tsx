import { CloseOutlined, MessageOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import { Alert, Button, Input, Tag, Tooltip, Typography } from 'antd'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import type { KeyboardEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import { formatUsage, lookupLabels, MAX_MESSAGE_LENGTH, normalizeQuestion, placeholderRange, SUGGESTED_QUESTIONS } from '../aiFormat'
import type { AskOutcome, ChatError, ChatMessage } from '../useAiChat'
import type { AiUsage } from '../types'

const { Text } = Typography

interface AiChatPanelProps {
  messages: ChatMessage[]
  pending: boolean
  error: ChatError | null
  usage: AiUsage | null
  /** Thuê bao đã hết hạn: máy chủ chặn POST nên không nhận câu hỏi mới */
  readOnly: boolean
  onAsk: (question: string, retry?: boolean) => Promise<AskOutcome>
  onReset: () => void
  onClose: () => void
}

/** Một câu trả lời của trợ lý kèm nhãn giả lập, các phép tra cứu và ghi chú bị cắt, bị từ chối. */
function AssistantMessage({ message }: { message: ChatMessage }) {
  const lookups = lookupLabels(message.lookups)
  return (
    <div className="flex flex-col items-start gap-1">
      <div className="ai-chat-bubble ai-chat-bubble-assistant">{message.text}</div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 pl-1 text-[11px] text-slate-500">
        {message.mode === 'MOCK' && (
          <Tag color="default" className="m-0 text-[11px]">
            Chế độ giả lập
          </Tag>
        )}
        {lookups.length > 0 && <span>Đã tra cứu: {lookups.join(', ')}</span>}
      </div>
      {message.truncated && (
        <Text type="secondary" className="pl-1 text-[11px]">
          Câu trả lời quá dài nên đã bị cắt bớt; hãy hỏi cụ thể hơn để xem phần còn lại.
        </Text>
      )}
      {message.refused && (
        <Text type="secondary" className="pl-1 text-[11px]">
          Trợ lý không trả lời câu hỏi này; lượt AI không bị tính.
        </Text>
      )}
    </div>
  )
}

/**
 * Khung hội thoại với trợ lý AI: danh sách tin nhắn, câu hỏi gợi ý, trạng thái đang trả lời, lỗi kèm nút gửi lại,
 * ô nhập (Enter gửi, Shift+Enter xuống dòng, tối đa 2000 ký tự) và mức dùng AI trong tháng.
 */
export default function AiChatPanel({
  messages,
  pending,
  error,
  usage,
  readOnly,
  onAsk,
  onReset,
  onClose,
}: AiChatPanelProps) {
  const [input, setInput] = useState('')
  const inputRef = useRef<TextAreaRef>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const usageText = formatUsage(usage)
  const canSend = !pending && normalizeQuestion(input) !== null

  // Cuộn xuống tin nhắn mới nhất
  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, pending, error])

  const submit = async () => {
    const question = normalizeQuestion(input)
    if (question === null || pending) return
    setInput('')
    const outcome = await onAsk(question)
    if (outcome === 'restore') {
      // Câu hỏi chưa được nhận (hộp thoại gói dịch vụ đã báo lý do): trả lại ô nhập nếu người dùng chưa gõ gì mới
      setInput((current) => (current === '' ? question : current))
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
    event.preventDefault()
    void submit()
  }

  const applySuggestion = (text: string) => {
    setInput(text)
    // Chọn sẵn phần "..." để người dùng gõ đè mã SKU, số chứng từ
    requestAnimationFrame(() => {
      const textArea = inputRef.current?.resizableTextArea?.textArea
      if (!textArea) return
      textArea.focus()
      const range = placeholderRange(text)
      if (range) textArea.setSelectionRange(range[0], range[1])
      else textArea.setSelectionRange(text.length, text.length)
    })
  }

  return (
    <section
      className="ai-chat-panel"
      role="dialog"
      aria-label="Trợ lý AI"
      onKeyDown={(event) => {
        if (event.key === 'Escape') onClose()
      }}
    >
      <header className="flex items-start justify-between gap-2 border-b border-slate-200 px-4 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-900">
            <RobotOutlined className="text-blue-600" />
            Trợ lý AI
          </div>
          {usageText !== null && (
            <div className="mt-0.5 text-[11px] text-slate-500">Lượt AI tháng này: {usageText}</div>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Tooltip title="Xóa nội dung đang hiện và bắt đầu cuộc trò chuyện mới">
            <Button
              size="small"
              type="text"
              icon={<MessageOutlined />}
              onClick={onReset}
              disabled={pending || (messages.length === 0 && error === null)}
            >
              Cuộc trò chuyện mới
            </Button>
          </Tooltip>
          <Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="Đóng trợ lý AI" />
        </div>
      </header>

      {readOnly && (
        <Alert
          type="warning"
          banner
          className="text-xs"
          message="Gói dịch vụ đã hết hạn nên máy chủ chưa nhận câu hỏi mới. Gia hạn hoặc chuyển gói để dùng lại trợ lý."
        />
      )}

      <div ref={listRef} className="ai-chat-messages flex flex-col gap-3" aria-live="polite">
        {messages.length === 0 && !pending && (
          <div className="flex flex-col gap-3">
            <Text type="secondary" className="text-xs">
              Hỏi cách dùng phần mềm, tồn kho theo mã SKU hoặc theo kho, trạng thái đơn mua PO-... và đơn bán SO-....
              Trợ lý chỉ đọc dữ liệu bạn được xem, không tạo, sửa hay xóa. Mỗi câu hỏi dùng một lượt AI.
            </Text>
            <div className="flex flex-wrap gap-2">
              {SUGGESTED_QUESTIONS.map((text) => (
                <Button key={text} size="small" shape="round" onClick={() => applySuggestion(text)}>
                  {text}
                </Button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message) => {
          if (message.role === 'user') {
            return (
              <div key={message.id} className="ai-chat-bubble ai-chat-bubble-user">
                {message.text}
              </div>
            )
          }
          if (message.role === 'notice') {
            return (
              <div key={message.id} className="text-center text-[11px] text-slate-500">
                {message.text}
              </div>
            )
          }
          return <AssistantMessage key={message.id} message={message} />
        })}

        {pending && (
          <div className="flex flex-col items-start gap-1" role="status">
            <div className="ai-chat-bubble ai-chat-bubble-assistant ai-chat-typing" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
            <Text type="secondary" className="pl-1 text-[11px]">
              Trợ lý đang trả lời, có thể mất tới 90 giây.
            </Text>
          </div>
        )}

        {error && !pending && (
          <Alert
            type="error"
            showIcon
            className="text-xs"
            message={error.message}
            action={
              <Button size="small" onClick={() => void onAsk(error.question, true)}>
                Gửi lại
              </Button>
            }
          />
        )}
      </div>

      <footer className="border-t border-slate-200 bg-white px-3 py-2">
        <div className="flex items-end gap-2">
          <Input.TextArea
            ref={inputRef}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Nhập câu hỏi..."
            aria-label="Câu hỏi cho trợ lý AI"
            autoSize={{ minRows: 1, maxRows: 5 }}
            autoFocus
            count={{
              show: true,
              max: MAX_MESSAGE_LENGTH,
              // Đếm như máy chủ (độ dài chuỗi Java) và cắt phần vượt khi dán
              strategy: (text) => text.length,
              exceedFormatter: (text, { max }) => text.slice(0, max),
            }}
            className="flex-1"
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => void submit()}
            disabled={!canSend}
            loading={pending}
            aria-label="Gửi câu hỏi"
          />
        </div>
        <div className="mt-1 text-[11px] text-slate-400">Enter để gửi, Shift+Enter để xuống dòng</div>
      </footer>
    </section>
  )
}
