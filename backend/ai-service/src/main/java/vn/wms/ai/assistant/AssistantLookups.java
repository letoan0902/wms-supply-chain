package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.wms.ai.config.AiAssistantProperties;
import vn.wms.ai.extraction.TextNormalizer;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Các phép tra cứu chỉ đọc của trợ lý AI (WMS-24.4), dùng chung cho công cụ của Claude ({@link AssistantTools}) và
 * chế độ giả lập ({@link MockAssistant}). Mỗi phép gọi API sẵn có của service khác qua {@link ServiceDataClient} với
 * danh tính người hỏi, rồi rút gọn kết quả còn đúng các trường cần để trả lời (không gửi Claude giá, ghi chú, địa chỉ,
 * người nhận...).
 *
 * <p>Mã SKU của doanh nghiệp khác trả {@code NOT_FOUND} như mã không tồn tại, vì product-service lọc theo
 * X-Company-Id của người hỏi.
 */
@Component
public class AssistantLookups {

    /** Kết quả một phép tra cứu: dữ liệu đã rút gọn hoặc thông báo lỗi tiếng Việt. */
    public record Lookup(ServiceDataClient.Status status, ObjectNode data, String message) {

        public boolean ok() {
            return status == ServiceDataClient.Status.OK;
        }

        static Lookup of(ObjectNode data) {
            return new Lookup(ServiceDataClient.Status.OK, data, null);
        }

        static Lookup failed(ServiceDataClient.Result result, String notFoundMessage) {
            String message = switch (result.status()) {
                case NOT_FOUND -> notFoundMessage;
                case FORBIDDEN -> ServiceDataClient.FORBIDDEN_MESSAGE;
                default -> result.message();
            };
            return new Lookup(result.status(), null, message);
        }

        static Lookup failed(ServiceDataClient.Status status, String message) {
            return new Lookup(status, null, message);
        }
    }

    static final String PRODUCT_SERVICE = "product-service";
    static final String INVENTORY_SERVICE = "inventory-service";
    static final String ORDER_SERVICE = "order-service";

    /**
     * Mã SKU, số chứng từ: chữ, số và các dấu {@code . _ -}, tối đa 64 ký tự. Không cho dấu {@code /} và {@code ..}
     * để giá trị do mô hình sinh ra không thể đổi đường dẫn API được gọi.
     */
    private static final Pattern CODE = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,63}");
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_TEXT_LENGTH = 200;
    private static final int MAX_ORDER_LINES = 30;

    /** Nhãn trạng thái đơn mua, giống giao diện (frontend/src/features/purchases/statusConfig.tsx). */
    static final Map<String, String> PURCHASE_STATUS = Map.of(
            "DRAFT", "Bản nháp", "PENDING", "Chờ duyệt", "APPROVED", "Đã phê duyệt", "REJECTED", "Đã từ chối",
            "CONFIRMED", "Đã xác nhận", "PARTIALLY_RECEIVED", "Nhập một phần", "COMPLETED", "Hoàn thành",
            "CANCELLED", "Đã hủy", "BACKORDER", "Thiếu hàng");
    /** Nhãn trạng thái đơn bán, giống giao diện (frontend/src/features/sales/statusConfig.tsx). */
    static final Map<String, String> SALES_STATUS = Map.of(
            "DRAFT", "Bản nháp", "CONFIRMED", "Đã xác nhận", "BACKORDER", "Thiếu hàng (Backorder)",
            "COMPLETED", "Đã hoàn thành", "CANCELLED", "Đã hủy", "PENDING", "Chờ duyệt", "APPROVED", "Đã phê duyệt");

    private final ServiceDataClient client;
    private final ObjectMapper objectMapper;
    private final AiAssistantProperties properties;
    private final Clock clock;

    @Autowired
    public AssistantLookups(ServiceDataClient client, ObjectMapper objectMapper, AiAssistantProperties properties) {
        this(client, objectMapper, properties, Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE));
    }

    AssistantLookups(ServiceDataClient client, ObjectMapper objectMapper, AiAssistantProperties properties,
                     Clock clock) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Tìm hàng hóa theo một phần mã SKU, tên hoặc mã vạch. */
    public Lookup findProducts(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > MAX_KEYWORD_LENGTH) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST,
                    "Cần từ khóa tìm hàng hóa (mã SKU, tên hoặc mã vạch), tối đa 100 ký tự.");
        }
        ServiceDataClient.Result result = client.get(PRODUCT_SERVICE, "/api/products", Map.of(),
                ordered("keyword", keyword.strip(), "page", 0, "size", Math.min(properties.maxListItems(), 10)));
        if (!result.ok()) {
            return Lookup.failed(result, "Không tìm thấy hàng hóa nào khớp \"" + keyword.strip() + "\".");
        }
        ArrayNode products = objectMapper.createArrayNode();
        for (JsonNode product : result.data().path("content")) {
            products.add(productSummary(product));
        }
        if (products.isEmpty()) {
            return Lookup.failed(ServiceDataClient.Status.NOT_FOUND,
                    "Không tìm thấy hàng hóa nào khớp \"" + keyword.strip() + "\".");
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("keyword", keyword.strip());
        data.put("totalMatches", result.data().path("totalElements").asLong(products.size()));
        data.set("products", products);
        return Lookup.of(data);
    }

    /**
     * Tồn của một SKU: tồn thực tế, đang giữ cho đơn, khả dụng (ATP) trên mọi kho hoặc một kho.
     *
     * @param warehouse mã, tên hoặc ID kho; {@code null} là mọi kho
     */
    public Lookup getStock(String sku, String warehouse) {
        if (!isCode(sku)) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Mã SKU không hợp lệ.");
        }
        ServiceDataClient.Result product = client.get(PRODUCT_SERVICE, "/api/products/sku/{sku}",
                Map.of("sku", sku.strip()), Map.of());
        if (!product.ok()) {
            return Lookup.failed(product, "Không tìm thấy hàng hóa có mã SKU " + sku.strip()
                    + " trong dữ liệu của doanh nghiệp bạn.");
        }
        ObjectNode warehouseInfo = null;
        if (warehouse != null && !warehouse.isBlank()) {
            Lookup resolved = resolveWarehouse(warehouse);
            if (!resolved.ok()) {
                return resolved;
            }
            warehouseInfo = resolved.data();
        }
        long productId = product.data().path("id").asLong();
        ServiceDataClient.Result atp = client.get(INVENTORY_SERVICE, "/api/inventory/stock/atp", Map.of(),
                ordered("productId", productId, "warehouseId",
                        warehouseInfo != null ? warehouseInfo.path("id").asLong() : null));
        if (!atp.ok()) {
            return Lookup.failed(atp, "Không tìm thấy dữ liệu tồn kho của mã SKU " + sku.strip() + ".");
        }
        ObjectNode data = productSummary(product.data());
        if (warehouseInfo != null) {
            data.set("warehouse", warehouseInfo);
        } else {
            data.put("scope", "Mọi kho của doanh nghiệp");
        }
        copyNumber(atp.data(), "onHandQuantity", data, "onHandQuantity");
        copyNumber(atp.data(), "reservedQuantity", data, "reservedQuantity");
        copyNumber(atp.data(), "atpQuantity", data, "availableQuantity");
        return Lookup.of(data);
    }

    /**
     * Tồn hiện tại của một kho theo từng mặt hàng (tồn cuối ngày hôm nay của báo cáo Xuất - Nhập - Tồn), nhiều nhất
     * trước.
     *
     * @param keyword lọc theo mã SKU hoặc tên hàng, có thể {@code null}
     */
    public Lookup getWarehouseStock(String warehouse, String keyword) {
        if (warehouse == null || warehouse.isBlank()) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Cần mã, tên hoặc ID kho.");
        }
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Từ khóa tối đa 100 ký tự.");
        }
        Lookup resolved = resolveWarehouse(warehouse);
        if (!resolved.ok()) {
            return resolved;
        }
        ObjectNode warehouseInfo = resolved.data();
        LocalDate today = LocalDate.now(clock);
        ServiceDataClient.Result report = client.get(INVENTORY_SERVICE, "/api/inventory/reports/in-out-balance",
                Map.of(), ordered("fromDate", today.toString(), "toDate", today.toString(),
                        "warehouseId", warehouseInfo.path("id").asLong(),
                        "keyword", keyword != null && !keyword.isBlank() ? keyword.strip() : null,
                        "sort", "closingQuantity", "direction", "desc", "page", 0,
                        "size", properties.maxListItems()));
        if (!report.ok()) {
            return Lookup.failed(report, "Không tìm thấy dữ liệu tồn của kho này.");
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.set("warehouse", warehouseInfo);
        data.put("date", today.toString());
        if (keyword != null && !keyword.isBlank()) {
            data.put("keyword", keyword.strip());
        }
        JsonNode rows = report.data().path("rows");
        data.put("productCount", rows.path("totalElements").asLong(0));
        copyNumber(report.data().path("totals"), "closingQuantity", data, "totalQuantity");
        ArrayNode items = objectMapper.createArrayNode();
        for (JsonNode row : rows.path("content")) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("sku", text(row, "skuCode"));
            item.put("name", text(row, "productName"));
            item.put("uom", text(row, "uom"));
            copyNumber(row, "closingQuantity", item, "quantity");
            items.add(item);
        }
        data.set("items", items);
        data.put("itemsShown", items.size());
        return Lookup.of(data);
    }

    /** Danh sách kho người hỏi được xem. */
    public Lookup listWarehouses() {
        ServiceDataClient.Result result = client.get(INVENTORY_SERVICE, "/api/inventory/warehouses", Map.of(),
                Map.of());
        if (!result.ok()) {
            return Lookup.failed(result, "Doanh nghiệp chưa có kho nào.");
        }
        ArrayNode warehouses = objectMapper.createArrayNode();
        for (JsonNode warehouse : result.data()) {
            warehouses.add(warehouseSummary(warehouse));
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("count", warehouses.size());
        data.set("warehouses", warehouses);
        return Lookup.of(data);
    }

    /** Trạng thái và nội dung chính của một đơn mua theo số chứng từ (PO-...). */
    public Lookup getPurchaseOrder(String poNumber) {
        if (!isCode(poNumber)) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Số đơn mua không hợp lệ.");
        }
        String number = poNumber.strip().toUpperCase();
        ServiceDataClient.Result result = client.get(ORDER_SERVICE, "/api/orders/purchase/number/{number}",
                Map.of("number", number), Map.of());
        if (!result.ok()) {
            return Lookup.failed(result, "Không tìm thấy đơn mua " + number
                    + " trong dữ liệu của doanh nghiệp bạn.");
        }
        JsonNode order = result.data();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("poNumber", text(order, "poNumber"));
        data.put("status", text(order, "status"));
        data.put("statusLabel", PURCHASE_STATUS.getOrDefault(text(order, "status"), text(order, "status")));
        data.put("supplierName", text(order, "supplierName"));
        data.put("warehouseName", text(order, "warehouseName"));
        data.put("orderDate", text(order, "orderDate"));
        data.put("expectedDate", text(order, "expectedDate"));
        copyNumber(order, "totalAmount", data, "totalAmount");
        data.put("approvedBy", text(order, "approvedBy"));
        data.put("rejectedReason", text(order, "rejectedReason"));
        data.set("items", orderLines(order.path("items"), false));
        return Lookup.of(data);
    }

    /** Trạng thái và nội dung chính của một đơn bán theo số chứng từ (SO-...). */
    public Lookup getSalesOrder(String soNumber) {
        if (!isCode(soNumber)) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Số đơn bán không hợp lệ.");
        }
        String number = soNumber.strip().toUpperCase();
        ServiceDataClient.Result result = client.get(ORDER_SERVICE, "/api/orders/sales/number/{number}",
                Map.of("number", number), Map.of());
        if (!result.ok()) {
            return Lookup.failed(result, "Không tìm thấy đơn bán " + number
                    + " trong dữ liệu của doanh nghiệp bạn.");
        }
        JsonNode order = result.data();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("soNumber", text(order, "soNumber"));
        data.put("status", text(order, "status"));
        data.put("statusLabel", SALES_STATUS.getOrDefault(text(order, "status"), text(order, "status")));
        data.put("customerName", text(order, "customerName"));
        data.put("warehouseName", text(order, "warehouseName"));
        data.put("orderDate", text(order, "orderDate"));
        data.put("expectedDate", text(order, "expectedDate"));
        copyNumber(order, "totalAmount", data, "totalAmount");
        data.put("confirmedBy", text(order, "confirmedBy"));
        data.put("backorderReason", text(order, "backorderReason"));
        data.set("items", orderLines(order.path("items"), true));
        return Lookup.of(data);
    }

    /**
     * Tìm kho theo ID, mã (không phân biệt hoa thường) hoặc một phần tên (không phân biệt dấu) trong danh sách kho
     * người hỏi được xem.
     */
    Lookup resolveWarehouse(String reference) {
        String ref = reference.strip();
        if (ref.length() > MAX_KEYWORD_LENGTH) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Tên kho tối đa 100 ký tự.");
        }
        Lookup all = listWarehouses();
        if (!all.ok()) {
            return all.status() == ServiceDataClient.Status.NOT_FOUND
                    ? Lookup.failed(ServiceDataClient.Status.NOT_FOUND, "Không tìm thấy kho \"" + ref + "\".")
                    : all;
        }
        List<ObjectNode> byName = new ArrayList<>();
        String folded = TextNormalizer.fold(ref);
        for (JsonNode node : all.data().path("warehouses")) {
            ObjectNode warehouse = (ObjectNode) node;
            if (ref.equals(warehouse.path("id").asText()) || ref.equalsIgnoreCase(warehouse.path("code").asText())) {
                return Lookup.of(warehouse);
            }
            if (!folded.isEmpty() && TextNormalizer.fold(warehouse.path("name").asText()).contains(folded)) {
                byName.add(warehouse);
            }
        }
        if (byName.size() == 1) {
            return Lookup.of(byName.get(0));
        }
        if (byName.size() > 1) {
            return Lookup.failed(ServiceDataClient.Status.BAD_REQUEST, "Có " + byName.size() + " kho khớp \"" + ref
                    + "\": " + String.join(", ", byName.stream().map(w -> w.path("code").asText() + " - "
                    + w.path("name").asText()).toList()) + ". Hãy nói rõ mã kho.");
        }
        return Lookup.failed(ServiceDataClient.Status.NOT_FOUND, "Không tìm thấy kho \"" + ref
                + "\" trong các kho bạn được xem.");
    }

    private ObjectNode productSummary(JsonNode product) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("sku", text(product, "sku"));
        summary.put("name", text(product, "name"));
        summary.put("uom", text(product, "uomName"));
        if (product.has("active")) {
            summary.put("active", product.path("active").asBoolean());
        }
        return summary;
    }

    private ObjectNode warehouseSummary(JsonNode warehouse) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("id", warehouse.path("id").asLong());
        summary.put("code", text(warehouse, "code"));
        summary.put("name", text(warehouse, "name"));
        summary.put("active", warehouse.path("active").asBoolean(true));
        return summary;
    }

    private ArrayNode orderLines(JsonNode items, boolean sales) {
        ArrayNode lines = objectMapper.createArrayNode();
        for (JsonNode item : items) {
            if (lines.size() >= MAX_ORDER_LINES) {
                break;
            }
            ObjectNode line = objectMapper.createObjectNode();
            line.put("sku", text(item, "skuCode"));
            line.put("name", text(item, "productName"));
            line.put("uom", text(item, "uom"));
            copyNumber(item, "quantity", line, "quantity");
            if (sales && item.has("isBackorder")) {
                line.put("backorder", item.path("isBackorder").asBoolean());
            }
            lines.add(line);
        }
        return lines;
    }

    /** Chuỗi đã cắt còn tối đa 200 ký tự; {@code null} khi thiếu. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().strip();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "..." : text;
    }

    /** Chép một trường số (giữ nguyên độ chính xác thập phân); thiếu thì ghi {@code null}, không tự đặt 0. */
    private static void copyNumber(JsonNode source, String field, ObjectNode target, String targetField) {
        JsonNode value = source.path(field);
        if (value.isNumber()) {
            target.put(targetField, value.decimalValue());
        } else {
            target.putNull(targetField);
        }
    }

    static boolean isCode(String value) {
        return value != null && CODE.matcher(value.strip()).matches() && !value.contains("..");
    }

    /** Map giữ thứ tự, cho phép giá trị {@code null} (tham số bị bỏ qua). */
    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
