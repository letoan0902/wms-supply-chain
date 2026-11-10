package vn.wms.auth.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.payment.dto.CreatePaymentRequest;
import vn.wms.auth.payment.dto.PaymentResponse;
import vn.wms.auth.payment.service.PaymentService;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

/**
 * Thanh toán gói dịch vụ qua PayOS (docs/SAAS_SPEC.md, WMS-23.5a). Mọi API trừ webhook chỉ dành cho ADMIN và chỉ thấy
 * giao dịch của doanh nghiệp mình; giao dịch của doanh nghiệp khác trả 404.
 */
@RestController
@RequestMapping("/api/auth/payments")
@Tag(name = "Payment", description = "Thanh toán gói dịch vụ qua PayOS")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo giao dịch và liên kết thanh toán PayOS (số tiền lấy từ bảng gói, liên kết hết hạn sau 15 phút)")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request,
                                               Authentication authentication) {
        Long companyId = UserPrincipal.requireCompanyId(authentication);
        Long userId = authentication.getPrincipal() instanceof UserPrincipal principal ? principal.getId() : null;
        return ApiResponse.ok(paymentService.create(companyId, userId, request));
    }

    @PostMapping("/payos/webhook")
    @Operation(summary = "Webhook của PayOS (công khai, xác thực bằng chữ ký HMAC-SHA256 với checksum key)")
    public ApiResponse<Void> webhook(@RequestBody(required = false) JsonNode body) {
        paymentService.handleWebhook(body);
        return ApiResponse.ok();
    }

    @PostMapping("/{orderCode}/mock-confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xác nhận thanh toán giả lập như một webhook hợp lệ (chỉ khi PAYOS_MOCK=true)")
    public ApiResponse<PaymentResponse> mockConfirm(@PathVariable Long orderCode, Authentication authentication) {
        return ApiResponse.ok(paymentService.mockConfirm(UserPrincipal.requireCompanyId(authentication), orderCode));
    }

    @GetMapping("/{orderCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trạng thái một giao dịch của doanh nghiệp mình (trang kết quả thanh toán hỏi lại)")
    public ApiResponse<PaymentResponse> get(@PathVariable Long orderCode, Authentication authentication) {
        return ApiResponse.ok(paymentService.get(UserPrincipal.requireCompanyId(authentication), orderCode));
    }

    @PostMapping("/{orderCode}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hủy giao dịch đang chờ thanh toán (PENDING thành CANCELLED)")
    public ApiResponse<PaymentResponse> cancel(@PathVariable Long orderCode, Authentication authentication) {
        return ApiResponse.ok(paymentService.cancel(UserPrincipal.requireCompanyId(authentication), orderCode));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lịch sử giao dịch của doanh nghiệp, mới nhất trước, có phân trang")
    public ApiResponse<PageResponse<PaymentResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size,
                                                           Authentication authentication) {
        return ApiResponse.ok(paymentService.list(UserPrincipal.requireCompanyId(authentication), page, size));
    }
}
