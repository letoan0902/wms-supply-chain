package vn.wms.ai.extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kết quả ghép cột: trường đích nào lấy dữ liệu từ cột nào của bảng tính, kèm độ tin cậy. Mỗi cột nguồn chỉ ghép với
 * tối đa một trường.
 *
 * @param columns trường đích (tên trường) đến cột nguồn; trường không có trong map là không tìm thấy cột phù hợp
 * @param notes   ghi chú cho người dùng (tiếng Việt), ví dụ cột được ghép nhưng chưa chắc chắn
 */
public record ColumnMapping(Map<String, MappedColumn> columns, List<String> notes) {

    /**
     * @param column     chỉ số cột nguồn, từ 0
     * @param header     tiêu đề cột nguồn
     * @param confidence độ tin cậy của việc ghép, từ 0 đến 1
     */
    public record MappedColumn(int column, String header, double confidence) {
    }

    /** Một đề xuất ghép trường với cột, trước khi loại trùng. */
    record Candidate(String field, int column, double confidence) {
    }

    public ColumnMapping {
        columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
        notes = List.copyOf(notes);
    }

    public Optional<MappedColumn> column(String field) {
        return Optional.ofNullable(columns.get(field));
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /**
     * Chọn tập ghép tốt nhất từ các đề xuất: duyệt theo độ tin cậy giảm dần, bỏ đề xuất trùng trường hoặc trùng cột
     * với đề xuất đã chọn. Đề xuất bằng điểm giữ thứ tự ban đầu.
     */
    static Map<String, MappedColumn> resolve(List<Candidate> candidates, List<String> headers) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Candidate::confidence).reversed());
        Map<String, MappedColumn> result = new LinkedHashMap<>();
        Set<Integer> usedColumns = new HashSet<>();
        for (Candidate candidate : sorted) {
            if (candidate.confidence() <= 0 || result.containsKey(candidate.field())
                    || usedColumns.contains(candidate.column())
                    || candidate.column() < 0 || candidate.column() >= headers.size()) {
                continue;
            }
            usedColumns.add(candidate.column());
            result.put(candidate.field(), new MappedColumn(candidate.column(), headers.get(candidate.column()),
                    CellNormalizer.round(candidate.confidence())));
        }
        return result;
    }
}
