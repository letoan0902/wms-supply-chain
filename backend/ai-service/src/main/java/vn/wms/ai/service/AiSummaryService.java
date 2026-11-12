package vn.wms.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.wms.ai.assistant.ServiceDataClient;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiContentPart;
import vn.wms.ai.client.AiJsonReply;
import vn.wms.ai.client.AiJsonRequest;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.config.AiSummaryProperties;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.SummaryResponse;
import vn.wms.ai.summary.DashboardFacts;
import vn.wms.ai.summary.SummaryCache;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.plan.SubscriptionCacheKeys;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bản tóm tắt số liệu điều hành bằng AI cho dashboard ({@code GET /api/ai/summary}, WMS-18.1).
 *
 * <p>Luồng: đọc bộ nhớ đệm 30 phút theo doanh nghiệp, nhóm vai trò và phạm vi kho (trừ khi {@code refresh=true});
 * không có thì lấy 8 thẻ KPI (notification-service) và tổng nhập, xuất từ đầu tháng (báo cáo Xuất - Nhập - Tồn của
 * inventory-service) bằng danh tính người xem, kiểm tra hạn mức, nhờ Claude viết 3 đến 5 ý và 1 đến 2 gợi ý chỉ từ số
 * đã cho (structured outputs), tính một lượt AI rồi lưu bộ nhớ đệm. Lấy từ bộ nhớ đệm không tốn lượt. Chế độ giả lập
 * dựng các ý bằng câu soạn sẵn từ cùng số liệu.
 *
 * <p>Ai xem được: người được notification-service cho xem KPI (vai trò theo kho phải được gán kho; kho không được gán
 * trả {@code 403}); thẻ tài chính bị che với vai trò không phải ADMIN, ACCOUNTANT và không được đưa vào bản tóm tắt.
 */
@Service
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);

    static final int MAX_BULLETS = 5;
    static final int MAX_ACTIONS = 2;

    /** Lược đồ câu trả lời của Claude. */
    static final Map<String, Object> SCHEMA = schema();

    private final AiRuntime runtime;
    private final AiClient aiClient;
    private final AiQuotaService quotaService;
    private final ServiceDataClient dataClient;
    private final SummaryCache cache;
    private final AiSummaryProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AiSummaryService(AiRuntime runtime, AiClient aiClient, AiQuotaService quotaService,
                            ServiceDataClient dataClient, SummaryCache cache, AiSummaryProperties properties,
                            ObjectMapper objectMapper) {
        this(runtime, aiClient, quotaService, dataClient, cache, properties, objectMapper,
                Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE));
    }

    AiSummaryService(AiRuntime runtime, AiClient aiClient, AiQuotaService quotaService, ServiceDataClient dataClient,
                     SummaryCache cache, AiSummaryProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.runtime = runtime;
        this.aiClient = aiClient;
        this.quotaService = quotaService;
        this.dataClient = dataClient;
        this.cache = cache;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SummaryResponse summary(Long warehouseId, boolean refresh) {
        long companyId = TenantContext.requireCompanyId();
        String key = SummaryCache.keyForCurrentUser(companyId, warehouseId);
        if (!refresh) {
            Optional<SummaryResponse> cached = cache.find(key);
            if (cached.isPresent()) {
                return cached.get().cached(new AskResponse.Usage(quotaService.currentUsage(),
                        quotaService.currentMax()));
            }
        }

        DashboardFacts facts = loadFacts(warehouseId);
        quotaService.checkQuota();
        List<String> bullets;
        List<String> actions;
        if (runtime.isMock()) {
            bullets = facts.mockBullets();
            actions = facts.mockActions();
        } else {
            JsonNode reply = askClaude(facts);
            bullets = lines(reply.path("bullets"), MAX_BULLETS);
            actions = lines(reply.path("actions"), MAX_ACTIONS);
            if (bullets.isEmpty()) {
                log.warn("Claude trả bản tóm tắt dashboard không có ý nào");
                throw unavailable();
            }
        }
        long used = quotaService.recordSuccessfulRequest();
        SummaryResponse response = new SummaryResponse(text(bullets, actions), bullets, actions, clock.instant(),
                false, runtime.mode().name(), warehouseId, facts.financialsVisible(),
                new AskResponse.Usage(used, quotaService.currentMax()));
        cache.save(key, response);
        return response;
    }

    /** Lấy KPI (bắt buộc) và tổng nhập, xuất từ đầu tháng (không bắt buộc) cùng một phạm vi kho. */
    private DashboardFacts loadFacts(Long warehouseId) {
        Map<String, Object> kpiQuery = new LinkedHashMap<>();
        kpiQuery.put("warehouseId", warehouseId);
        ServiceDataClient.Result kpi = dataClient.get("notification-service",
                "/api/notifications/dashboard/kpi-summary", Map.of(), kpiQuery);
        if (!kpi.ok()) {
            throw switch (kpi.status()) {
                case FORBIDDEN -> ApiException.forbidden("Bạn không có quyền xem số liệu điều hành của phạm vi này.");
                case NOT_FOUND -> ApiException.notFound("Không tìm thấy kho đã chọn.");
                case BAD_REQUEST -> ApiException.badRequest(kpi.message());
                default -> new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Chưa lấy được số liệu điều hành: "
                        + "dịch vụ thông báo, KPI chưa sẵn sàng. Lượt AI không bị tính, vui lòng thử lại sau.");
            };
        }
        // Cùng kho notification-service đã dùng (vai trò theo kho không chọn kho thì là kho được gán đầu tiên).
        JsonNode effectiveWarehouse = kpi.data().path("meta").path("warehouseId");
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> reportQuery = new LinkedHashMap<>();
        reportQuery.put("fromDate", today.withDayOfMonth(1).toString());
        reportQuery.put("toDate", today.toString());
        reportQuery.put("warehouseId", effectiveWarehouse.isNumber() ? effectiveWarehouse.asLong() : null);
        reportQuery.put("groupBy", "PRODUCT");
        reportQuery.put("page", 0);
        reportQuery.put("size", 1);
        ServiceDataClient.Result report = dataClient.get("inventory-service", "/api/inventory/reports/in-out-balance",
                Map.of(), reportQuery);
        String note = null;
        if (!report.ok()) {
            note = report.status() == ServiceDataClient.Status.FORBIDDEN
                    ? "vai trò của người xem không được xem báo cáo Xuất - Nhập - Tồn"
                    : "chưa đọc được báo cáo Xuất - Nhập - Tồn";
        }
        return new DashboardFacts(kpi.data(), report.ok() ? report.data() : null, note);
    }

    private JsonNode askClaude(DashboardFacts facts) {
        String data = facts.toJson(objectMapper).toPrettyString();
        AiJsonReply reply = aiClient.generateJson(new AiJsonRequest(SummaryPrompts.SYSTEM_PROMPT,
                List.of(new AiContentPart.Text("<kpi_data>\n" + data + "\n</kpi_data>\n\n" + SummaryPrompts.TASK)),
                SCHEMA, properties.maxTokens(), properties.timeout()));
        if (reply.refused() || reply.truncated()) {
            log.info("Không dùng bản tóm tắt dashboard của Claude (stop_reason={})", reply.stopReason());
            throw unavailable();
        }
        try {
            return objectMapper.readTree(reply.json());
        } catch (Exception ex) {
            log.warn("Bản tóm tắt dashboard của Claude không phải JSON hợp lệ: {}", ex.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "AI chưa tạo được bản tóm tắt lúc này, lượt này không bị tính. Vui lòng thử lại sau.");
    }

    private static List<String> lines(JsonNode array, int max) {
        List<String> lines = new ArrayList<>();
        for (JsonNode node : array) {
            String line = node.asText("").strip().replaceFirst("^[-*•]\\s*", "");
            if (!line.isEmpty() && lines.size() < max) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    static String text(List<String> bullets, List<String> actions) {
        StringBuilder text = new StringBuilder();
        bullets.forEach(bullet -> text.append("- ").append(bullet).append('\n'));
        if (!actions.isEmpty()) {
            text.append("\nGợi ý:\n");
            actions.forEach(action -> text.append("- ").append(action).append('\n'));
        }
        return text.toString().strip();
    }

    private static Map<String, Object> schema() {
        Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("bullets", stringArray);
        properties.put("actions", stringArray);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("bullets", "actions"));
        schema.put("additionalProperties", false);
        return schema;
    }
}
