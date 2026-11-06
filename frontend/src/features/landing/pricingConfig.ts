export type BillingCycle = 'monthly' | 'yearly'

export type PlanTier = 'free' | 'pro' | 'enterprise'

export interface PlanLimits {
  users: string
  warehouses: string
  skus: string
  aiRequests: string
  support: string
  apiAccess: boolean
  dataIsolation: string
}

export interface PricingPlan {
  id: string
  tier: PlanTier
  name: string
  badge?: string
  tagline: string
  description: string
  monthlyPrice: number
  yearlyPrice: number
  isPopular?: boolean
  isTrialDefault?: boolean
  limits: PlanLimits
  highlightFeatures: string[]
  ctaText: string
  ctaSubtext?: string
}

export interface FeatureComparisonRow {
  name: string
  tooltip?: string
  free: string | boolean
  pro: string | boolean
  enterprise: string | boolean
}

export interface FeatureComparisonCategory {
  category: string
  rows: FeatureComparisonRow[]
}

export interface FAQItem {
  id: string
  question: string
  answer: string
}

export interface TestimonialItem {
  id: string
  quote: string
  author: string
  role: string
  company: string
  rating: number
  metric: string
  metricLabel: string
}

export interface KeyFeatureItem {
  id: string
  title: string
  description: string
  category: string
  highlights: string[]
  badge?: string
}

export interface PlatformStat {
  value: string
  label: string
  sublabel: string
}

/**
 * Cấu hình toàn bộ dữ liệu gói dịch vụ, giá tiền, tính năng và tài nguyên.
 * Mọi thay đổi về hạn mức, chính sách giá, ưu đãi chỉ cần cập nhật tại file này.
 */
export const PRICING_PLANS: PricingPlan[] = [
  {
    id: 'plan-free',
    tier: 'free',
    name: 'Gói Khởi Đầu',
    tagline: 'Phù hợp cho cửa hàng & kho nhỏ mới bắt đầu số hóa',
    description: 'Trải nghiệm miễn phí các tính năng quản lý kho cơ bản không giới hạn thời gian.',
    monthlyPrice: 0,
    yearlyPrice: 0,
    limits: {
      users: 'Tối đa 3 tài khoản',
      warehouses: '1 kho hoạt động',
      skus: '200 mã hàng (SKU)',
      aiRequests: '20 lượt AI / tháng',
      support: 'Tài liệu hướng dẫn trực tuyến',
      apiAccess: false,
      dataIsolation: 'Logic độc lập (Multi-tenant)',
    },
    highlightFeatures: [
      'Quản lý danh mục hàng hóa & phân loại SKU',
      'Sơ đồ kho cơ bản: Khu vực (Zone) & Kệ (Rack)',
      'Tạo đơn mua (PO) & phiếu nhập kho (GRN)',
      'Tạo đơn bán (SO) & phiếu xuất kho (GDN)',
      'Thẻ kho theo dõi lịch sử xuất nhập tồn cơ bản',
      'Hỗ trợ 20 lượt AI trích xuất tài liệu/tháng',
    ],
    ctaText: 'Bắt đầu Miễn phí',
    ctaSubtext: 'Không cần thẻ tín dụng',
  },
  {
    id: 'plan-pro',
    tier: 'pro',
    name: 'Gói Chuyên Nghiệp',
    badge: 'Phổ biến nhất • Khuyên dùng',
    tagline: 'Dành cho doanh nghiệp vừa và nhỏ, chuỗi kho vận đang tăng trưởng nhanh',
    description: 'Mở rộng quy mô với đa kho, barcode tự động, kiểm kê định kỳ và trợ lý AI thông minh.',
    monthlyPrice: 499000,
    yearlyPrice: 4790000, // Tiết kiệm 20% khi trả theo năm (~399.000đ/tháng)
    isPopular: true,
    isTrialDefault: true,
    limits: {
      users: 'Lên đến 50 tài khoản',
      warehouses: 'Tối đa 20 kho vận hành',
      skus: 'Không giới hạn SKU',
      aiRequests: '500 lượt AI / tháng',
      support: 'Hỗ trợ ưu tiên qua Email & Chat trực tiếp',
      apiAccess: false,
      dataIsolation: 'Logic độc lập bảo mật cao',
    },
    highlightFeatures: [
      'Bao gồm toàn bộ tính năng của Gói Khởi Đầu',
      'Quản trị đa kho & sơ đồ vị trí ô kệ (Zone - Rack - Bin)',
      'Điều chuyển hàng hóa giữa các kho nội bộ linh hoạt',
      'Kiểm kê kho thực tế & cân đối tồn kho tự động',
      'In mã vạch Barcode & QR Code chuẩn công nghiệp',
      'Nhập dữ liệu hàng loạt bằng AI từ Excel, CSV, PDF',
      'Trợ lý AI thông minh hỏi đáp tồn kho theo thời gian thực',
      'Cảnh báo tồn an toàn (Min/Max) và hàng cận hạn sử dụng',
      'Báo cáo hiệu suất kho & phân tích luân chuyển SKU',
    ],
    ctaText: 'Dùng thử 30 ngày Miễn phí',
    ctaSubtext: 'Trải nghiệm trọn vẹn đặc quyền Pro',
  },
  {
    id: 'plan-enterprise',
    tier: 'enterprise',
    name: 'Gói Doanh Nghiệp',
    badge: 'Tùy chỉnh & Tích hợp ERP',
    tagline: 'Giải pháp tổng thể cho tổng kho, chuỗi logistics & tập đoàn bán lẻ',
    description: 'Quyền năng tối đa với API kết nối ERP, máy chủ chuyên dụng và cam kết dịch vụ cao nhất.',
    monthlyPrice: 1990000,
    yearlyPrice: 19100000, // Tiết kiệm 20% khi trả theo năm (~1.590.000đ/tháng)
    limits: {
      users: 'Không giới hạn tài khoản',
      warehouses: 'Không giới hạn kho bãi',
      skus: 'Không giới hạn SKU',
      aiRequests: 'Không giới hạn lượt AI',
      support: 'Kỹ sư hỗ trợ 24/7 & Hotline riêng biệt',
      apiAccess: true,
      dataIsolation: 'Tùy chọn cơ sở dữ liệu riêng biệt',
    },
    highlightFeatures: [
      'Bao gồm toàn bộ tính năng của Gói Chuyên Nghiệp',
      'Không giới hạn toàn bộ tài nguyên: User, Kho, SKU & AI',
      'Cung cấp API Key riêng biệt tích hợp phần mềm Kế toán, ERP, POS',
      'Tài liệu Swagger và sandbox kiểm thử tích hợp chuyên sâu',
      'Phân quyền chi tiết nhiều cấp độ và nhật ký truy vết (Audit Log)',
      'Cam kết chất lượng dịch vụ SLA 99.9% uptime',
      'Chuyên viên triển khai & đào tạo nhân sự tận nơi',
      'Sao lưu dữ liệu định kỳ mỗi 6 giờ và khôi phục sự cố tức thì',
    ],
    ctaText: 'Liên hệ Doanh nghiệp',
    ctaSubtext: 'Tư vấn giải pháp riêng biệt',
  },
]

/**
 * Bảng so sánh chi tiết ma trận tính năng giữa các gói
 */
export const FEATURE_COMPARISON_CATEGORIES: FeatureComparisonCategory[] = [
  {
    category: '1. Quy mô & Giới hạn Tài nguyên',
    rows: [
      {
        name: 'Số lượng tài khoản người dùng',
        free: '3 người',
        pro: '50 người',
        enterprise: 'Không giới hạn',
      },
      {
        name: 'Số kho hàng vận hành',
        free: '1 kho',
        pro: '20 kho',
        enterprise: 'Không giới hạn',
      },
      {
        name: 'Giới hạn danh mục sản phẩm (SKU)',
        free: '200 mã',
        pro: 'Không giới hạn',
        enterprise: 'Không giới hạn',
      },
      {
        name: 'Lượt xử lý tài liệu & trợ lý AI',
        free: '20 lượt / tháng',
        pro: '500 lượt / tháng',
        enterprise: 'Không giới hạn',
      },
    ],
  },
  {
    category: '2. Nghiệp vụ Quản trị Kho Vận',
    rows: [
      {
        name: 'Sơ đồ vị trí trực quan (Zone, Rack, Bin)',
        tooltip: 'Theo dõi chi tiết hàng hóa nằm ở từng ngăn ô kệ trong kho',
        free: 'Cơ bản (Zone)',
        pro: 'Toàn diện (Bin-level)',
        enterprise: 'Toàn diện (Bin-level)',
      },
      {
        name: 'Đơn mua hàng (PO) & Phiếu nhập kho (GRN)',
        tooltip: 'Theo dõi đơn đặt hàng nhà cung cấp và kiểm nhận số lượng thực tế',
        free: true,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Đơn bán hàng (SO) & Phiếu xuất kho (GDN)',
        tooltip: 'Quy trình xuất kho theo đơn bán, giữ chỗ hàng và giảm tồn kho',
        free: true,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Điều chuyển hàng giữa các chi nhánh kho',
        tooltip: 'Lập phiếu luân chuyển và quản lý trạng thái đang chuyển (in-transit)',
        free: false,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Kiểm kê định kỳ & Cân đối sai lệch tồn',
        tooltip: 'Hỗ trợ kiểm đếm thực tế, ghi nhận chênh lệch thừa thiếu và lập phiếu điều chỉnh',
        free: false,
        pro: true,
        enterprise: true,
      },
      {
        name: 'In tem nhãn Barcode & Mã QR tiêu chuẩn',
        tooltip: 'Tạo mã vạch và in tem nhãn trực tiếp cho từng SKU hoặc thùng hàng',
        free: false,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Thẻ kho & Lịch sử biến động thời gian thực',
        tooltip: 'Ghi nhận chi tiết từng giao dịch nhập, xuất, chuyển, kiểm của mọi sản phẩm',
        free: true,
        pro: true,
        enterprise: true,
      },
    ],
  },
  {
    category: '3. Trí tuệ Nhân tạo (AI) & Tự động hóa',
    rows: [
      {
        name: 'Trích xuất tự động danh mục SKU từ Excel/PDF/Ảnh',
        tooltip: 'AI quét và bóc tách thông tin sản phẩm từ báo giá hoặc bảng kiểm kê cũ',
        free: '20 dòng/tháng',
        pro: 'Không giới hạn',
        enterprise: 'Không giới hạn',
      },
      {
        name: 'Trợ lý AI hỏi đáp tồn kho & quy trình hệ thống',
        tooltip: 'Hỏi đáp trực tiếp với trợ lý về vị trí tồn, số lượng khả dụng qua ngôn ngữ tự nhiên',
        free: false,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Cảnh báo tự động tồn kho an toàn & cận hạn',
        tooltip: 'Thông báo qua hệ thống khi hàng dưới mức tối thiểu hoặc gần hết date',
        free: 'Cơ bản',
        pro: 'Nâng cao',
        enterprise: 'Tùy biến đa kênh',
      },
    ],
  },
  {
    category: '4. Bảo mật, Tích hợp & Dịch vụ Hỗ trợ',
    rows: [
      {
        name: 'Cách ly dữ liệu đa doanh nghiệp (Multi-tenant)',
        tooltip: 'Dữ liệu từng doanh nghiệp hoàn toàn riêng biệt, bảo mật tuyệt đối',
        free: true,
        pro: true,
        enterprise: true,
      },
      {
        name: 'Tích hợp API kết nối phần mềm ngoài (ERP/POS)',
        tooltip: 'Cung cấp API Key chuẩn RESTful cho bên thứ ba gọi dữ liệu kho',
        free: false,
        pro: false,
        enterprise: true,
      },
      {
        name: 'Phân quyền chi tiết theo vai trò (RBAC)',
        free: '2 vai trò',
        pro: 'Đầy đủ vai trò',
        enterprise: 'Tùy biến không giới hạn',
      },
      {
        name: 'Thời gian hỗ trợ kỹ thuật cam kết',
        free: 'Cộng đồng',
        pro: 'Trong vòng 4 giờ',
        enterprise: 'Ưu tiên 24/7 (SLA 99.9%)',
      },
      {
        name: 'Đào tạo & Hướng dẫn triển khai',
        free: 'Video & Tài liệu',
        pro: 'Họp trực tuyến 1-1',
        enterprise: 'Chuyên viên đào tạo tại chỗ',
      },
    ],
  },
]

/**
 * Câu hỏi thường gặp (FAQ)
 */
export const FAQS: FAQItem[] = [
  {
    id: 'faq-trial',
    question: 'Chương trình dùng thử 30 ngày hoạt động như thế nào?',
    answer:
      'Khi đăng ký tài khoản doanh nghiệp mới, bạn sẽ được tự động kích hoạt 30 ngày dùng thử miễn phí với đầy đủ toàn bộ tính năng và quyền năng của Gói Chuyên Nghiệp (Pro). Bạn không cần nhập bất kỳ thông tin thẻ tín dụng nào trong suốt thời gian dùng thử.',
  },
  {
    id: 'faq-after-trial',
    question: 'Điều gì sẽ xảy ra sau khi hết 30 ngày dùng thử?',
    answer:
      'Khi hết thời gian dùng thử, hệ thống sẽ chuyển sang chế độ an toàn (chỉ đọc) để bảo vệ toàn vẹn dữ liệu của bạn. Bạn có thể gia hạn hoặc nâng cấp gói Pro thông qua chuyển khoản ngân hàng quét mã QR tự động (PayOS), hoặc chuyển về gói Miễn Phí (Free) mà không bao giờ bị mất dữ liệu.',
  },
  {
    id: 'faq-data-safety',
    question: 'Dữ liệu của công ty tôi có bị lộ hoặc nhìn thấy bởi công ty khác không?',
    answer:
      'Tuyệt đối không. Hệ thống được xây dựng trên kiến trúc Multi-Tenant chuẩn doanh nghiệp, mọi bản ghi dữ liệu đều được phân lập logic và bảo vệ chặt chẽ ở tầng mã hóa truy vấn. Công ty A hoàn toàn không có bất kỳ khả năng nào đọc hoặc truy xuất dữ liệu của Công ty B.',
  },
  {
    id: 'faq-payment',
    question: 'Các phương thức thanh toán được hỗ trợ là gì?',
    answer:
      'Chúng tôi hỗ trợ thanh toán linh hoạt và an toàn qua cổng PayOS với mã QR chuyển khoản VietQR tương thích 100% các ngân hàng tại Việt Nam và ví điện tử. Gói dịch vụ của bạn sẽ được tự động kích hoạt ngay sau vài giây kể từ khi giao dịch hoàn tất.',
  },
  {
    id: 'faq-upgrade-downgrade',
    question: 'Tôi có thể nâng cấp hoặc đổi chu kỳ thanh toán bất cứ lúc nào không?',
    answer:
      'Có. Bạn có thể dễ dàng chuyển đổi giữa chu kỳ Hàng tháng và Hàng năm hoặc nâng cấp từ gói Pro lên Enterprise bất cứ lúc nào ngay trong mục Cài đặt Tài khoản của doanh nghiệp.',
  },
  {
    id: 'faq-api-integration',
    question: 'Doanh nghiệp có thể kết nối hệ thống với phần mềm kế toán hoặc ERP đang có không?',
    answer:
      'Hoàn toàn được. Với Gói Doanh Nghiệp (Enterprise), bạn được cấp API Key an toàn kèm bộ tài liệu kỹ thuật Swagger đầy đủ để đội ngũ lập trình viên của bạn tích hợp dữ liệu đơn hàng và tồn kho một cách mượt mà.',
  },
]

/**
 * Tình huống sử dụng minh họa. Tên người và doanh nghiệp là giả định cho đồ án, không phải khách hàng thật.
 */
export const TESTIMONIALS: TestimonialItem[] = [
  {
    id: 'testi-1',
    quote:
      'Nhờ sơ đồ kho trực quan theo từng ô kệ Bin-level và tính năng in mã vạch tự động, thời gian lấy hàng (picking) của kho chúng tôi đã giảm hơn 45% chỉ sau 2 tháng vận hành.',
    author: 'Nguyễn Văn Hùng',
    role: 'Giám đốc Vận hành Kho',
    company: 'Công ty mẫu A (dịch vụ kho vận)',
    rating: 5,
    metric: 'Giảm 45%',
    metricLabel: 'Thời gian lấy và xử lý đơn hàng',
  },
  {
    id: 'testi-2',
    quote:
      'Tính năng trích xuất danh mục hàng hóa bằng AI thật sự là bước đột phá. Đội ngũ chỉ mất chưa đầy 15 phút để đưa hơn 1.200 SKU từ file Excel cũ vào hệ thống mà không hề sai sót.',
    author: 'Trần Thị Mỹ Linh',
    role: 'Trưởng phòng Cung ứng',
    company: 'Công ty mẫu B (chuỗi bán lẻ)',
    rating: 5,
    metric: '10x Nhanh hơn',
    metricLabel: 'Tốc độ số hóa danh mục hàng hóa',
  },
  {
    id: 'testi-3',
    quote:
      'Khả năng quản lý nhiều kho song song và điều chuyển hàng nội bộ minh bạch giúp chúng tôi kiểm soát chính xác 99.8% độ lệch tồn kho trên toàn hệ thống 8 chi nhánh.',
    author: 'Lê Hoàng Nam',
    role: 'Phó Tổng Giám Đốc',
    company: 'Công ty mẫu C (phân phối dược phẩm)',
    rating: 5,
    metric: '99.8%',
    metricLabel: 'Độ chính xác tồn kho thực tế',
  },
]

/**
 * Điểm nổi bật nền tảng WMS SaaS
 */
export const KEY_FEATURES: KeyFeatureItem[] = [
  {
    id: 'feat-multi-warehouse',
    title: 'Quản trị Đa Kho & Sơ đồ Vị trí Ô/Kệ',
    description:
      'Bố trí không gian lưu trữ trực quan theo cấu trúc Zone, Rack và Bin. Dễ dàng nắm bắt vị trí chính xác của từng lô hàng theo thời gian thực.',
    category: 'Vận hành Kho',
    highlights: ['Sơ đồ nhiệt vị trí lưu trữ', 'Gợi ý vị trí cất hàng tối ưu', 'Quản lý đa điểm kho trên toàn quốc'],
    badge: 'Cốt lõi',
  },
  {
    id: 'feat-supply-chain',
    title: 'Tự động hóa Chuỗi Cung ứng Khép kín',
    description:
      'Đồng bộ liền mạch từ Đơn mua hàng (PO) đến Phiếu nhập kho (GRN), từ Đơn bán (SO) đến Phiếu xuất (GDN) và kiểm soát công nợ đối tác.',
    category: 'Chuỗi Cung Ứng',
    highlights: ['Tự động cập nhật công nợ', 'Giữ chỗ tồn kho theo đơn bán', 'Quy trình xét duyệt đa cấp'],
  },
  {
    id: 'feat-ai-extraction',
    title: 'Trí tuệ Nhân tạo & Trợ lý Thông minh',
    description:
      'Chuyển đổi dữ liệu hóa đơn, báo giá PDF và file Excel cũ thành dữ liệu số trong tích tắc. Trợ lý AI sẵn sàng trả lời tình trạng tồn kho tức thì.',
    category: 'Công nghệ AI',
    highlights: ['Trích xuất SKU từ file Excel & PDF', 'Trợ lý tra cứu tồn kho qua ngôn ngữ tự nhiên', 'Cảnh báo tồn kho thông minh'],
    badge: 'Đột phá AI',
  },
  {
    id: 'feat-barcode-label',
    title: 'Mã vạch & Nhận diện Barcode / QR',
    description:
      'Tạo mã vạch chuẩn quốc tế và in tem nhãn tức thì cho từng sản phẩm. Tối ưu thao tác quét mã khi nhập, xuất và kiểm kê hàng hóa.',
    category: 'Công nghệ Mã Vạch',
    highlights: ['Hỗ trợ Code128, EAN-13, QR Code', 'In tem nhãn đa khổ giấy', 'Quy đổi đơn vị linh hoạt'],
  },
  {
    id: 'feat-inventory-control',
    title: 'Kiểm soát Xuất Nhập Tồn & Điều chuyển',
    description:
      'Theo dõi thẻ kho chi tiết từng giây, tự động phát hiện biến động bất thường và hỗ trợ điều chuyển hàng hóa liên kho an toàn.',
    category: 'Quản lý Tồn Kho',
    highlights: ['Thẻ kho thời gian thực', 'Phiếu điều chuyển liên kho', 'Kiểm kê và cân đối kho nhanh chóng'],
  },
  {
    id: 'feat-enterprise-security',
    title: 'Bảo mật Đa Doanh nghiệp & Tích hợp ERP',
    description:
      'Dữ liệu mỗi doanh nghiệp được cô lập logic an toàn tuyệt đối. Cung cấp API Key chuẩn mực cho các hệ thống ERP, POS, CRM kết nối dễ dàng.',
    category: 'Hạ tầng & Bảo mật',
    highlights: ['Kiến trúc Multi-tenant an toàn', 'Cổng API Key tích hợp chuyên nghiệp', 'Sao lưu dữ liệu tự động'],
    badge: 'Bảo mật Cao',
  },
]

/**
 * Thông tin nổi bật của nền tảng (đúng với phạm vi sản phẩm, không dùng số liệu khách hàng giả định)
 */
export const PLATFORM_STATS: PlatformStat[] = [
  {
    value: '30 ngày',
    label: 'Dùng thử Gói Chuyên Nghiệp',
    sublabel: 'Không cần thẻ tín dụng',
  },
  {
    value: '6',
    label: 'Vai trò phân quyền',
    sublabel: 'Từ quản trị hệ thống đến nhân viên kho',
  },
  {
    value: '3 cấp',
    label: 'Vị trí lưu trữ Zone, Rack, Bin',
    sublabel: 'Theo dõi hàng hóa tới từng ô kệ',
  },
  {
    value: 'Tức thì',
    label: 'Cảnh báo tồn kho và đơn hàng',
    sublabel: 'Thông báo thời gian thực trên giao diện',
  },
]

