package vn.wms.ai.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghép cột theo tên tiêu đề, không gọi AI: dùng ở chế độ giả lập ({@code AI_MOCK=true} hoặc chưa có khóa) để nhóm
 * phát triển và demo được với file Excel, CSV thật. So khớp không phân biệt hoa thường và dấu ("Don vi tinh" khớp
 * "Đơn vị tính"), theo danh sách tên cột thường gặp tiếng Việt và tiếng Anh của từng trường
 * ({@link TargetField#synonyms()}). Kết quả luôn giống nhau với cùng một file.
 *
 * <p>Điểm: trùng khớp cả tiêu đề 0,95; tiêu đề chứa nguyên cụm từ đồng nghĩa (theo từ) 0,6 cộng 0,1 cho mỗi từ của
 * cụm, tối đa 0,85, nên "Giá nhập (VNĐ)" nghiêng về Giá nhập hơn Giá bán.
 */
public final class HeaderSynonymMatcher {

    static final double EXACT = 0.95;
    static final double PARTIAL_BASE = 0.6;
    static final double PARTIAL_PER_WORD = 0.1;
    static final double PARTIAL_MAX = 0.85;

    private HeaderSynonymMatcher() {
    }

    public static ColumnMapping match(ExtractionDataType type, List<String> headers) {
        List<ColumnMapping.Candidate> candidates = new ArrayList<>();
        for (TargetField field : type.fields()) {
            for (int column = 0; column < headers.size(); column++) {
                double score = score(field, headers.get(column));
                if (score > 0) {
                    candidates.add(new ColumnMapping.Candidate(field.name(), column, score));
                }
            }
        }
        return new ColumnMapping(ColumnMapping.resolve(candidates, headers), List.of());
    }

    static double score(TargetField field, String header) {
        String folded = TextNormalizer.fold(header);
        if (folded.isEmpty()) {
            return 0;
        }
        String paddedHeader = " " + folded + " ";
        double best = 0;
        List<String> names = new ArrayList<>(field.synonyms());
        names.add(splitCamelCase(field.name()));
        for (String synonym : names) {
            String foldedSynonym = TextNormalizer.fold(synonym);
            if (foldedSynonym.isEmpty()) {
                continue;
            }
            if (folded.equals(foldedSynonym)) {
                return EXACT;
            }
            if (paddedHeader.contains(" " + foldedSynonym + " ")) {
                int words = foldedSynonym.split(" ").length;
                best = Math.max(best, Math.min(PARTIAL_MAX, PARTIAL_BASE + PARTIAL_PER_WORD * words));
            }
        }
        return best;
    }

    /** "purchasePrice" thành "purchase price" để tiêu đề tiếng Anh trùng tên trường cũng khớp. */
    private static String splitCamelCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
