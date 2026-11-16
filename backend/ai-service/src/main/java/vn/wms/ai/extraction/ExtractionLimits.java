package vn.wms.ai.extraction;

/** Giới hạn nghiệp vụ của trích xuất tài liệu (docs/SAAS_SPEC.md, mục WMS-24.2). */
public final class ExtractionLimits {

    /** Dung lượng file tối đa: 10 MB (khớp {@code spring.servlet.multipart.max-file-size}). */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * Dung lượng tối đa của một hình ảnh (png, jpg): 7,5 MB (7 500 000 byte). Claude API nhận mỗi ảnh tối đa 10 MB tính
     * trên chuỗi base64 gửi đi (docs Vision, mục "Image limits"), mà base64 dài gấp 4/3 file gốc: 7 500 000 byte thành
     * 10 000 000 ký tự, dưới 10 MB dù tính theo 10^6 hay 2^20. PDF và bảng tính vẫn theo {@link #MAX_FILE_BYTES}.
     */
    public static final long MAX_IMAGE_BYTES = 7_500_000L;

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
