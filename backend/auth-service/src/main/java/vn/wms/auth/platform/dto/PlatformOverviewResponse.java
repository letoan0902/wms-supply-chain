package vn.wms.auth.platform.dto;

import java.util.List;

/**
 * Số liệu tổng quan của nền tảng ({@code GET /api/auth/platform/overview}). Mọi số liệu bỏ qua doanh nghiệp hệ thống
 * {@code PLATFORM} và tài khoản của nó.
 *
 * @param registrationsByMonth số doanh nghiệp đăng ký theo tháng, đúng 6 tháng gần nhất kể cả tháng hiện tại (giờ
 *                             Việt Nam), tăng dần, tháng không có dữ liệu có giá trị 0
 * @param revenueByMonth       doanh thu (thanh toán PAID theo {@code paidAt}) theo tháng, cùng 6 tháng như trên
 * @param recentCompanies      5 doanh nghiệp tạo gần nhất
 * @param recentPayments       5 thanh toán PAID gần nhất
 */
public record PlatformOverviewResponse(
        Companies companies,
        Subscriptions subscriptions,
        Users users,
        Revenue revenue,
        List<MonthCount> registrationsByMonth,
        List<MonthAmount> revenueByMonth,
        List<PlatformCompanySummary> recentCompanies,
        List<PlatformPayment> recentPayments) {

    /** Số doanh nghiệp theo trạng thái. */
    public record Companies(long total, long active, long pending, long suspended) {
    }

    /**
     * Số doanh nghiệp theo gói của thuê bao (kể cả đã hết hạn), số thuê bao EXPIRED và số thuê bao ACTIVE có hạn trong
     * 7 ngày tới.
     */
    public record Subscriptions(long free, long trial, long pro, long expired, long expiringSoon) {
    }

    /** Số tài khoản của doanh nghiệp khách hàng và số tài khoản đang bật. */
    public record Users(long total, long active) {
    }

    /** Doanh thu VND từ thanh toán PAID: tổng, tháng hiện tại (giờ Việt Nam) và số giao dịch PAID. */
    public record Revenue(long total, long thisMonth, long paidCount) {
    }

    /** Một tháng dạng {@code yyyy-MM} và số lượng. */
    public record MonthCount(String month, long count) {
    }

    /** Một tháng dạng {@code yyyy-MM} và số tiền VND. */
    public record MonthAmount(String month, long amount) {
    }
}
