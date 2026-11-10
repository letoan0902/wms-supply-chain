package vn.wms.common.plan;

/**
 * Các giới hạn của gói dịch vụ (docs/SAAS_SPEC.md, mục 2.5). Tên hằng số chính là giá trị trường {@code limit}
 * trong {@code data} của lỗi {@code PLAN_LIMIT_EXCEEDED}.
 */
public enum PlanLimit {

    /** Tài khoản đang bật của doanh nghiệp. */
    USERS(SubscriptionCacheKeys.FIELD_MAX_USERS, SubscriptionCacheKeys.USAGE_FIELD_USERS,
            "người dùng", "thêm người dùng mới"),
    /** Kho đang hoạt động. */
    WAREHOUSES(SubscriptionCacheKeys.FIELD_MAX_WAREHOUSES, SubscriptionCacheKeys.USAGE_FIELD_WAREHOUSES,
            "kho", "thêm kho mới"),
    /** SKU đang hoạt động (không tính SKU đã ngưng hoặc xóa mềm). */
    SKUS(SubscriptionCacheKeys.FIELD_MAX_SKUS, SubscriptionCacheKeys.USAGE_FIELD_SKUS,
            "SKU", "thêm SKU mới"),
    /** Lượt AI trong tháng, đếm ở khóa riêng {@code wms:usage:ai:{companyId}:{yyyyMM}}. */
    AI_REQUESTS(SubscriptionCacheKeys.FIELD_MAX_AI_REQUESTS, null,
            "lượt AI mỗi tháng", "dùng thêm lượt AI"),
    /** Quyền dùng API key; trong hash là trường {@code apiAccess} kiểu true/false chứ không phải số. */
    API_ACCESS(SubscriptionCacheKeys.FIELD_API_ACCESS, null,
            "API key", "dùng API key");

    private final String maxField;
    private final String usageField;
    private final String unit;
    private final String upgradeAction;

    PlanLimit(String maxField, String usageField, String unit, String upgradeAction) {
        this.maxField = maxField;
        this.usageField = usageField;
        this.unit = unit;
        this.upgradeAction = upgradeAction;
    }

    /** Tên trường giới hạn trong hash {@code wms:subscription:{companyId}}. */
    public String maxField() {
        return maxField;
    }

    /** Tên trường trong hash {@code wms:usage:{companyId}}, {@code null} nếu giới hạn này không ghi vào hash đó. */
    public String usageField() {
        return usageField;
    }

    /** Đơn vị đếm dùng trong thông báo, ví dụ "kho". */
    public String unit() {
        return unit;
    }

    /** Việc người dùng muốn làm, dùng trong câu gợi ý nâng cấp, ví dụ "thêm kho mới". */
    public String upgradeAction() {
        return upgradeAction;
    }
}
