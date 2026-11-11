package vn.wms.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AiHealthResponse;
import vn.wms.ai.dto.AiUsageResponse;
import vn.wms.ai.dto.AskRequest;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.ChatRequest;
import vn.wms.ai.dto.ChatResponse;
import vn.wms.ai.dto.SummaryResponse;
import vn.wms.ai.service.AiAskService;
import vn.wms.ai.service.AiChatService;
import vn.wms.ai.service.AiSummaryService;
import vn.wms.ai.service.AiQuotaService;
import vn.wms.common.api.ApiResponse;

/**
 * API của ai-service (WMS-24.1, trợ lý AI WMS-24.4, tóm tắt dashboard WMS-18.1). Mọi API cần doanh nghiệp (header X-Company-Id do api-gateway gắn từ JWT).
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "Hỏi đáp với Claude và hạn mức lượt AI theo gói")
public class AiController {

    private final AiRuntime runtime;
    private final AiQuotaService quotaService;
    private final AiAskService askService;
    private final AiChatService chatService;
    private final AiSummaryService summaryService;

    public AiController(AiRuntime runtime, AiQuotaService quotaService, AiAskService askService,
                        AiChatService chatService, AiSummaryService summaryService) {
        this.runtime = runtime;
        this.quotaService = quotaService;
        this.askService = askService;
        this.chatService = chatService;
        this.summaryService = summaryService;
    }

    @GetMapping("/health")
    @Operation(summary = "Trạng thái dịch vụ AI: chế độ chạy (MOCK hoặc LIVE) và mô hình đang cấu hình")
    public ApiResponse<AiHealthResponse> health() {
        return ApiResponse.ok(new AiHealthResponse("UP", runtime.mode().name(), runtime.model()));
    }

    @GetMapping("/usage")
    @Operation(summary = "Số lượt AI doanh nghiệp đã dùng trong tháng và giới hạn của gói")
    public ApiResponse<AiUsageResponse> usage() {
        return ApiResponse.ok(quotaService.usage());
    }

    @PostMapping("/ask")
    @Operation(summary = "Gửi một câu hỏi cho trợ lý AI (tính một lượt AI khi trả lời thành công)")
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.ok(askService.ask(request.question()));
    }

    @PostMapping("/chat")
    @Operation(summary = "Hỏi trợ lý AI: hướng dẫn sử dụng, tồn kho theo SKU hoặc theo kho, trạng thái đơn mua, đơn bán "
            + "theo số chứng từ. Chỉ đọc dữ liệu trong phạm vi quyền của người hỏi; lịch sử hội thoại giữ 1 giờ. Tính một "
            + "lượt AI khi trả lời thành công")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(request));
    }

    @GetMapping("/summary")
    @Operation(summary = "Tóm tắt số liệu điều hành của dashboard bằng AI (3 đến 5 ý và 1 đến 2 gợi ý). Lưu 30 phút "
            + "theo doanh nghiệp, nhóm vai trò và kho; refresh=true tạo lại. Tính một lượt AI mỗi lần tạo, không tính "
            + "khi lấy từ bộ nhớ đệm")
    public ApiResponse<SummaryResponse> summary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(summaryService.summary(warehouseId, refresh));
    }
}
