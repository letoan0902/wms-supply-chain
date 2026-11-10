package vn.wms.common.plan;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Tên khóa và tên trường Redis của bộ nhớ đệm thuê bao và mức sử dụng (docs/SAAS_SPEC.md, mục 2.7).
 * auth-service (nơi duy nhất ghi trạng thái thuê bao) và {@link PlanLimitChecker} dùng chung các hằng số này để
 * hai bên không lệch nhau. api-gateway chạy WebFlux, không phụ thuộc module common, nên khai báo lại các tên khóa
 * cần đọc; đổi tên ở đây thì phải sửa cả gateway.
 *
 * <pre>
 * wms:subscription:{companyId}      HASH  plan, status, expiresAt, maxUsers, maxWarehouses, maxSkus,
 *                                         maxAiRequests, apiAccess          (auth-service ghi)
 * wms:usage:{companyId}             HASH  users, warehouses, skus           (service nghiệp vụ ghi)
 * wms:usage:ai:{companyId}:{yyyyMM} STRING bộ đếm lượt AI trong tháng       (ai-service tăng)
 * </pre>
 */
public final class SubscriptionCacheKeys {

    /** Tiền tố hash trạng thái thuê bao của một doanh nghiệp. */
    public static final String SUBSCRIPTION_KEY_PREFIX = "wms:subscription:";
    /** Tiền tố hash mức sử dụng (số người dùng, kho, SKU đang hoạt động) của một doanh nghiệp. */
    public static final String USAGE_KEY_PREFIX = "wms:usage:";
    /** Tiền tố bộ đếm lượt AI theo tháng. */
    public static final String AI_USAGE_KEY_PREFIX = "wms:usage:ai:";

    public static final String FIELD_PLAN = "plan";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_MAX_USERS = "maxUsers";
    public static final String FIELD_MAX_WAREHOUSES = "maxWarehouses";
    public static final String FIELD_MAX_SKUS = "maxSkus";
    public static final String FIELD_MAX_AI_REQUESTS = "maxAiRequests";
    public static final String FIELD_API_ACCESS = "apiAccess";

    public static final String USAGE_FIELD_USERS = "users";
    public static final String USAGE_FIELD_WAREHOUSES = "warehouses";
    public static final String USAGE_FIELD_SKUS = "skus";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** Giá trị giới hạn nghĩa là không giới hạn. */
    public static final long UNLIMITED = -1L;

    /** Lượt AI đếm theo tháng dương lịch giờ Việt Nam. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private SubscriptionCacheKeys() {
        // Lớp hằng số không khởi tạo
    }

    /** Khóa hash thuê bao, ví dụ {@code wms:subscription:7}. */
    public static String subscriptionKey(long companyId) {
        return SUBSCRIPTION_KEY_PREFIX + companyId;
    }

    /** Khóa hash mức sử dụng, ví dụ {@code wms:usage:7}. */
    public static String usageKey(long companyId) {
        return USAGE_KEY_PREFIX + companyId;
    }

    /** Khóa bộ đếm lượt AI của một tháng, ví dụ {@code wms:usage:ai:7:202611}. */
    public static String aiUsageKey(long companyId, YearMonth month) {
        return AI_USAGE_KEY_PREFIX + companyId + ":" + month.format(MONTH_FORMAT);
    }
}
