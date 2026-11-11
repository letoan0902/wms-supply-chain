package vn.wms.common.bulk;

import java.util.Comparator;
import java.util.List;

/**
 * Kết quả của API nhập hàng loạt, luôn trả HTTP 200 kể cả khi mọi dòng đều lỗi (docs/SAAS_SPEC.md, WMS-22.4b).
 *
 * @param total   số dòng trong request
 * @param created số dòng đã tạo mới
 * @param skipped số dòng bỏ qua vì mã đã tồn tại trong doanh nghiệp
 * @param failed  số dòng lỗi
 * @param results kết quả từng dòng, sắp theo {@code row} tăng dần
 */
public record BulkResult(int total, int created, int skipped, int failed, List<BulkRowResult> results) {

    /** Tổng hợp số đếm từ kết quả từng dòng. */
    public static BulkResult of(List<BulkRowResult> results) {
        List<BulkRowResult> sorted = results.stream()
                .sorted(Comparator.comparingInt(BulkRowResult::row))
                .toList();
        return new BulkResult(sorted.size(),
                count(sorted, BulkRowStatus.CREATED),
                count(sorted, BulkRowStatus.SKIPPED),
                count(sorted, BulkRowStatus.FAILED),
                sorted);
    }

    private static int count(List<BulkRowResult> results, BulkRowStatus status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }
}
