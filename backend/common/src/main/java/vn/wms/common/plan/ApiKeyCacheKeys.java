package vn.wms.common.plan;

/**
 * Tên khóa và tên trường Redis dùng để api-gateway xác thực API key của hệ thống ngoài (WMS-25.1,
 * docs/SAAS_SPEC.md mục WMS-25.1). auth-service là nơi duy nhất ghi khóa tra cứu key; gateway chỉ đọc khóa đó và
 * ghi hai khóa phụ (thời điểm dùng gần nhất, bộ đếm tốc độ). Cùng ý tưởng với {@link SubscriptionCacheKeys}:
 * api-gateway chạy WebFlux, không phụ thuộc module common, nên khai báo lại các tên khóa; đổi tên ở đây thì phải sửa
 * cả {@code vn.wms.gateway.apikey.ApiKeyAuthenticationGlobalFilter}.
 *
 * <pre>
 * wms:apikey:{sha256}                   HASH   companyId, keyId, prefix     (auth-service ghi sau commit, xóa khi
 *                                                                           thu hồi, ghi lại toàn bộ định kỳ)
 * wms:apikey:last-used                  HASH   {keyId} -&gt; epoch mili giây  (gateway ghi mỗi request hợp lệ,
 *                                                                           auth-service đổ vào cột last_used_at)
 * wms:apikey:rate:{keyId}:{epochSecond} STRING bộ đếm request trong một giây, tự hết hạn sau 2 giây (gateway)
 * </pre>
 *
 * <p>{@code {sha256}} là chuỗi hex chữ thường 64 ký tự của SHA-256 trên toàn bộ key (UTF-8). Key gốc không bao giờ
 * nằm trên Redis hay trong cơ sở dữ liệu.
 */
public final class ApiKeyCacheKeys {

    /** Tiền tố hash tra cứu key theo SHA-256. */
    public static final String KEY_PREFIX = "wms:apikey:";
    /** Hash thời điểm dùng gần nhất của mọi key, trường là ID key, giá trị là epoch mili giây. */
    public static final String LAST_USED_KEY = "wms:apikey:last-used";
    /** Tiền tố bộ đếm giới hạn tốc độ theo key và theo giây. */
    public static final String RATE_KEY_PREFIX = "wms:apikey:rate:";

    public static final String FIELD_COMPANY_ID = "companyId";
    public static final String FIELD_KEY_ID = "keyId";
    public static final String FIELD_PREFIX = "prefix";

    /** Header hệ thống ngoài gửi key. */
    public static final String HEADER_API_KEY = "X-Api-Key";

    private ApiKeyCacheKeys() {
        // Lớp hằng số không khởi tạo
    }

    /** Khóa hash tra cứu, ví dụ {@code wms:apikey:9f86d0...}. */
    public static String lookupKey(String sha256Hex) {
        return KEY_PREFIX + sha256Hex;
    }

    /** Khóa bộ đếm tốc độ của một key trong một giây, ví dụ {@code wms:apikey:rate:12:1762833600}. */
    public static String rateKey(long keyId, long epochSecond) {
        return RATE_KEY_PREFIX + keyId + ":" + epochSecond;
    }
}
