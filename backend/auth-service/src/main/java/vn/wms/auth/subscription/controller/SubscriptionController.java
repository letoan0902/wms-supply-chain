package vn.wms.auth.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.auth.subscription.dto.RenewMockRequest;
import vn.wms.auth.subscription.dto.SubscriptionResponse;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.exception.ApiException;

@RestController
@RequestMapping("/api/auth/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Quản lý thuê bao và gói dịch vụ của doanh nghiệp")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Value("${payos.mock:true}")
    private boolean payosMock;

    @Value("${app.demo-tools:false}")
    private boolean demoTools;

    @GetMapping
    @Operation(summary = "Xem thông tin gói dịch vụ hiện tại, hạn sử dụng và hạn mức của doanh nghiệp")
    public ApiResponse<SubscriptionResponse> getCurrentSubscription(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getCompanyId() == null) {
            throw ApiException.badRequest("Tài khoản không thuộc doanh nghiệp nào");
        }
        SubscriptionResponse response = subscriptionService.getCurrent(principal.getCompanyId());
        return ApiResponse.ok(response);
    }

    @PostMapping("/renew-mock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gia hạn giả lập gói Chuyên Nghiệp (PRO) phục vụ kiểm thử và phát triển")
    public ApiResponse<Void> renewMock(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RenewMockRequest request) {
        if (!payosMock) {
            throw ApiException.notFound("Chức năng gia hạn giả lập không khả dụng trên môi trường này");
        }
        if (principal == null || principal.getCompanyId() == null) {
            throw ApiException.badRequest("Tài khoản không thuộc doanh nghiệp nào");
        }
        subscriptionService.activate(principal.getCompanyId(), request.getPlan(), request.getCycle());
        return ApiResponse.ok();
    }

    @PostMapping("/downgrade")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chuyển về gói Khởi Đầu (FREE) khi đang dùng thử hoặc đã hết hạn")
    public ApiResponse<Void> downgrade(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getCompanyId() == null) {
            throw ApiException.badRequest("Tài khoản không thuộc doanh nghiệp nào");
        }
        subscriptionService.downgradeToFree(principal.getCompanyId());
        return ApiResponse.ok();
    }

    @PostMapping("/demo/expire")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chuyển thuê bao của công ty thành hết hạn ngay lập tức để trình diễn (chỉ bật khi APP_DEMO_TOOLS=true)")
    public ApiResponse<Void> demoExpire(@AuthenticationPrincipal UserPrincipal principal) {
        if (!demoTools) {
            throw ApiException.notFound("Công cụ demo không khả dụng");
        }
        if (principal == null || principal.getCompanyId() == null) {
            throw ApiException.badRequest("Tài khoản không thuộc doanh nghiệp nào");
        }
        subscriptionService.demoExpire(principal.getCompanyId());
        return ApiResponse.ok();
    }
}
