package vn.wms.auth.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.wms.auth.payment.dto.CreatePaymentRequest;
import vn.wms.auth.payment.dto.PaymentResponse;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;
import vn.wms.auth.payment.payos.PayOsClient;
import vn.wms.auth.payment.payos.PayOsProperties;
import vn.wms.auth.payment.payos.PayOsSignature;
import vn.wms.auth.payment.repository.PaymentRepository;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.repository.PlanRepository;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thanh toán gói dịch vụ qua PayOS (docs/SAAS_SPEC.md, WMS-23.5a).
 *
 * <p>Gói chỉ được kích hoạt bởi webhook có chữ ký hợp lệ (hoặc mock-confirm khi {@code PAYOS_MOCK=true}); tham số
 * PayOS gắn vào địa chỉ trả về trên trình duyệt chỉ dùng để hiển thị. Việc kích hoạt dùng chung
 * {@link SubscriptionService#activate} với renew-mock, nên công thức gia hạn và bộ nhớ đệm Redis giống hệt.
 */
@Slf4j
@Service
public class PaymentService {

    /** Liên kết thanh toán hết hạn sau 15 phút. */
    public static final Duration LINK_TTL = Duration.ofMinutes(15);
    /** PayOS gửi webhook mẫu với orderCode 123 khi đăng ký địa chỉ webhook (confirm-webhook). */
    public static final long PAYOS_SAMPLE_ORDER_CODE = 123L;
    /** Số nguyên lớn nhất PayOS nhận cho orderCode (Number.MAX_SAFE_INTEGER của JavaScript). */
    public static final long MAX_ORDER_CODE = 9_007_199_254_740_991L;
    /** PayOS giới hạn nội dung chuyển khoản 25 ký tự. */
    static final int MAX_DESCRIPTION_LENGTH = 25;
    private static final int MAX_ORDER_CODE_ATTEMPTS = 5;
    private static final String PAYOS_SUCCESS_CODE = "00";
    private static final String INVALID_MESSAGE = "Dữ liệu thanh toán không hợp lệ";

    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final PayOsClient payOsClient;
    private final PayOsProperties payOsProperties;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(PaymentRepository paymentRepository, PlanRepository planRepository,
                          SubscriptionService subscriptionService, PayOsClient payOsClient,
                          PayOsProperties payOsProperties, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
        this.payOsClient = payOsClient;
        this.payOsProperties = payOsProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Tạo giao dịch PENDING và liên kết thanh toán. Số tiền lấy từ bảng {@code plans}, không bao giờ từ client.
     * Không bọc cả hàm trong một giao dịch: bản ghi PENDING được lưu trước (thử lại nếu trùng orderCode), PayOS được
     * gọi ngoài giao dịch để không giữ kết nối CSDL trong lúc chờ mạng, rồi mới ghi dữ liệu liên kết.
     */
    public PaymentResponse create(Long companyId, Long userId, CreatePaymentRequest request) {
        String planCode = request.plan().trim().toUpperCase();
        if (!SubscriptionService.PLAN_PRO.equals(planCode)) {
            throw ApiException.badRequest("Chỉ gói Chuyên Nghiệp (PRO) được thanh toán");
        }
        BillingCycle cycle = request.cycle();
        Plan plan = planRepository.findById(planCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói dịch vụ: " + planCode));
        long amount = priceOf(plan, cycle);

        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(LINK_TTL);
        Payment payment = insertPending(companyId, userId, planCode, cycle, amount, expiresAt);

        PayOsClient.PaymentLink link;
        try {
            link = payOsClient.createPaymentLink(new PayOsClient.PaymentLinkRequest(
                    payment.getOrderCode(), amount, descriptionOf(planCode, payment.getOrderCode()), expiresAt));
        } catch (RuntimeException e) {
            // Không tạo được liên kết: đóng giao dịch để nó không treo ở PENDING
            markStatus(payment.getId(), PaymentStatus.CANCELLED);
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            log.warn("Lỗi không lường trước khi tạo liên kết PayOS cho đơn {}: {}", payment.getOrderCode(),
                    e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không tạo được liên kết thanh toán, vui lòng thử lại sau");
        }

        Payment saved = transactionTemplate.execute(status -> {
            Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
            current.setCheckoutUrl(link.checkoutUrl());
            current.setQrCode(link.qrCode());
            current.setPaymentLinkId(link.paymentLinkId());
            return paymentRepository.save(current);
        });
        log.info("Đã tạo giao dịch thanh toán {} cho công ty ID {}: gói {} {}, {} VND",
                saved.getOrderCode(), companyId, planCode, cycle, amount);
        return PaymentResponse.from(saved);
    }

    /**
     * Xử lý webhook PayOS. Thứ tự: kiểm tra chữ ký bằng checksum key, đọc orderCode và số tiền, khóa dòng giao dịch
     * ({@code PESSIMISTIC_WRITE}), đối chiếu số tiền, rồi trong cùng giao dịch này chuyển PAID và kích hoạt gói.
     * Mọi trường hợp không hợp lệ ném {@code PAYMENT_INVALID} (400) và không thay đổi gì.
     */
    @Transactional
    public void handleWebhook(JsonNode body) {
        if (!payOsProperties.hasChecksumKey()) {
            // Không có khóa thì không thể xác minh webhook nào, kể cả ở chế độ giả lập (dùng mock-confirm thay thế)
            log.warn("Từ chối webhook PayOS: chưa cấu hình PAYOS_CHECKSUM_KEY");
            throw invalid();
        }
        JsonNode data = body == null ? null : body.get("data");
        String signature = body == null ? null : body.path("signature").asText(null);
        if (data == null || !data.isObject() || signature == null) {
            log.warn("Từ chối webhook PayOS: thiếu data hoặc signature");
            throw invalid();
        }
        if (!PayOsSignature.verifyWebhook(data, signature, payOsProperties.checksumKey())) {
            log.warn("Từ chối webhook PayOS: sai chữ ký (orderCode={})", data.path("orderCode").asText());
            throw invalid();
        }

        JsonNode orderCodeNode = data.get("orderCode");
        JsonNode amountNode = data.get("amount");
        if (orderCodeNode == null || !orderCodeNode.isIntegralNumber()
                || amountNode == null || !amountNode.isIntegralNumber()) {
            log.warn("Từ chối webhook PayOS: orderCode hoặc amount không phải số nguyên");
            throw invalid();
        }
        long orderCode = orderCodeNode.asLong();
        long amount = amountNode.asLong();

        String code = data.hasNonNull("code") ? data.get("code").asText() : body.path("code").asText(PAYOS_SUCCESS_CODE);
        if (!PAYOS_SUCCESS_CODE.equals(code)) {
            // Thông báo hợp lệ nhưng không phải giao dịch thành công: ghi nhận, không đổi gì
            log.info("Webhook PayOS cho đơn {} có code={}, không kích hoạt gói", orderCode, code);
            return;
        }

        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (payment == null) {
            if (orderCode == PAYOS_SAMPLE_ORDER_CODE) {
                log.info("Nhận webhook mẫu của PayOS (orderCode {}) khi đăng ký địa chỉ webhook", orderCode);
                return;
            }
            log.warn("Từ chối webhook PayOS: không có giao dịch với orderCode {}", orderCode);
            throw invalid();
        }
        String reference = data.hasNonNull("reference") ? data.get("reference").asText() : null;
        confirmPaid(payment, amount, reference, "webhook");
    }

    /**
     * Xác nhận giả lập (chỉ khi {@code PAYOS_MOCK=true}): chạy đúng đường xử lý của một webhook hợp lệ cho đơn của
     * doanh nghiệp mình, để nhóm test trọn luồng mà không cần PayOS. Tắt giả lập hoặc đơn của doanh nghiệp khác: 404.
     */
    @Transactional
    public PaymentResponse mockConfirm(Long companyId, Long orderCode) {
        if (!payOsProperties.mock()) {
            throw ApiException.notFound("Chức năng xác nhận thanh toán giả lập không khả dụng trên môi trường này");
        }
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(PaymentService::paymentNotFound);
        confirmPaid(payment, payment.getAmount(), "MOCK-" + orderCode, "mock-confirm");
        return PaymentResponse.from(payment);
    }

    /** Một giao dịch của doanh nghiệp mình; giao dịch PENDING đã quá hạn được chuyển EXPIRED trước khi trả. */
    @Transactional
    public PaymentResponse get(Long companyId, Long orderCode) {
        expireOverdue(companyId);
        return paymentRepository.findByOrderCodeAndCompanyId(orderCode, companyId)
                .map(PaymentResponse::from)
                .orElseThrow(PaymentService::paymentNotFound);
    }

    /** Lịch sử giao dịch của doanh nghiệp, mới nhất trước. */
    @Transactional
    public PageResponse<PaymentResponse> list(Long companyId, int page, int size) {
        expireOverdue(companyId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Payment> result = paymentRepository.findByCompanyId(companyId, pageRequest);
        return PageResponse.from(result.map(PaymentResponse::from));
    }

    /** Người dùng hủy ở trang PayOS: PENDING thành CANCELLED; trạng thái khác trả 409. */
    @Transactional
    public PaymentResponse cancel(Long companyId, Long orderCode) {
        expireOverdue(companyId);
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(PaymentService::paymentNotFound);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict(switch (payment.getStatus()) {
                case PAID -> "Giao dịch đã thanh toán, không thể hủy";
                case EXPIRED -> "Giao dịch đã hết hạn";
                default -> "Giao dịch đã được hủy trước đó";
            });
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /**
     * Đường xác nhận dùng chung cho webhook và mock-confirm. Dòng giao dịch đã bị khóa bởi người gọi.
     * Gửi lặp cho đơn đã PAID: không làm gì (không cộng thêm ngày).
     */
    private void confirmPaid(Payment payment, long amount, String reference, String source) {
        if (payment.getAmount() != amount) {
            log.warn("Từ chối xác nhận thanh toán đơn {} ({}): số tiền không khớp", payment.getOrderCode(), source);
            throw invalid();
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Đơn {} đã PAID, bỏ qua {} gửi lặp", payment.getOrderCode(), source);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            // Đơn đã CANCELLED hoặc EXPIRED nhưng PayOS vẫn báo đã nhận tiền (ví dụ người dùng trả tiền sát lúc hết
            // hạn hoặc sau khi bấm hủy): tiền đã vào tài khoản nên vẫn coi là đã thanh toán và kích hoạt gói.
            log.info("Đơn {} đang {} nhưng nhận xác nhận đã trả tiền ({}): vẫn kích hoạt gói",
                    payment.getOrderCode(), payment.getStatus(), source);
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setReference(reference);
        payment.setPaidAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        paymentRepository.save(payment);
        // Cùng giao dịch: kích hoạt lỗi thì cả trạng thái PAID cũng được hoàn tác; bộ nhớ đệm ghi sau khi commit
        subscriptionService.activate(payment.getCompanyId(), payment.getPlanCode(), payment.getCycle());
        log.info("Đơn {} đã thanh toán ({}), kích hoạt gói {} {} cho công ty ID {}", payment.getOrderCode(), source,
                payment.getPlanCode(), payment.getCycle(), payment.getCompanyId());
    }

    private Payment insertPending(Long companyId, Long userId, String planCode, BillingCycle cycle, long amount,
                                  Instant expiresAt) {
        for (int attempt = 1; ; attempt++) {
            long orderCode = nextOrderCode();
            try {
                return transactionTemplate.execute(status -> paymentRepository.saveAndFlush(Payment.builder()
                        .companyId(companyId)
                        .orderCode(orderCode)
                        .planCode(planCode)
                        .cycle(cycle)
                        .amount(amount)
                        .status(PaymentStatus.PENDING)
                        .expiresAt(expiresAt)
                        .createdByUser(userId)
                        .build()));
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_ORDER_CODE_ATTEMPTS) {
                    throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không sinh được mã đơn thanh toán, vui lòng thử lại");
                }
                log.info("Mã đơn {} bị trùng, sinh lại (lần {})", orderCode, attempt);
            }
        }
    }

    private void markStatus(Long paymentId, PaymentStatus status) {
        transactionTemplate.executeWithoutResult(tx -> paymentRepository.findById(paymentId).ifPresent(p -> {
            p.setStatus(status);
            paymentRepository.save(p);
        }));
    }

    private void expireOverdue(Long companyId) {
        paymentRepository.expireOverdue(companyId, Instant.now(), PaymentStatus.PENDING, PaymentStatus.EXPIRED);
    }

    /**
     * Mã đơn: mili giây epoch nhân 1000 cộng ba chữ số ngẫu nhiên, ví dụ {@code 1762761600123457}
     * (16 chữ số, nhỏ hơn giới hạn 9007199254740991 của PayOS tới khoảng năm 2255). Trùng thì sinh lại.
     */
    static long nextOrderCode() {
        long code = System.currentTimeMillis() * 1000L + ThreadLocalRandom.current().nextInt(1000);
        if (code <= 0 || code > MAX_ORDER_CODE) {
            throw new IllegalStateException("Mã đơn vượt giới hạn của PayOS");
        }
        return code;
    }

    /** Nội dung chuyển khoản, ví dụ {@code WMS PRO 1762761600123457} (tối đa 25 ký tự). */
    static String descriptionOf(String planCode, long orderCode) {
        String description = "WMS " + planCode + " " + orderCode;
        return description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH) : description;
    }

    /** Giá theo chu kỳ, đổi từ DECIMAL(18,2) sang số nguyên VND. */
    private static long priceOf(Plan plan, BillingCycle cycle) {
        BigDecimal price = cycle == BillingCycle.YEARLY ? plan.getPriceYearly() : plan.getPriceMonthly();
        if (price == null || price.signum() <= 0) {
            throw ApiException.conflict("Gói " + plan.getCode() + " chưa có giá cho chu kỳ " + cycle);
        }
        return price.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.PAYMENT_INVALID, INVALID_MESSAGE);
    }

    private static ApiException paymentNotFound() {
        return ApiException.notFound("Không tìm thấy giao dịch thanh toán");
    }
}
