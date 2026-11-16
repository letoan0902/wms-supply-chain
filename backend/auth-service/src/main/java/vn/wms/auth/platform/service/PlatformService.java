package vn.wms.auth.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;
import vn.wms.auth.payment.repository.PaymentRepository;
import vn.wms.auth.platform.dto.PlatformCompanyDetailResponse;
import vn.wms.auth.platform.dto.PlatformCompanySummary;
import vn.wms.auth.platform.dto.PlatformOverviewResponse;
import vn.wms.auth.platform.dto.PlatformPayment;
import vn.wms.auth.platform.dto.UpdateCompanyStatusRequest;
import vn.wms.auth.platform.repository.PlatformCompanyRepository;
import vn.wms.auth.platform.repository.PlatformPaymentRepository;
import vn.wms.auth.platform.repository.PlatformSubscriptionRepository;
import vn.wms.auth.platform.repository.PlatformUserRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.PlanRepository;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.auth.subscription.service.SubscriptionUsageReader;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ quản trị nền tảng (docs/SAAS_SPEC.md mục 6): thống kê toàn nền tảng, danh sách và chi tiết doanh nghiệp
 * khách hàng, tạm khóa và mở khóa doanh nghiệp, danh sách thanh toán.
 *
 * <p>Đây là chỗ duy nhất của auth-service cố ý đọc dữ liệu của mọi doanh nghiệp. Bảng của auth-service không dùng
 * {@code TenantEntity} (docs/MULTI_TENANT.md mục 7), nên không có bộ lọc nào phải tắt: mọi truy vấn xuyên doanh nghiệp
 * nằm trong bốn repository {@code Platform*Repository}, viết tường minh và luôn loại doanh nghiệp hệ thống
 * {@code PLATFORM}. Controller chỉ cho {@code SUPER_ADMIN} gọi.
 *
 * <p>Số liệu theo tháng được gom trong Java từ các cột cần thiết (không dùng hàm ngày của cơ sở dữ liệu) để chạy giống
 * nhau trên MySQL và H2; số doanh nghiệp và số thanh toán nhỏ nên việc này rẻ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformService {

    /** Múi giờ tính tháng và ngày (giờ Việt Nam). */
    static final ZoneId ZONE = SubscriptionCacheKeys.BUSINESS_ZONE;
    static final int MONTHS = 6;
    static final int RECENT_LIMIT = 5;
    static final int DETAIL_PAYMENT_LIMIT = 10;
    static final int MAX_PAGE_SIZE = 100;
    static final Duration EXPIRING_SOON = Duration.ofDays(7);

    /** Mốc thay cho "không giới hạn" của bộ lọc ngày, nằm trong khoảng DATETIME của MySQL. */
    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-01-01T00:00:00Z");

    private static final Sort COMPANY_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort PAYMENT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort PAID_SORT = Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("id"));

    private final PlatformCompanyRepository companyRepository;
    private final PlatformSubscriptionRepository platformSubscriptionRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformPaymentRepository platformPaymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final SubscriptionUsageReader usageReader;

    private final Clock clock = Clock.system(ZONE);

    // ==========================================
    // 3.1 Tổng quan
    // ==========================================

    @Transactional(readOnly = true)
    public PlatformOverviewResponse overview() {
        Instant now = Instant.now(clock);
        YearMonth currentMonth = YearMonth.now(clock);
        List<YearMonth> months = new ArrayList<>(MONTHS);
        for (int i = MONTHS - 1; i >= 0; i--) {
            months.add(currentMonth.minusMonths(i));
        }

        // Doanh nghiệp theo trạng thái và theo tháng đăng ký
        long total = 0;
        long active = 0;
        long pending = 0;
        long suspended = 0;
        Map<YearMonth, Long> registrations = zeroBuckets(months);
        for (Object[] row : companyRepository.findStatusAndCreatedAtOfCustomers()) {
            total++;
            CompanyStatus status = (CompanyStatus) row[0];
            if (status == CompanyStatus.ACTIVE) {
                active++;
            } else if (status == CompanyStatus.PENDING) {
                pending++;
            } else if (status == CompanyStatus.SUSPENDED) {
                suspended++;
            }
            addToBucket(registrations, (Instant) row[1], 1L);
        }

        // Thuê bao theo gói, đã hết hạn, sắp hết hạn
        long free = 0;
        long trial = 0;
        long pro = 0;
        long expired = 0;
        long expiringSoon = 0;
        Instant soon = now.plus(EXPIRING_SOON);
        for (Subscription subscription : platformSubscriptionRepository.findAllOfCustomers()) {
            switch (subscription.getPlan().getCode()) {
                case SubscriptionService.PLAN_FREE -> free++;
                case SubscriptionService.PLAN_TRIAL -> trial++;
                case SubscriptionService.PLAN_PRO -> pro++;
                default -> { }
            }
            if (subscription.getStatus() == SubscriptionStatus.EXPIRED) {
                expired++;
            } else {
                Instant expiry = SubscriptionCacheWriter.effectiveExpiry(subscription);
                if (expiry != null && expiry.isAfter(now) && !expiry.isAfter(soon)) {
                    expiringSoon++;
                }
            }
        }

        // Doanh thu: chỉ thanh toán PAID, theo thời điểm thanh toán
        long revenueTotal = 0;
        long revenueThisMonth = 0;
        long paidCount = 0;
        Map<YearMonth, Long> revenue = zeroBuckets(months);
        for (Object[] row : platformPaymentRepository.findAmountAndPaidAtByStatus(PaymentStatus.PAID)) {
            long amount = row[0] != null ? (Long) row[0] : 0L;
            Instant paidAt = (Instant) row[1];
            revenueTotal += amount;
            paidCount++;
            if (paidAt != null && YearMonth.from(paidAt.atZone(ZONE)).equals(currentMonth)) {
                revenueThisMonth += amount;
            }
            addToBucket(revenue, paidAt, amount);
        }

        List<Company> recentCompanies = companyRepository
                .search(null, null, null, PageRequest.of(0, RECENT_LIMIT, COMPANY_SORT)).getContent();
        List<Payment> recentPayments = platformPaymentRepository
                .findByStatus(PaymentStatus.PAID, PageRequest.of(0, RECENT_LIMIT, PAID_SORT));

        return new PlatformOverviewResponse(
                new PlatformOverviewResponse.Companies(total, active, pending, suspended),
                new PlatformOverviewResponse.Subscriptions(free, trial, pro, expired, expiringSoon),
                new PlatformOverviewResponse.Users(platformUserRepository.countCustomerUsers(),
                        platformUserRepository.countEnabledCustomerUsers()),
                new PlatformOverviewResponse.Revenue(revenueTotal, revenueThisMonth, paidCount),
                registrations.entrySet().stream()
                        .map(e -> new PlatformOverviewResponse.MonthCount(e.getKey().toString(), e.getValue()))
                        .toList(),
                revenue.entrySet().stream()
                        .map(e -> new PlatformOverviewResponse.MonthAmount(e.getKey().toString(), e.getValue()))
                        .toList(),
                toSummaries(recentCompanies),
                toPayments(recentPayments));
    }

    // ==========================================
    // 3.2, 3.3 Doanh nghiệp
    // ==========================================

    @Transactional(readOnly = true)
    public PageResponse<PlatformCompanySummary> companies(String keyword, CompanyStatus status, String plan,
                                                          int page, int size) {
        String planCode = normalizePlan(plan);
        String pattern = keyword == null || keyword.isBlank()
                ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Company> result = companyRepository.search(status, pattern, planCode, pageRequest(page, size, COMPANY_SORT));
        List<PlatformCompanySummary> content = toSummaries(result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public PlatformCompanyDetailResponse companyDetail(Long id) {
        Company company = companyRepository.findCustomerById(id).orElseThrow(PlatformService::companyNotFound);
        Subscription subscription = subscriptionRepository.findByCompanyId(id).orElse(null);
        PlatformCompanySummary summary = toSummaries(List.of(company)).get(0);

        PlatformCompanyDetailResponse.Limits limits = null;
        PlatformCompanyDetailResponse.Usage usage = null;
        if (subscription != null) {
            limits = PlatformCompanyDetailResponse.Limits.of(subscription.getPlan());
            usage = readUsage(id);
        }

        List<PlatformCompanyDetailResponse.Admin> admins = platformUserRepository.findAdmins(id).stream()
                .map(PlatformCompanyDetailResponse.Admin::of)
                .toList();
        List<PlatformPayment> payments = paymentRepository
                .findByCompanyId(id, PageRequest.of(0, DETAIL_PAYMENT_LIMIT, PAYMENT_SORT))
                .map(payment -> PlatformPayment.of(payment, company))
                .getContent();

        return new PlatformCompanyDetailResponse(
                PlatformCompanyDetailResponse.CompanyInfo.of(summary, company.getAddress()),
                limits, usage, admins, payments);
    }

    /**
     * Tạm khóa (ACTIVE thành SUSPENDED) hoặc mở khóa (SUSPENDED thành ACTIVE) một doanh nghiệp khách hàng. Người dùng
     * của doanh nghiệp bị khóa không đăng nhập và không làm mới token được ({@code AuthService}). Doanh nghiệp PENDING
     * chỉ kích hoạt qua liên kết trong email. Lý do chỉ ghi vào log.
     */
    @Transactional
    public PlatformCompanySummary updateStatus(Long id, UpdateCompanyStatusRequest request, String actor) {
        Company company = companyRepository.findCustomerByIdForUpdate(id).orElseThrow(PlatformService::companyNotFound);
        CompanyStatus from = company.getStatus();
        CompanyStatus to = request.status();
        if (to == CompanyStatus.SUSPENDED) {
            if (from != CompanyStatus.ACTIVE) {
                throw ApiException.badRequest(from == CompanyStatus.SUSPENDED
                        ? "Doanh nghiệp đang bị tạm khóa"
                        : "Chỉ tạm khóa được doanh nghiệp đang hoạt động (ACTIVE); doanh nghiệp đang ở trạng thái "
                                + from);
            }
        } else if (to == CompanyStatus.ACTIVE) {
            if (from != CompanyStatus.SUSPENDED) {
                throw ApiException.badRequest(from == CompanyStatus.PENDING
                        ? "Doanh nghiệp chưa kích hoạt (PENDING) chỉ kích hoạt được qua liên kết trong email đăng ký"
                        : "Doanh nghiệp đang hoạt động, không cần mở khóa");
            }
        } else {
            throw ApiException.badRequest(
                    "Chỉ chuyển được sang trạng thái SUSPENDED (tạm khóa) hoặc ACTIVE (mở khóa)");
        }

        company.setStatus(to);
        log.info("Quản trị nền tảng {} chuyển doanh nghiệp {} (companyId={}) từ {} sang {}, lý do: {}",
                sanitize(actor), company.getCode(), company.getId(), from, to, sanitize(request.reason()));
        return toSummaries(List.of(company)).get(0);
    }

    // ==========================================
    // 3.5 Thanh toán
    // ==========================================

    /**
     * Thanh toán của mọi doanh nghiệp khách hàng, lọc theo trạng thái, doanh nghiệp và ngày tạo (giờ Việt Nam, gồm cả
     * hai đầu). Giao dịch PENDING đã quá hạn được chuyển EXPIRED trước khi trả, như lịch sử của từng doanh nghiệp.
     */
    @Transactional
    public PageResponse<PlatformPayment> payments(PaymentStatus status, Long companyId, LocalDate from,
                                                  LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("Ngày bắt đầu (from) không được sau ngày kết thúc (to)");
        }
        Instant fromInclusive = from != null ? from.atStartOfDay(ZONE).toInstant() : MIN_INSTANT;
        Instant toExclusive = to != null ? to.plusDays(1).atStartOfDay(ZONE).toInstant() : MAX_INSTANT;

        platformPaymentRepository.expireAllOverdue(Instant.now(), PaymentStatus.PENDING, PaymentStatus.EXPIRED);
        Page<Payment> result = platformPaymentRepository.search(status, companyId, fromInclusive, toExclusive,
                pageRequest(page, size, PAYMENT_SORT));
        return new PageResponse<>(toPayments(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    // ==========================================
    // Hỗ trợ
    // ==========================================

    /** Dựng danh sách tóm tắt, nạp thuê bao và số tài khoản của cả trang bằng hai câu truy vấn. */
    private List<PlatformCompanySummary> toSummaries(List<Company> companies) {
        if (companies.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = companies.stream().map(Company::getId).collect(Collectors.toSet());
        Map<Long, Subscription> subscriptions = platformSubscriptionRepository.findByCompanyIds(ids).stream()
                .collect(Collectors.toMap(s -> s.getCompany().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Long> userCounts = platformUserRepository.countByCompanyIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return companies.stream()
                .map(c -> PlatformCompanySummary.of(c, subscriptions.get(c.getId()),
                        userCounts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** Dựng danh sách thanh toán kèm mã và tên doanh nghiệp (một câu truy vấn cho cả trang). */
    private List<PlatformPayment> toPayments(List<Payment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Set<Long> companyIds = payments.stream().map(Payment::getCompanyId).collect(Collectors.toSet());
        Map<Long, Company> companies = companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        return payments.stream()
                .map(p -> PlatformPayment.of(p, companies.get(p.getCompanyId())))
                .toList();
    }

    /**
     * Mức dùng của một doanh nghiệp bất kỳ: số tài khoản đang bật đếm trong cơ sở dữ liệu; kho, SKU, lượt AI đọc từ
     * Redis qua {@link SubscriptionUsageReader} (theo id doanh nghiệp, không phụ thuộc người đang đăng nhập). Số nào
     * không đọc được thì {@code null}, không làm hỏng API.
     */
    private PlatformCompanyDetailResponse.Usage readUsage(Long companyId) {
        long users = userRepository.countByCompanyIdAndEnabledTrue(companyId);
        SubscriptionUsageReader.Usage usage;
        try {
            usage = usageReader.read(companyId);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được mức dùng của doanh nghiệp {}: {}", companyId, ex.getMessage());
            usage = new SubscriptionUsageReader.Usage(null, null, null);
        }
        return new PlatformCompanyDetailResponse.Usage(users, usage.warehouses(), usage.skus(), usage.aiRequests());
    }

    private String normalizePlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return null;
        }
        String code = plan.trim().toUpperCase(Locale.ROOT);
        if (!planRepository.existsById(code)) {
            throw ApiException.badRequest("Gói dịch vụ không hợp lệ: " + plan.trim());
        }
        return code;
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private static Map<YearMonth, Long> zeroBuckets(List<YearMonth> months) {
        Map<YearMonth, Long> buckets = new LinkedHashMap<>();
        months.forEach(month -> buckets.put(month, 0L));
        return buckets;
    }

    /** Cộng {@code value} vào tháng (giờ Việt Nam) của {@code instant} nếu tháng đó nằm trong các tháng đang xét. */
    private static void addToBucket(Map<YearMonth, Long> buckets, Instant instant, long value) {
        if (instant == null) {
            return;
        }
        buckets.computeIfPresent(YearMonth.from(instant.atZone(ZONE)), (month, current) -> current + value);
    }

    /** Bỏ ký tự xuống dòng trong chuỗi do người dùng nhập trước khi ghi log. */
    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
    }

    private static ApiException companyNotFound() {
        return ApiException.notFound("Không tìm thấy doanh nghiệp");
    }
}
