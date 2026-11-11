package vn.wms.ai.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.wms.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiểm tra định dạng (phần mở rộng và chữ ký nội dung) và dung lượng file tải lên. */
class UploadedFileTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Test
    void acceptsEverySupportedTypeWhenContentMatches() {
        assertThat(UploadedFile.of(file("hang.xlsx", TestSpreadsheets.xlsx(List.of(List.of("a"))))).type())
                .isEqualTo(UploadedFileType.XLSX);
        assertThat(UploadedFile.of(file("hang.XLS", TestSpreadsheets.xls(List.of(List.of("a"))))).type())
                .isEqualTo(UploadedFileType.XLS);
        assertThat(UploadedFile.of(file("hang.csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8))).type())
                .isEqualTo(UploadedFileType.CSV);
        assertThat(UploadedFile.of(file("bang-gia.pdf", PDF)).type()).isEqualTo(UploadedFileType.PDF);
        assertThat(UploadedFile.of(file("anh.png", PNG)).type()).isEqualTo(UploadedFileType.PNG);
        assertThat(UploadedFile.of(file("anh.jpeg", JPG)).type()).isEqualTo(UploadedFileType.JPG);
        assertThat(UploadedFile.of(file("C:\\Users\\a\\anh.jpg", JPG)).fileName()).isEqualTo("anh.jpg");
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> UploadedFile.of(file("hang.docx", PDF)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getHttpStatus()).isEqualTo(400))
                .hasMessageContaining("Chỉ nhận xlsx, xls, csv, pdf, png, jpg");
        assertThatThrownBy(() -> UploadedFile.of(file("khong-duoi", PDF))).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsContentThatDoesNotMatchExtension() {
        assertThatThrownBy(() -> UploadedFile.of(file("hang.xlsx", PDF)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không đúng định dạng .xlsx");
        assertThatThrownBy(() -> UploadedFile.of(file("anh.png", JPG))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> UploadedFile.of(file("bang.pdf", PNG))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> UploadedFile.of(file("hang.csv", TestSpreadsheets.xlsx(List.of(List.of("a"))))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsEmptyAndTooLargeFiles() {
        assertThatThrownBy(() -> UploadedFile.of(file("hang.csv", new byte[0])))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("file rỗng");
        assertThatThrownBy(() -> UploadedFile.of(null)).isInstanceOf(ApiException.class);
        byte[] tooLarge = new byte[(int) ExtractionLimits.MAX_FILE_BYTES + 1];
        tooLarge[0] = 'a';
        assertThatThrownBy(() -> UploadedFile.of(file("hang.csv", tooLarge)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tối đa 10 MB");
    }

    @Test
    void fileOfExactly10MbIsAccepted() {
        byte[] content = new byte[(int) ExtractionLimits.MAX_FILE_BYTES];
        java.util.Arrays.fill(content, (byte) 'a');

        assertThat(UploadedFile.of(file("hang.csv", content)).bytes()).hasSize((int) ExtractionLimits.MAX_FILE_BYTES);
    }
}
