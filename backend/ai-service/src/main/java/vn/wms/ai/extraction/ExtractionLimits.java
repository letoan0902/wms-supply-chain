package vn.wms.ai.extraction;

/** Giới hạn nghiệp vụ của trích xuất tài liệu (docs/SAAS_SPEC.md, mục WMS-24.2). */
public final class ExtractionLimits {

    /** Dung lượng file tối đa: 10 MB (khớp {@code spring.servlet.multipart.max-file-size}). */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /** Số dòng dữ liệu tối đa lấy từ một file. */
    public static final int MAX_ROWS = 200;

    /** Số dòng đầu tiên xét để tìm dòng tiêu đề (bỏ qua dòng tên bảng phía trên). */
    public static final int HEADER_SCAN_ROWS = 10;

    /** Số dòng dữ liệu mẫu gửi Claude để ghép cột. */
    public static final int SAMPLE_ROWS = 5;

    /** Số ký tự tối đa của một ô mẫu gửi Claude. */
    public static final int SAMPLE_CELL_LENGTH = 80;

    /** Số cột tối đa xét trong một bảng tính. */
    public static final int MAX_COLUMNS = 100;

    private ExtractionLimits() {
    }
}
