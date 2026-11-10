package vn.wms.auth.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.subscription.dto.PlanResponse;
import vn.wms.auth.subscription.repository.PlanRepository;
import vn.wms.common.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/auth/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Danh mục và bảng giá các gói dịch vụ")
public class PlanController {

    private final PlanRepository planRepository;

    @GetMapping
    @Operation(summary = "Xem danh sách các gói dịch vụ, giá niêm yết và hạn mức")
    public ApiResponse<List<PlanResponse>> getPlans() {
        List<PlanResponse> list = planRepository.findAll().stream()
                .map(PlanResponse::from)
                .toList();
        return ApiResponse.ok(list);
    }
}
