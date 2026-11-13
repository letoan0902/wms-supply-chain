/**
 * Quy tắc và ánh xạ lỗi của form đăng ký doanh nghiệp (trang /register). Mọi giới hạn ở đây chép đúng từ
 * `RegisterCompanyRequest` của auth-service (vn.wms.auth.subscription.dto) và thông báo 409 của
 * `CompanyRegistrationService`; khi backend đổi quy tắc thì sửa file này theo.
 *
 * File không phụ thuộc React, antd hay axios để kiểm tra được bằng Node.
 */

/** Giá trị của form đăng ký (gồm hai trường chỉ có ở giao diện: nhập lại mật khẩu, đồng ý điều khoản). */
export interface RegisterCompanyFormValues {
  companyName: string
  taxCode?: string
  phone?: string
  fullName: string
  username: string
  email: string
  password: string
  confirmPassword: string
  agreement: boolean
}

/** Body của POST /api/auth/companies/register. */
export interface RegisterCompanyRequest {
  companyName: string
  taxCode?: string
  phone?: string
  fullName: string
  username: string
  email: string
  password: string
}

/** Các trường form gửi lên máy chủ, cũng là khóa của `data` khi máy chủ trả VALIDATION_ERROR. */
export type RegisterField = keyof RegisterCompanyRequest

export const REGISTER_FIELDS: readonly RegisterField[] = [
  'companyName',
  'taxCode',
  'phone',
  'fullName',
  'username',
  'email',
  'password',
]

/** Giới hạn độ dài theo các chú thích @Size của RegisterCompanyRequest. */
export const REGISTER_LIMITS = {
  companyNameMin: 2,
  companyNameMax: 150,
  taxCodeMax: 30,
  phoneMax: 30,
  fullNameMax: 100,
  usernameMin: 3,
  usernameMax: 50,
  emailMax: 100,
  passwordMin: 6,
  passwordMax: 72,
} as const

/** @Pattern của tên đăng nhập. */
export const USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/

// Chép từ bộ kiểm tra @Email của Hibernate Validator 8 (AbstractEmailValidator, DomainNameUtil): phần trước @ tối
// đa 64 ký tự gồm các cụm ký tự cho phép nối bằng dấu chấm; tên miền là các nhãn nối bằng dấu chấm, mỗi nhãn bắt
// đầu và kết thúc bằng ký tự cho phép, dấu gạch ngang chỉ ở giữa (không bắt buộc có đuôi như .vn), hoặc địa chỉ IP
// trong ngoặc vuông. Không nhận dạng phần trước @ đặt trong ngoặc kép (máy chủ nhận nhưng thực tế không ai dùng).
const LOCAL_PART = /^[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-￿-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-￿-]+)*$/i
const DOMAIN_CHAR = "[a-z\\u0080-\\uFFFF0-9!#$%&'*+/=?^_`{|}~]"
const DOMAIN_LABEL = `${DOMAIN_CHAR}+(?:-+${DOMAIN_CHAR}+)*`
const DOMAIN = new RegExp(`^${DOMAIN_LABEL}(?:\\.${DOMAIN_LABEL})*$`, 'i')
const IP_DOMAIN = /^\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\]$/

/** Email hợp lệ theo @Email của máy chủ (chuỗi đã bỏ khoảng trắng hai đầu). */
export function isValidEmail(value: string): boolean {
  const at = value.lastIndexOf('@')
  if (at < 0) return false
  const local = value.slice(0, at)
  const domain = value.slice(at + 1)
  if (local.length === 0 || local.length > 64 || !LOCAL_PART.test(local)) return false
  if (IP_DOMAIN.test(domain)) return true
  if (domain.length > 255 || !DOMAIN.test(domain)) return false
  // IDN.toASCII của Java từ chối nhãn dài quá 63 ký tự
  return domain.split('.').every((label) => label.length <= 63)
}

/** Chuỗi chỉ gồm khoảng trắng coi như bỏ trống (@NotBlank). */
export function isBlank(value: string | undefined | null): boolean {
  return !value || value.trim().length === 0
}

/**
 * Body gửi máy chủ: bỏ khoảng trắng hai đầu các trường chữ (máy chủ cũng cắt trước khi lưu), trường không bắt buộc
 * để trống thì không gửi. Mật khẩu giữ nguyên.
 */
export function buildRegisterPayload(values: RegisterCompanyFormValues): RegisterCompanyRequest {
  const optional = (value: string | undefined) => {
    const trimmed = value?.trim()
    return trimmed ? trimmed : undefined
  }
  return {
    companyName: values.companyName.trim(),
    taxCode: optional(values.taxCode),
    phone: optional(values.phone),
    fullName: values.fullName.trim(),
    username: values.username.trim(),
    email: values.email.trim(),
    password: values.password,
  }
}

/** Kết quả ánh xạ một phản hồi lỗi: thông báo dưới từng trường và thông báo chung (nếu có). */
export interface RegisterErrorMapping {
  fieldErrors: Partial<Record<RegisterField, string>>
  /** Thông báo hiển thị phía trên form khi lỗi không gắn được với trường nào */
  message?: string
}

/** Thân phản hồi lỗi chuẩn của backend (ApiResponse). */
export interface ErrorBody {
  code?: string
  message?: string
  data?: unknown
}

// CompanyRegistrationService báo trùng bằng ApiException.conflict với thông báo theo mẫu
// "<tên trường> đã tồn tại: <giá trị>", không có `data`; đây là cách duy nhất để biết trường nào bị trùng.
const CONFLICT_PREFIXES: ReadonlyArray<[string, RegisterField]> = [
  ['Tên đăng nhập đã tồn tại', 'username'],
  ['Email đã tồn tại', 'email'],
  ['Mã số thuế đã tồn tại', 'taxCode'],
]

function isRegisterField(key: string): key is RegisterField {
  return (REGISTER_FIELDS as readonly string[]).includes(key)
}

/**
 * Chuyển phản hồi lỗi của POST /api/auth/companies/register thành lỗi theo trường:
 * - 409 CONFLICT: nhận ra trường bị trùng từ thông báo, gắn dưới trường đó;
 * - 400 VALIDATION_ERROR: `data` là `{ tênTrường: thôngBáo }`, trường có trên form thì gắn dưới trường, còn lại
 *   gộp vào thông báo chung;
 * - lỗi khác: chỉ có thông báo chung (`message` của máy chủ hoặc `fallback`).
 */
export function mapRegisterError(
  status: number | undefined,
  body: ErrorBody | undefined,
  fallback: string,
): RegisterErrorMapping {
  const serverMessage = body?.message?.trim() || undefined

  if (status === 409 || body?.code === 'CONFLICT') {
    const match = serverMessage ? CONFLICT_PREFIXES.find(([prefix]) => serverMessage.startsWith(prefix)) : undefined
    if (match) {
      return { fieldErrors: { [match[1]]: serverMessage } }
    }
    return { fieldErrors: {}, message: serverMessage ?? fallback }
  }

  if (body?.code === 'VALIDATION_ERROR' && body.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
    const fieldErrors: Partial<Record<RegisterField, string>> = {}
    const others: string[] = []
    for (const [key, value] of Object.entries(body.data as Record<string, unknown>)) {
      if (typeof value !== 'string' || value.length === 0) continue
      if (isRegisterField(key)) {
        fieldErrors[key] = value
      } else {
        others.push(value)
      }
    }
    if (Object.keys(fieldErrors).length > 0 || others.length > 0) {
      return { fieldErrors, message: others.length > 0 ? others.join(' ') : undefined }
    }
  }

  return { fieldErrors: {}, message: serverMessage ?? fallback }
}
