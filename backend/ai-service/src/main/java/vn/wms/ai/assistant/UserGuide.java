package vn.wms.ai.assistant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.wms.ai.extraction.TextNormalizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tài liệu hướng dẫn sử dụng ({@code docs/USER_GUIDE.md}, bản chép trong {@value #RESOURCE}) cho trợ lý AI: gửi
 * nguyên văn trong lời dặn hệ thống của Claude, và ở chế độ giả lập thì chọn mục ({@code ## }) khớp câu hỏi nhất theo
 * từ khóa, không phân biệt dấu.
 */
@Component
public class UserGuide {

    /** Đường dẫn trong classpath. Test {@code UserGuideTest} kiểm tra bản này trùng với {@code docs/USER_GUIDE.md}. */
    public static final String RESOURCE = "assistant/USER_GUIDE.md";

    /** Từ không mang nghĩa tra cứu (đã bỏ dấu). */
    private static final Set<String> STOP_WORDS = Set.of("the", "nao", "lam", "sao", "cach", "nhu", "toi", "minh",
            "ban", "co", "khong", "la", "gi", "o", "dau", "duoc", "cua", "va", "voi", "cho", "de", "mot", "nhung",
            "cac", "trong", "thi", "ma", "hay", "giup", "xin", "hoi", "muon", "can", "phai", "bao", "nhieu", "khi",
            "nay", "do", "vay", "a", "nhe", "oi", "em", "anh", "chi", "huong", "dan", "su", "dung", "he", "thong");

    /** Một mục của tài liệu. */
    public record Section(String title, String body) {

        /** Mục ở dạng Markdown, như trong tài liệu. */
        public String markdown() {
            return "## " + title + "\n\n" + body;
        }
    }

    private final String content;
    private final List<Section> sections;

    public UserGuide() {
        this(load());
    }

    UserGuide(String content) {
        // Chuẩn hóa xuống dòng để lời dặn gửi Claude giống hệt nhau dù file được checkout trên Windows hay Linux.
        this.content = content.replace("\r\n", "\n").strip();
        this.sections = parse(this.content);
    }

    /** Toàn văn tài liệu. */
    public String content() {
        return content;
    }

    public List<Section> sections() {
        return sections;
    }

    /**
     * Mục khớp câu hỏi nhất: mỗi từ có nghĩa của câu hỏi xuất hiện trong tiêu đề được 3 điểm, trong nội dung được 1
     * điểm; bằng điểm thì lấy mục đứng trước. Rỗng khi không mục nào có điểm.
     */
    public Optional<Section> bestSection(String question) {
        Set<String> words = keywords(question);
        Section best = null;
        int bestScore = 0;
        for (Section section : sections) {
            Set<String> titleWords = words(section.title());
            Set<String> bodyWords = words(section.body());
            int score = 0;
            for (String word : words) {
                if (titleWords.contains(word)) {
                    score += 3;
                }
                if (bodyWords.contains(word)) {
                    score += 1;
                }
            }
            if (score > bestScore) {
                best = section;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    static Set<String> keywords(String question) {
        Set<String> words = new LinkedHashSet<>();
        for (String word : TextNormalizer.fold(question).split(" ")) {
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static List<Section> parse(String markdown) {
        List<Section> result = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                if (title != null) {
                    result.add(new Section(title, body.toString().strip()));
                }
                title = line.substring(3).strip();
                body.setLength(0);
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        if (title != null) {
            result.add(new Section(title, body.toString().strip()));
        }
        return List.copyOf(result);
    }

    private static String load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được tài liệu hướng dẫn " + RESOURCE, ex);
        }
    }

    /** Tiêu đề các mục, dùng khi không mục nào khớp câu hỏi. */
    public List<String> titles() {
        return sections.stream().map(Section::title).toList();
    }

    private static Set<String> words(String text) {
        return new HashSet<>(Arrays.asList(TextNormalizer.fold(text).split(" ")));
    }
}
