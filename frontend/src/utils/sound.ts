/**
 * Tiện ích phát âm thanh phản hồi cho máy quét mã vạch và súng bắn mã (Web Audio API - FR07, FR11).
 * Sử dụng AudioContext nội tại của trình duyệt, không phụ thuộc file mp3 ngoài, độ trễ cực thấp (< 10ms).
 */

let audioCtx: AudioContext | null = null

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  if (!audioCtx) {
    const AudioContextClass =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    if (AudioContextClass) {
      audioCtx = new AudioContextClass()
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    void audioCtx.resume()
  }
  return audioCtx
}

/**
 * Phát tiếng "BEEP" tần số cao (1200Hz, 80ms) báo quét mã vạch thành công.
 * Âm thanh trong trẻo, mô phỏng chuẩn tiếng bip của súng bắn mã POS/Kho chuyên dụng.
 */
export function playSuccessBeep() {
  try {
    const ctx = getAudioContext()
    if (!ctx) return

    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sine'
    osc.frequency.setValueAtTime(1200, ctx.currentTime)

    // Fade out êm dịu tránh tiếng click ở cuối
    gain.gain.setValueAtTime(0.12, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.08)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + 0.08)
  } catch (err) {
    console.debug('Không thể phát âm thanh beep thành công:', err)
  }
}

/**
 * Phát tiếng "BỤP/RÈ" tần số trầm (220Hz, 200ms) báo lỗi:
 * - Không tìm thấy mã vạch trong đơn PO
 * - Mã vạch đã vượt quá số lượng cho phép
 */
export function playErrorBeep() {
  try {
    const ctx = getAudioContext()
    if (!ctx) return

    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sawtooth'
    osc.frequency.setValueAtTime(220, ctx.currentTime)

    gain.gain.setValueAtTime(0.15, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + 0.2)
  } catch (err) {
    console.debug('Không thể phát âm thanh beep lỗi:', err)
  }
}

/**
 * Phát tiếng cảnh báo hai nốt (600Hz -> 800Hz, 120ms):
 * Dùng khi quét đủ số lượng PO hoặc có cảnh báo cận hạn sử dụng.
 */
export function playWarningBeep() {
  try {
    const ctx = getAudioContext()
    if (!ctx) return

    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'triangle'
    osc.frequency.setValueAtTime(600, ctx.currentTime)
    osc.frequency.linearRampToValueAtTime(850, ctx.currentTime + 0.1)

    gain.gain.setValueAtTime(0.1, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.12)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + 0.12)
  } catch (err) {
    console.debug('Không thể phát âm thanh beep cảnh báo:', err)
  }
}
