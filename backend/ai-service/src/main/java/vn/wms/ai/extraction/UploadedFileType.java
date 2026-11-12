package vn.wms.ai.extraction;

import java.util.List;
import java.util.Optional;

/** Định dạng file được trích xuất, nhận diện theo phần mở rộng rồi kiểm tra lại bằng chữ ký nội dung. */
public enum UploadedFileType {
    XLSX(List.of("xlsx"), true, null),
    XLS(List.of("xls"), true, null),
    CSV(List.of("csv"), true, null),
    PDF(List.of("pdf"), false, "application/pdf"),
    PNG(List.of("png"), false, "image/png"),
    JPG(List.of("jpg", "jpeg"), false, "image/jpeg");

    private final List<String> extensions;
    private final boolean spreadsheet;
    private final String mediaType;

    UploadedFileType(List<String> extensions, boolean spreadsheet, String mediaType) {
        this.extensions = extensions;
        this.spreadsheet = spreadsheet;
        this.mediaType = mediaType;
    }

    /** Bảng tính (đọc tại chỗ, Claude chỉ ghép cột) hay tài liệu (Claude đọc toàn bộ). */
    public boolean spreadsheet() {
        return spreadsheet;
    }

    /** Kiểu nội dung gửi Claude với PDF và hình ảnh, {@code null} với bảng tính. */
    public String mediaType() {
        return mediaType;
    }

    public static Optional<UploadedFileType> fromExtension(String extension) {
        for (UploadedFileType type : values()) {
            if (type.extensions.contains(extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
