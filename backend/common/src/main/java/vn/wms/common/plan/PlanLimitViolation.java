package vn.wms.common.plan;

/**
 * Nội dung trường {@code data} của lỗi {@code PLAN_LIMIT_EXCEEDED} (docs/SAAS_SPEC.md, mục 2.4).
 *
 * @param limit   giới hạn bị vượt
 * @param current số lượng hiện có (hoặc số lượt đã dùng trong tháng)
 * @param max     mức tối đa của gói
 * @param plan    mã gói hiện tại, ví dụ {@code FREE}
 */
public record PlanLimitViolation(PlanLimit limit, long current, long max, String plan) {
}
