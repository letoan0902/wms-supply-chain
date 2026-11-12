/**
 * Hàng đợi gửi số đếm của màn hình đếm mù: các lần quét nhanh được xếp hàng và gửi lần lượt, đúng thứ tự quét,
 * mỗi lúc chỉ một request.
 *
 * Quy tắc (API ghi số đếm cộng dồn không có khóa chống trùng, nên gửi lại tự động có thể cộng hai lần):
 * - Mỗi thao tác chỉ được gửi một lần; không bao giờ tự gửi lại.
 * - Lỗi có phản hồi của máy chủ (400, 403, 404, 409, 500...) nghĩa là máy chủ chưa ghi: thao tác chuyển `failed`,
 *   người dùng bấm "Thử lại" hoặc "Bỏ".
 * - Không rõ kết quả (mất mạng, hết thời gian chờ, 502, 504): chuyển `unknown`; màn hình phải tải lại số đã đếm
 *   để người dùng tự kiểm tra trước khi thử lại.
 * - Lỗi do màn hình phát hiện trước khi gửi (mã không có trong danh mục, thiếu số lô) là `rejected`: thao tác bị
 *   loại khỏi hàng đợi kèm thông báo, vì gửi lại cũng không khác.
 * - Thao tác lỗi vẫn nằm trong danh sách cho tới khi người dùng thử lại hoặc bỏ, không bao giờ bị xóa âm thầm.
 *
 * Tệp không phụ thuộc React hay axios để kiểm tra được bằng Node.
 */

export type QueueItemStatus = 'queued' | 'sending' | 'failed' | 'unknown'

/** Cách xử lý một lỗi: xem chú thích đầu tệp. */
export type FailureKind = 'failed' | 'unknown' | 'rejected'

export interface QueueItem<T> {
  /** Số thứ tự tăng dần theo lần thêm vào hàng đợi */
  id: number
  payload: T
  status: QueueItemStatus
  /** Số lần đã gửi (lần đầu và các lần người dùng bấm thử lại) */
  attempts: number
  error: string | null
}

export interface QueueHandlers<T, R> {
  /** Gửi một thao tác; ném lỗi nếu thất bại */
  send: (payload: T) => Promise<R>
  /** Phân loại lỗi */
  classify: (error: unknown) => FailureKind
  /** Thông báo lỗi để hiển thị */
  describe: (error: unknown) => string
  onSuccess?: (item: QueueItem<T>, result: R) => void
  /** Gọi khi thao tác lỗi (kể cả `rejected`, lúc đó thao tác đã bị loại khỏi hàng đợi) */
  onFailure?: (item: QueueItem<T>, kind: FailureKind) => void
  /** Danh sách thao tác đang chờ hoặc lỗi thay đổi */
  onChange?: (items: readonly QueueItem<T>[]) => void
}

export class SerialCountQueue<T, R> {
  private items: QueueItem<T>[] = []
  private nextId = 1
  private running = false
  private disposed = false
  private readonly handlers: QueueHandlers<T, R>

  constructor(handlers: QueueHandlers<T, R>) {
    this.handlers = handlers
  }

  /** Các thao tác đang chờ gửi, đang gửi hoặc lỗi, theo thứ tự thêm vào. */
  snapshot(): readonly QueueItem<T>[] {
    return this.items.map((item) => ({ ...item }))
  }

  /** Thêm một thao tác vào cuối hàng đợi; trả số thứ tự của thao tác. */
  enqueue(payload: T): number {
    const id = this.nextId++
    this.items.push({ id, payload, status: 'queued', attempts: 0, error: null })
    this.emit()
    void this.pump()
    return id
  }

  /**
   * Người dùng bấm thử lại một thao tác lỗi: đưa về cuối hàng đợi. Trả false nếu thao tác không còn hoặc không ở
   * trạng thái lỗi (đang chờ hoặc đang gửi thì không gửi thêm lần nữa).
   */
  retry(id: number): boolean {
    const index = this.items.findIndex((item) => item.id === id)
    if (index < 0) return false
    const item = this.items[index]
    if (item.status !== 'failed' && item.status !== 'unknown') return false
    this.items.splice(index, 1)
    this.items.push({ ...item, status: 'queued', error: null })
    this.emit()
    void this.pump()
    return true
  }

  /** Người dùng bỏ một thao tác lỗi. Không bỏ được thao tác đang chờ hoặc đang gửi. */
  discard(id: number): boolean {
    const index = this.items.findIndex((item) => item.id === id)
    if (index < 0) return false
    const status = this.items[index].status
    if (status !== 'failed' && status !== 'unknown') return false
    this.items.splice(index, 1)
    this.emit()
    return true
  }

  /** Số thao tác chưa gửi xong (đang chờ hoặc đang gửi). */
  pendingCount(): number {
    return this.items.filter((item) => item.status === 'queued' || item.status === 'sending').length
  }

  /** Ngừng gọi lại màn hình (khi rời trang). Request đang gửi vẫn chạy nốt nhưng kết quả không còn được báo. */
  dispose(): void {
    this.disposed = true
  }

  private emit(): void {
    if (!this.disposed) this.handlers.onChange?.(this.snapshot())
  }

  private async pump(): Promise<void> {
    if (this.running) return
    this.running = true
    try {
      for (;;) {
        if (this.disposed) return
        const item = this.items.find((candidate) => candidate.status === 'queued')
        if (!item) return
        item.status = 'sending'
        item.attempts += 1
        this.emit()
        try {
          const result = await this.handlers.send(item.payload)
          this.items = this.items.filter((candidate) => candidate.id !== item.id)
          if (!this.disposed) this.handlers.onSuccess?.({ ...item }, result)
          this.emit()
        } catch (error) {
          const kind = this.handlers.classify(error)
          const message = this.handlers.describe(error)
          if (kind === 'rejected') {
            this.items = this.items.filter((candidate) => candidate.id !== item.id)
            if (!this.disposed) this.handlers.onFailure?.({ ...item, error: message }, kind)
          } else {
            item.status = kind
            item.error = message
            if (!this.disposed) this.handlers.onFailure?.({ ...item }, kind)
          }
          this.emit()
        }
      }
    } finally {
      this.running = false
    }
  }
}
