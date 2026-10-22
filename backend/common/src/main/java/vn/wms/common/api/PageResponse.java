package vn.wms.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Kết quả phân trang trả về cho client. Chỉ số trang (page) bắt đầu từ 0, giống Spring Data.
 *
 * @param content       các phần tử của trang hiện tại
 * @param page          chỉ số trang hiện tại (từ 0)
 * @param size          kích thước trang yêu cầu
 * @param totalElements tổng số phần tử
 * @param totalPages    tổng số trang
 * @param first         có phải trang đầu
 * @param last          có phải trang cuối
 * @param <T>           kiểu phần tử
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean first, boolean last) {

    /** Dựng từ danh sách đã cắt trang sẵn (không phụ thuộc Spring Data). */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(List.copyOf(content), page, size, totalElements, totalPages,
                page <= 0, page + 1 >= totalPages);
    }

    /** Chuyển từ {@link Page} của Spring Data, ví dụ {@code PageResponse.from(page.map(Dto::from))}. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
