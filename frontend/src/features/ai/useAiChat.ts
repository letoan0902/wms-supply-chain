import { useCallback, useRef, useState } from 'react'
import { fetchAiUsage, sendChatMessage } from './aiApi'
import { describeAiError } from './aiErrors'
import type { AiUsage } from './types'

/** Một tin nhắn trong khung chat. `notice` là dòng thông báo của hệ thống (ví dụ hội thoại cũ đã hết hạn). */
export interface ChatMessage {
  id: number
  role: 'user' | 'assistant' | 'notice'
  text: string
  mode?: string
  lookups?: string[]
  truncated?: boolean
  refused?: boolean
}

/** Lỗi của câu hỏi gần nhất; `question` để gửi lại khi người dùng bấm "Gửi lại". */
export interface ChatError {
  message: string
  question: string
}

/** Kết quả của một lần gửi: `restore` nghĩa là câu hỏi chưa được nhận (hộp thoại gói dịch vụ đã báo), trả lại ô nhập. */
export type AskOutcome = 'answered' | 'error' | 'restore' | 'skipped'

const EXPIRED_NOTICE =
  'Cuộc trò chuyện trước đã hết hạn (quá 1 giờ không hỏi thêm) nên trợ lý bắt đầu cuộc trò chuyện mới.'

/**
 * Trạng thái khung chat trợ lý AI: tin nhắn, mã hội thoại (chỉ giữ trong bộ nhớ của phiên đang mở), trạng thái đang
 * chờ, lỗi của câu gần nhất và mức dùng AI trong tháng. Không tự gửi lại khi lỗi vì mỗi lần gửi có thể tốn một lượt.
 */
export function useAiChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ChatError | null>(null)
  const [usage, setUsage] = useState<AiUsage | null>(null)
  /** Số câu trả lời đã nhận, để nút nổi báo có câu trả lời mới khi khung đang đóng */
  const [answerCount, setAnswerCount] = useState(0)

  const conversationIdRef = useRef<string | undefined>(undefined)
  const pendingRef = useRef(false)
  const nextIdRef = useRef(1)
  /** Tin nhắn của câu hỏi gần nhất, để "Gửi lại" không thêm tin nhắn mới */
  const lastQuestionIdRef = useRef<number | null>(null)
  /** Tăng mỗi khi mức dùng được cập nhật từ câu trả lời, để kết quả GET /ai/usage cũ hơn không ghi đè */
  const usageVersionRef = useRef(0)

  const newId = () => nextIdRef.current++

  /**
   * Gửi câu hỏi. `retry = true` gửi lại câu hỏi đang hiện trên khung (không thêm tin nhắn mới).
   */
  const ask = useCallback(async (question: string, retry = false): Promise<AskOutcome> => {
    if (pendingRef.current) return 'skipped'
    pendingRef.current = true
    setPending(true)
    setError(null)

    const previousId = lastQuestionIdRef.current
    const questionId = retry && previousId !== null ? previousId : newId()
    if (questionId !== previousId) {
      lastQuestionIdRef.current = questionId
      setMessages((prev) => [...prev, { id: questionId, role: 'user', text: question }])
    }

    const hadConversation = conversationIdRef.current !== undefined
    try {
      const reply = await sendChatMessage({ message: question, conversationId: conversationIdRef.current })
      conversationIdRef.current = reply.conversationId || undefined
      if (reply.usage) {
        usageVersionRef.current += 1
        setUsage(reply.usage)
      }
      const answer: ChatMessage = {
        id: newId(),
        role: 'assistant',
        text: reply.answer ?? '',
        mode: reply.mode,
        lookups: reply.lookups ?? [],
        truncated: reply.truncated,
        refused: reply.refused,
      }
      setMessages((prev) => {
        if (reply.newConversation && hadConversation) {
          // Máy chủ đã mở hội thoại mới: bỏ các tin nhắn cũ, giữ câu hỏi vừa gửi
          const current = prev.filter((m) => m.id === questionId)
          return [{ id: newId(), role: 'notice', text: EXPIRED_NOTICE }, ...current, answer]
        }
        return [...prev, answer]
      })
      setAnswerCount((count) => count + 1)
      return 'answered'
    } catch (err: unknown) {
      const message = describeAiError(err, 'Trợ lý chưa trả lời được. Vui lòng thử lại sau.')
      if (message === null) {
        // Hộp thoại gói dịch vụ đã báo (hết lượt AI hoặc gói hết hạn): bỏ tin nhắn chưa được trả lời, trả lại ô nhập
        setMessages((prev) => prev.filter((m) => m.id !== questionId))
        lastQuestionIdRef.current = null
        return 'restore'
      }
      setError({ message, question })
      return 'error'
    } finally {
      pendingRef.current = false
      setPending(false)
    }
  }, [])

  /** Bắt đầu cuộc trò chuyện mới: xóa tin nhắn và mã hội thoại (không gọi máy chủ). */
  const reset = useCallback(() => {
    if (pendingRef.current) return
    conversationIdRef.current = undefined
    lastQuestionIdRef.current = null
    setMessages([])
    setError(null)
  }, [])

  /** Tải mức dùng AI trong tháng; lỗi thì bỏ qua (dòng mức dùng không hiện). */
  const loadUsage = useCallback(async () => {
    const version = usageVersionRef.current
    try {
      const info = await fetchAiUsage()
      if (version === usageVersionRef.current) {
        setUsage({ used: info.used, max: info.max })
      }
    } catch {
      // Không chặn khung chat vì thiếu mức dùng
    }
  }, [])

  return { messages, pending, error, usage, answerCount, ask, reset, loadUsage }
}
