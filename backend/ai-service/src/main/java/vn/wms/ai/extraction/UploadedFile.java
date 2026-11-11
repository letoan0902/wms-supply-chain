package vn.wms.ai.extraction;

import org.springframework.web.multipart.MultipartFile;
import vn.wms.common.exception.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * File người dùng tải lên đã qua kiểm tra: có tên, không rỗng, không quá {@link ExtractionLimits#MAX_FILE_BYTES},
 * phần mở rộng được hỗ trợ và nội dung khớp phần mở rộng (chữ ký đầu file).
 *
 * @param fileName tên file gốc (chỉ phần tên, bỏ đường dẫn)
 * @param type     định dạng
 * @param bytes    nội dung
 */
public record UploadedFile(String fileName, UploadedFileType type, byte[] bytes) {

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String SUPPORTED = "xlsx, xls, csv, pdf, png, jpg";

    /** Kiểm tra file tải lên; mọi lỗi là {@code 400 BAD_REQUEST} với thông báo tiếng Việt. */
    public static UploadedFile of(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Chưa chọn file hoặc file rỗng.");
        }
        if (file.getSize() > ExtractionLimits.MAX_FILE_BYTES) {
            throw ApiException.badRequest("File vượt quá dung lượng cho phép (tối đa 10 MB).");
        }
        String fileName = baseName(file.getOriginalFilename());
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        UploadedFileType type = UploadedFileType.fromExtension(extension).orElseThrow(() -> ApiException.badRequest(
                "Định dạng file không được hỗ trợ. Chỉ nhận " + SUPPORTED + "."));
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("Không đọc được file tải lên, vui lòng thử lại.");
        }
        if (bytes.length > ExtractionLimits.MAX_FILE_BYTES) {
            throw ApiException.badRequest("File vượt quá dung lượng cho phép (tối đa 10 MB).");
        }
        if (!contentMatches(type, bytes)) {
            throw ApiException.badRequest("Nội dung file không đúng định dạng ." + extension
                    + " (file bị hỏng hoặc bị đổi phần mở rộng). Lượt này không bị tính.");
        }
        return new UploadedFile(fileName, type, bytes);
    }

    static boolean contentMatches(UploadedFileType type, byte[] bytes) {
        return switch (type) {
            case XLSX -> startsWith(bytes, ZIP);
            case XLS -> startsWith(bytes, OLE2);
            case PNG -> startsWith(bytes, PNG_SIGNATURE);
            case JPG -> startsWith(bytes, JPEG_SIGNATURE);
            // Chuẩn PDF cho phép vài byte rác trước "%PDF-" trong 1024 byte đầu.
            case PDF -> indexOf(bytes, PDF_SIGNATURE, 1024) >= 0;
            case CSV -> isText(bytes);
        };
    }

    /** CSV là văn bản: không có byte NUL và không phải một định dạng nhị phân đã biết. */
    private static boolean isText(byte[] bytes) {
        if (startsWith(bytes, ZIP) || startsWith(bytes, OLE2) || startsWith(bytes, PDF_SIGNATURE)
                || startsWith(bytes, PNG_SIGNATURE) || startsWith(bytes, JPEG_SIGNATURE)) {
            return false;
        }
        int limit = Math.min(bytes.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static int indexOf(byte[] bytes, byte[] needle, int searchLimit) {
        int last = Math.min(bytes.length - needle.length, searchLimit);
        for (int i = 0; i <= last; i++) {
            if (Arrays.equals(bytes, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }

    private static String baseName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw ApiException.badRequest("File tải lên không có tên.");
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            throw ApiException.badRequest("File tải lên không có tên.");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
