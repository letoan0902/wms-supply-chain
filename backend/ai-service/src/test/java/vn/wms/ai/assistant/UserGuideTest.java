package vn.wms.ai.assistant;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tài liệu hướng dẫn trong classpath: trùng với docs/USER_GUIDE.md, tách mục và chọn mục theo từ khóa. */
class UserGuideTest {

    private final UserGuide guide = new UserGuide();

    @Test
    void classpathCopyIsIdenticalToDocsUserGuide() throws Exception {
        // Gradle chạy test với thư mục làm việc là backend/ai-service; image Docker không có docs/ nên giữ bản chép.
        Path docs = Path.of("..", "..", "docs", "USER_GUIDE.md");
        assumeTrue(Files.exists(docs), "Không có docs/USER_GUIDE.md (chạy ngoài repo)");
        String expected = Files.readString(docs, StandardCharsets.UTF_8).strip();
        assertThat(guide.content())
                .as("Sửa docs/USER_GUIDE.md thì chép lại vào src/main/resources/" + UserGuide.RESOURCE)
                .isEqualTo(expected.replace("\r\n", "\n"));
    }

    @Test
    void guideHasSectionsForTheMainTasks() {
        assertThat(guide.sections()).hasSizeGreaterThan(10);
        assertThat(guide.titles()).contains("Tạo kho và vị trí lưu trữ", "Trợ lý AI");
        assertThat(guide.content()).doesNotContain("WMS-");
    }

    @Test
    void bestSectionMatchesKeywordsWithOrWithoutDiacritics() {
        assertThat(guide.bestSection("Tạo kho thế nào?")).get()
                .extracting(UserGuide.Section::title).isEqualTo("Tạo kho và vị trí lưu trữ");
        assertThat(guide.bestSection("tao kho the nao")).get()
                .extracting(UserGuide.Section::title).isEqualTo("Tạo kho và vị trí lưu trữ");
        assertThat(guide.bestSection("Làm sao tạo API key?")).get()
                .extracting(UserGuide.Section::title).isEqualTo("API key");
        assertThat(guide.bestSection("điều chuyển hàng giữa hai kho")).get()
                .extracting(UserGuide.Section::title).isEqualTo("Điều chuyển kho");
    }

    @Test
    void questionWithoutMeaningfulWordsHasNoSection() {
        assertThat(guide.bestSection("thế nào ạ?")).isEmpty();
        assertThat(guide.bestSection("zzzz qqqq")).isEmpty();
    }

    @Test
    void parsesSectionsFromMarkdown() {
        UserGuide small = new UserGuide("# Tiêu đề\n\nGiới thiệu\n\n## Mục A\n\nNội dung A\n\n## Mục B\nNội dung B\n");
        assertThat(small.titles()).containsExactly("Mục A", "Mục B");
        assertThat(small.sections().get(0).body()).isEqualTo("Nội dung A");
        assertThat(small.sections().get(1).markdown()).isEqualTo("## Mục B\n\nNội dung B");
    }
}
