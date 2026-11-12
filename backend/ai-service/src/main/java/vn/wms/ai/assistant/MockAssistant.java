package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import vn.wms.ai.extraction.TextNormalizer;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trợ lý ở chế độ giả lập ({@code AI_MOCK=true} hoặc chưa có khóa): không gọi Claude, không bịa số liệu. Nhận diện
 * câu hỏi bằng quy tắc cố định rồi chạy đúng các phép tra cứu mà Claude dùng ({@link AssistantLookups}), trình bày kết
 * quả thật bằng câu soạn sẵn:
 * <ol>
 *   <li>có số chứng từ {@code PO-...} hoặc {@code SO-...}: trạng thái đơn mua, đơn bán;</li>
 *   <li>có mã dạng SKU (chữ lẫn số, hoặc chữ in hoa có gạch nối): tồn của SKU (kèm kho nếu câu có "kho &lt;mã&gt;");</li>
 *   <li>có "kho &lt;mã&gt;" và hỏi về tồn: tồn của kho; chỉ hỏi "tồn" mà không nói SKU hay kho: danh sách kho để hỏi
 *       lại;</li>
 *   <li>còn lại: mục hướng dẫn sử dụng khớp nhất theo từ khóa.</li>
 * </ol>
 * Mọi câu trả lời mở đầu bằng {@link #MOCK_NOTICE}.
 */
@Component
public class MockAssistant {

    /** Dòng đầu của mọi câu trả lời giả lập. */
    public static final String MOCK_NOTICE = "Đây là trợ lý giả lập (hệ thống chưa bật AI thật), trả lời theo quy tắc "
            + "cố định từ dữ liệu và tài liệu hướng dẫn của hệ thống.";

    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("(?i)\\b(PO|SO)-[A-Za-z0-9][A-Za-z0-9-]{0,40}");
    private static final Pattern EXPLICIT_SKU =
            Pattern.compile("(?iu)\\b(?:sku|mã hàng|ma hang)\\s*[:#]?\\s*([\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,63})");
    private static final Pattern WAREHOUSE_REF = Pattern.compile("(?iu)\\bkho\\s+([\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,63})");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*");
    /** Từ chỉ thao tác (đã bỏ dấu): câu hỏi cách làm, không phải tra cứu số liệu. */
    private static final Set<String> HOW_TO_WORDS = Set.of("tao", "them", "sua", "xoa", "lap", "cach", "huong",
            "the nao", "lam sao", "duyet", "huy");
    private static final int MAX_LISTED = 10;

    private final AssistantLookups lookups;
    private final UserGuide guide;

    public MockAssistant(AssistantLookups lookups, UserGuide guide) {
        this.lookups = lookups;
        this.guide = guide;
    }

    /**
     * Câu trả lời giả lập.
     *
     * @return nội dung trả lời và tên phép tra cứu đã chạy (rỗng khi trả lời từ tài liệu)
     */
    public Answer answer(String question) {
        Matcher document = DOCUMENT_NUMBER.matcher(question);
        if (document.find()) {
            String number = document.group();
            boolean purchase = number.toUpperCase(Locale.ROOT).startsWith("PO");
            AssistantLookups.Lookup lookup = purchase ? lookups.getPurchaseOrder(number)
                    : lookups.getSalesOrder(number);
            return new Answer(withNotice(lookup.ok() ? describeOrder(lookup.data(), purchase) : lookup.message()),
                    purchase ? AssistantTools.GET_PURCHASE_ORDER : AssistantTools.GET_SALES_ORDER);
        }

        String folded = " " + TextNormalizer.fold(question) + " ";
        String warehouse = warehouseReference(question);
        String sku = skuCandidate(question, warehouse);
        if (sku != null) {
            AssistantLookups.Lookup lookup = lookups.getStock(sku, warehouse);
            return new Answer(withNotice(lookup.ok() ? describeStock(lookup.data()) : lookup.message()),
                    AssistantTools.GET_STOCK);
        }
        boolean asksStock = folded.contains(" ton ");
        boolean howTo = HOW_TO_WORDS.stream().anyMatch(word -> folded.contains(" " + word + " "));
        if (warehouse != null && (asksStock || !howTo)) {
            AssistantLookups.Lookup lookup = lookups.getWarehouseStock(warehouse, null);
            return new Answer(withNotice(lookup.ok() ? describeWarehouseStock(lookup.data()) : lookup.message()),
                    AssistantTools.GET_WAREHOUSE_STOCK);
        }
        if (asksStock && !howTo) {
            AssistantLookups.Lookup lookup = lookups.listWarehouses();
            return new Answer(withNotice(lookup.ok() ? describeWarehouses(lookup.data()) : lookup.message()),
                    AssistantTools.LIST_WAREHOUSES);
        }
        return new Answer(withNotice(guideAnswer(question)), null);
    }

    /**
     * @param text   nội dung trả lời
     * @param lookup tên phép tra cứu đã chạy, {@code null} khi trả lời từ tài liệu hướng dẫn
     */
    public record Answer(String text, String lookup) {
    }

    private String guideAnswer(String question) {
        return guide.bestSection(question)
                .map(section -> "Theo tài liệu hướng dẫn, mục \"" + section.title() + "\":\n\n" + section.body())
                .orElse("Chưa tìm thấy mục hướng dẫn phù hợp với câu hỏi. Tài liệu có các mục: "
                        + String.join("; ", guide.titles()) + ". Bạn có thể hỏi tồn kho theo mã SKU (ví dụ "
                        + "\"tồn của SKU-001\"), tồn theo kho (\"tồn kho WH01\") hoặc trạng thái đơn theo số chứng "
                        + "từ (PO-..., SO-...).");
    }

    /** Mã kho sau chữ "kho" (ví dụ "kho WH01", "kho 3"); bỏ qua từ thường như "kho hàng", "kho nào". */
    static String warehouseReference(String question) {
        Matcher matcher = WAREHOUSE_REF.matcher(question);
        while (matcher.find()) {
            String token = trimPunctuation(matcher.group(1));
            if (looksLikeCode(token) || token.chars().allMatch(Character::isDigit)) {
                return token;
            }
        }
        return null;
    }

    /** Mã SKU được nêu rõ ("SKU ABC", "mã hàng ABC") hoặc token trông như mã (chữ lẫn số, chữ in hoa có gạch nối). */
    static String skuCandidate(String question, String warehouse) {
        Matcher explicit = EXPLICIT_SKU.matcher(question);
        if (explicit.find()) {
            String token = trimPunctuation(explicit.group(1));
            if (!token.equalsIgnoreCase(warehouse) && !isPlainWord(token)) {
                return token;
            }
        }
        Matcher tokens = TOKEN.matcher(question);
        while (tokens.find()) {
            String token = trimPunctuation(tokens.group());
            if (token.equalsIgnoreCase(warehouse) || token.length() < 3) {
                continue;
            }
            if (looksLikeCode(token)) {
                return token;
            }
        }
        return null;
    }

    /** Có cả chữ lẫn số, hoặc là chữ in hoa (không dấu) có gạch nối hay gạch dưới. */
    private static boolean looksLikeCode(String token) {
        boolean letter = token.chars().anyMatch(Character::isLetter);
        boolean digit = token.chars().anyMatch(Character::isDigit);
        if (letter && digit) {
            return true;
        }
        boolean separator = token.indexOf('-') >= 0 || token.indexOf('_') >= 0;
        return separator && token.equals(token.toUpperCase(Locale.ROOT)) && token.matches("[A-Z0-9._-]+");
    }

    /** Từ tiếng Việt thường (toàn chữ thường, không số): "sku là gì" không phải mã. */
    private static boolean isPlainWord(String token) {
        return token.equals(token.toLowerCase(Locale.ROOT)) && token.chars().noneMatch(Character::isDigit)
                && token.indexOf('-') < 0 && token.indexOf('_') < 0;
    }

    private static String trimPunctuation(String token) {
        return token.replaceAll("[._-]+$", "");
    }

    private static String describeStock(ObjectNode data) {
        String uom = unit(data.path("uom"));
        String where = data.hasNonNull("warehouse")
                ? "tại kho " + data.path("warehouse").path("code").asText() + " - "
                + data.path("warehouse").path("name").asText()
                : "trên mọi kho của doanh nghiệp";
        return "Mã SKU " + data.path("sku").asText() + " (" + data.path("name").asText() + ") " + where
                + ": tồn thực tế " + number(data.path("onHandQuantity")) + uom
                + ", đang giữ cho đơn bán " + number(data.path("reservedQuantity")) + uom
                + ", còn khả dụng " + number(data.path("availableQuantity")) + uom + ".";
    }

    private static String describeWarehouseStock(ObjectNode data) {
        JsonNode warehouse = data.path("warehouse");
        long count = data.path("productCount").asLong();
        String header = "Kho " + warehouse.path("code").asText() + " - " + warehouse.path("name").asText()
                + " (tồn cuối ngày " + data.path("date").asText() + "): ";
        if (count == 0) {
            return header + "không có mặt hàng nào còn tồn hoặc phát sinh trong ngày.";
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            if (lines.size() >= MAX_LISTED) {
                break;
            }
            lines.add("- " + item.path("sku").asText() + " (" + item.path("name").asText() + "): "
                    + number(item.path("quantity")) + unit(item.path("uom")));
        }
        return header + count + " mặt hàng, tổng số lượng " + number(data.path("totalQuantity"))
                + ". Các mặt hàng tồn nhiều nhất:\n" + String.join("\n", lines);
    }

    private static String describeWarehouses(ObjectNode data) {
        if (data.path("count").asInt() == 0) {
            return "Bạn chưa có kho nào để xem tồn.";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode warehouse : data.path("warehouses")) {
            names.add(warehouse.path("code").asText() + " - " + warehouse.path("name").asText());
        }
        return "Bạn muốn xem tồn của mặt hàng hay của kho nào? Hãy nêu mã SKU (ví dụ \"tồn của SKU-001\") hoặc mã "
                + "kho (ví dụ \"tồn kho " + data.path("warehouses").get(0).path("code").asText()
                + "\"). Các kho bạn được xem: " + String.join("; ", names) + ".";
    }

    private static String describeOrder(ObjectNode data, boolean purchase) {
        StringBuilder text = new StringBuilder(purchase ? "Đơn mua " : "Đơn bán ")
                .append(data.path(purchase ? "poNumber" : "soNumber").asText())
                .append(": trạng thái ").append(data.path("statusLabel").asText())
                .append(purchase ? "; nhà cung cấp " : "; khách hàng ")
                .append(orDash(data.path(purchase ? "supplierName" : "customerName")))
                .append(purchase ? "; kho nhận " : "; kho xuất ").append(orDash(data.path("warehouseName")))
                .append("; ngày lập ").append(orDash(data.path("orderDate")))
                .append(purchase ? "; ngày nhận dự kiến " : "; ngày giao dự kiến ")
                .append(orDash(data.path("expectedDate")))
                .append("; ").append(data.path("items").size()).append(" dòng hàng.");
        if (data.hasNonNull("rejectedReason")) {
            text.append(" Lý do từ chối: ").append(data.path("rejectedReason").asText()).append('.');
        }
        if (data.hasNonNull("backorderReason")) {
            text.append(" Lý do thiếu hàng: ").append(data.path("backorderReason").asText()).append('.');
        }
        return text.toString();
    }

    private static String orDash(JsonNode value) {
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? "chưa có" : value.asText();
    }

    private static String unit(JsonNode uom) {
        return uom.isMissingNode() || uom.isNull() || uom.asText().isBlank() ? "" : " " + uom.asText();
    }

    /** Số kiểu Việt Nam: "1.250,5"; trường {@code null} là "không xác định" (không thay bằng 0). */
    static String number(JsonNode value) {
        if (!value.isNumber()) {
            return "không xác định";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.###", symbols);
        format.setParseBigDecimal(true);
        BigDecimal decimal = value.decimalValue();
        return format.format(decimal);
    }

    private static String withNotice(String text) {
        return MOCK_NOTICE + "\n\n" + text;
    }
}
