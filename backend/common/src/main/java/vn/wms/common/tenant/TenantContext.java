package vn.wms.common.tenant;

import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.function.Supplier;

/**
 * Giữ ID doanh nghiệp (company) của luồng đang xử lý. Với request HTTP, {@link TenantFilter} đặt giá trị từ header
 * {@value #HEADER_COMPANY_ID} do api-gateway chuyển tiếp; với message RabbitMQ, {@link TenantMessagePostProcessor}
 * đặt từ header của message; với tác vụ nền thì dùng {@link #runAs(Long, Runnable)}.
 *
 * <p>Hibernate đọc giá trị này tại thời điểm mở phiên làm việc (EntityManager). Vì vậy phải đặt ngữ cảnh
 * TRƯỚC khi mở transaction; đổi doanh nghiệp giữa chừng một transaction đang mở sẽ không có tác dụng.
 */
public final class TenantContext {

    /** Header mang ID doanh nghiệp, do api-gateway gắn từ claim {@code companyId} của JWT. */
    public static final String HEADER_COMPANY_ID = "X-Company-Id";

    /**
     * Giá trị Hibernate dùng khi luồng hiện tại không thuộc doanh nghiệp nào. Không doanh nghiệp nào có ID này
     * nên mọi truy vấn trên bảng có {@code company_id} trả về rỗng (đóng theo mặc định).
     */
    public static final long NO_COMPANY = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
        // Lớp tiện ích không khởi tạo
    }

    /** ID doanh nghiệp của luồng hiện tại, {@code null} nếu chưa xác định. */
    public static Long getCompanyId() {
        return CURRENT.get();
    }

    /** Có doanh nghiệp trong ngữ cảnh hay không. */
    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /**
     * Lấy ID doanh nghiệp, bắt buộc phải có.
     *
     * @throws ApiException mã {@link ErrorCode#COMPANY_REQUIRED} khi chưa xác định được doanh nghiệp
     */
    public static Long requireCompanyId() {
        Long companyId = CURRENT.get();
        if (companyId == null) {
            throw new ApiException(ErrorCode.COMPANY_REQUIRED);
        }
        return companyId;
    }

    /** Đặt doanh nghiệp cho luồng hiện tại. Giá trị {@code null} hoặc không dương sẽ xóa ngữ cảnh. */
    public static void setCompanyId(Long companyId) {
        if (companyId == null || companyId <= 0) {
            CURRENT.remove();
        } else {
            CURRENT.set(companyId);
        }
    }

    /** Xóa ngữ cảnh, phải gọi khi xử lý xong để luồng trong pool không mang doanh nghiệp cũ sang request khác. */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Chạy một đoạn code dưới danh nghĩa một doanh nghiệp rồi trả lại ngữ cảnh cũ. Dùng cho tác vụ định kỳ,
     * xử lý bất đồng bộ và test. Transaction phải được mở bên trong {@code action}.
     */
    public static void runAs(Long companyId, Runnable action) {
        callAs(companyId, () -> {
            action.run();
            return null;
        });
    }

    /** Giống {@link #runAs(Long, Runnable)} nhưng trả về kết quả. */
    public static <T> T callAs(Long companyId, Supplier<T> action) {
        Long previous = CURRENT.get();
        setCompanyId(companyId);
        try {
            return action.get();
        } finally {
            setCompanyId(previous);
        }
    }

    /** Đọc ID doanh nghiệp từ chuỗi (giá trị header). Trả {@code null} nếu rỗng, không phải số hoặc không dương. */
    public static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
