/** Tham số trên trang đăng nhập giữ trang cần quay lại, ví dụ `/login?redirect=%2Fpayment%2Fresult%3ForderCode%3D123`. */
export const REDIRECT_PARAM = 'redirect'

const LOGIN_PATH = '/login'

/**
 * Trả đường dẫn nội bộ an toàn để quay lại sau khi đăng nhập (đường dẫn, query và hash), hoặc `null`.
 * Chỉ nhận đường dẫn tương đối bắt đầu bằng một dấu `/` và sau khi phân giải vẫn cùng origin với trang hiện tại:
 * URL tuyệt đối, `//host`, `/\host` lấy từ query string bị bỏ qua để không chuyển người dùng sang trang khác.
 */
export function safeRedirectPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return null
  try {
    const url = new URL(raw, window.location.origin)
    if (url.origin !== window.location.origin || url.pathname === LOGIN_PATH) return null
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return null
  }
}

/**
 * Đường dẫn trang đăng nhập kèm trang hiện tại để quay lại (dùng khi phiên hết hạn và phải tải lại trang, lúc đó
 * state của router không còn). Trang chủ hoặc trang đăng nhập thì không kèm tham số.
 */
export function loginPathWithRedirect(
  current: { pathname: string; search: string; hash: string } = window.location,
): string {
  const target = safeRedirectPath(`${current.pathname}${current.search}${current.hash}`)
  if (!target || target === '/') return LOGIN_PATH
  return `${LOGIN_PATH}?${REDIRECT_PARAM}=${encodeURIComponent(target)}`
}
