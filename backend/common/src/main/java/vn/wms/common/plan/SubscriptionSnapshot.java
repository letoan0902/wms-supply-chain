package vn.wms.common.plan;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ảnh chụp bất biến của hash {@code wms:subscription:{companyId}} do auth-service ghi.
 *
 * @param plan      {@code FREE}, {@code TRIAL} hoặc {@code PRO}
 * @param status    {@code ACTIVE} hoặc {@code EXPIRED}
 * @param expiresAt thời điểm hết hạn, {@code null} với gói {@code FREE} hoặc khi trường rỗng
 * @param limits    giới hạn dạng số theo từng {@link PlanLimit} ({@code -1} là không giới hạn); không chứa
 *                  {@link PlanLimit#API_ACCESS}
 * @param apiAccess gói có được dùng API key hay không
 */
public record SubscriptionSnapshot(String plan, String status, Instant expiresAt, Map<PlanLimit, Long> limits,
                                   boolean apiAccess) {

    public SubscriptionSnapshot {
        EnumMap<PlanLimit, Long> copy = new EnumMap<>(PlanLimit.class);
        if (limits != null) {
            copy.putAll(limits);
        }
        copy.remove(PlanLimit.API_ACCESS);
        limits = Collections.unmodifiableMap(copy);
    }

    /**
     * Dựng ảnh chụp từ nội dung hash. Trả rỗng khi hash rỗng (khóa không tồn tại) hoặc thiếu trường {@code plan}.
     * Trường giới hạn thiếu hoặc không phải số được coi là không giới hạn, để lỗi dữ liệu không chặn nhầm người dùng.
     */
    public static Optional<SubscriptionSnapshot> fromHash(Map<?, ?> hash) {
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        String plan = text(hash.get(SubscriptionCacheKeys.FIELD_PLAN));
        if (plan == null) {
            return Optional.empty();
        }
        EnumMap<PlanLimit, Long> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit limit : PlanLimit.values()) {
            if (limit != PlanLimit.API_ACCESS) {
                limits.put(limit, number(hash.get(limit.maxField())));
            }
        }
        return Optional.of(new SubscriptionSnapshot(
                plan,
                text(hash.get(SubscriptionCacheKeys.FIELD_STATUS)),
                instant(hash.get(SubscriptionCacheKeys.FIELD_EXPIRES_AT)),
                limits,
                Boolean.parseBoolean(text(hash.get(SubscriptionCacheKeys.FIELD_API_ACCESS)))));
    }

    /**
     * Mức tối đa của một giới hạn. {@link PlanLimit#API_ACCESS} quy về {@code -1} (được dùng) hoặc {@code 0}
     * (không được dùng) để mọi giới hạn so sánh chung một quy tắc {@code current >= max}.
     */
    public long maxOf(PlanLimit limit) {
        if (limit == PlanLimit.API_ACCESS) {
            return apiAccess ? SubscriptionCacheKeys.UNLIMITED : 0L;
        }
        return limits.getOrDefault(limit, SubscriptionCacheKeys.UNLIMITED);
    }

    /** Thuê bao đã hết hạn (chế độ chỉ đọc). */
    public boolean isExpired() {
        return SubscriptionCacheKeys.STATUS_EXPIRED.equalsIgnoreCase(status);
    }

    /** Tên gói hiển thị cho người dùng, ví dụ "Khởi Đầu". */
    public String planDisplayName() {
        return switch (plan.toUpperCase()) {
            case "FREE" -> "Khởi Đầu";
            case "TRIAL" -> "Dùng thử";
            case "PRO" -> "Chuyên Nghiệp";
            default -> plan;
        };
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static long number(Object value) {
        String raw = text(value);
        if (raw == null) {
            return SubscriptionCacheKeys.UNLIMITED;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return SubscriptionCacheKeys.UNLIMITED;
        }
    }

    private static Instant instant(Object value) {
        String raw = text(value);
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
