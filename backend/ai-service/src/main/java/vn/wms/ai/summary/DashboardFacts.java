package vn.wms.ai.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Số liệu điều hành dùng cho bản tóm tắt (WMS-18.1), dựng từ 8 thẻ KPI của notification-service
 * ({@code GET /api/notifications/dashboard/kpi-summary}) và tổng nhập, xuất từ đầu tháng của báo cáo Xuất - Nhập - Tồn.
 *
 * <p>Quy tắc để không bịa số:
 * <ul>
 *   <li>thẻ có {@code visible = false} (thẻ tài chính bị che với vai trò không phải ADMIN, ACCOUNTANT) không đưa giá trị
 *       vào (notification-service điền 0 cho thẻ bị che), chỉ ghi tên vào {@code hiddenForRole};</li>
 *   <li>giá trị {@code null} (giá trị tồn kho chưa có giá vốn, tăng trưởng khi tháng trước không có doanh số) giữ
 *       {@code null} kèm lời giải thích của notification-service;</li>
 *   <li>thiếu số liệu nhập, xuất (không có quyền xem báo cáo, sổ kho bắt đầu giữa tháng) thì ghi rõ lý do.</li>
 * </ul>
 */
public final class DashboardFacts {

    static final int URGENT_OCCUPANCY_PERCENT = 85;

    private final JsonNode kpi;
    private final JsonNode inOut;
    private final String inOutNote;

    /**
     * @param kpi       trường {@code data} của kpi-summary
     * @param inOut     trường {@code data} của báo cáo in-out-balance từ đầu tháng tới hôm nay, {@code null} khi không đọc
     *                  được
     * @param inOutNote lý do không có số liệu nhập, xuất khi {@code inOut} là {@code null}
     */
    public DashboardFacts(JsonNode kpi, JsonNode inOut, String inOutNote) {
        this.kpi = kpi;
        this.inOut = inOut;
        this.inOutNote = inOutNote;
    }

    /** Người xem có được thấy chỉ số tài chính (thẻ công nợ phải thu hiển thị). */
    public boolean financialsVisible() {
        return kpi.path("receivables").path("visible").asBoolean(false);
    }

    /** Tài liệu JSON gửi Claude: chỉ những con số người xem được thấy. */
    public ObjectNode toJson(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        JsonNode meta = kpi.path("meta");
        root.put("scope", textOr(meta.path("warehouseName"), "Không rõ phạm vi"));
        root.put("calculatedAt", textOr(meta.path("calculatedAt"), null));
        ArrayNode metrics = root.putArray("metrics");
        ArrayNode hidden = root.putArray("hiddenForRole");

        card(mapper, metrics, hidden, "receivables", "amount", "VNĐ", "overdueCustomersCount",
                "overdueCustomers");
        card(mapper, metrics, hidden, "payables", "amount", "VNĐ", "dueSoonSuppliersCount",
                "suppliersDueThisWeek");
        card(mapper, metrics, hidden, "inventoryValuation", "amount", "VNĐ", null, null);
        ObjectNode revenue = card(mapper, metrics, hidden, "monthlyRevenue", "amount", "VNĐ", null, null);
        if (revenue != null) {
            JsonNode growth = kpi.path("monthlyRevenue").path("growthRatePercentage");
            if (growth.isNumber()) {
                revenue.put("growthPercentVsLastMonth", growth.decimalValue());
            } else {
                revenue.putNull("growthPercentVsLastMonth");
            }
        }
        card(mapper, metrics, hidden, "availableStock", "totalQuantity",
                textOr(kpi.path("availableStock").path("unit"), null), null, null);
        card(mapper, metrics, hidden, "pendingSalesOrders", "totalOrders", "đơn", "urgentOrders", "urgentOrders");
        card(mapper, metrics, hidden, "pendingPurchaseOrders", "totalOrders", "đơn", "arrivingTodayCount",
                "arrivingToday");
        ObjectNode utilization = card(mapper, metrics, hidden, "warehouseUtilization", "occupancyRatePercentage",
                "%", null, null);
        if (utilization != null) {
            utilization.put("usedLocations", kpi.path("warehouseUtilization").path("usedLocations").asLong());
            utilization.put("totalLocations", kpi.path("warehouseUtilization").path("totalLocations").asLong());
        }

        ObjectNode movement = root.putObject("inOutThisMonth");
        if (inOut != null) {
            movement.put("fromDate", textOr(inOut.path("fromDate"), null));
            movement.put("toDate", textOr(inOut.path("toDate"), null));
            JsonNode totals = inOut.path("totals");
            number(movement, "quantityIn", totals.path("quantityIn"));
            number(movement, "quantityOut", totals.path("quantityOut"));
            number(movement, "closingQuantity", totals.path("closingQuantity"));
            movement.put("note", textOr(inOut.path("dataNote"), null));
        } else {
            movement.put("available", false);
            movement.put("note", inOutNote);
        }
        return root;
    }

    /**
     * Thêm một thẻ vào {@code metrics}; thẻ bị che thì chỉ thêm tên vào {@code hidden} và trả {@code null}.
     */
    private ObjectNode card(ObjectMapper mapper, ArrayNode metrics, ArrayNode hidden, String field,
                            String valueField, String unit, String countField, String countName) {
        JsonNode card = kpi.path(field);
        if (card.isMissingNode() || card.isNull()) {
            return null;
        }
        String title = textOr(card.path("title"), field);
        if (!card.path("visible").asBoolean(true)) {
            hidden.add(title);
            return null;
        }
        ObjectNode metric = mapper.createObjectNode();
        metric.put("name", title);
        number(metric, "value", card.path(valueField));
        if (unit != null) {
            metric.put("unit", unit);
        }
        if (countField != null) {
            metric.put(countName, card.path(countField).asLong());
        }
        metric.put("note", textOr(card.path("subText"), null));
        metrics.add(metric);
        return metric;
    }

    /** Các ý tóm tắt soạn sẵn cho chế độ giả lập, chỉ dùng số đã có. */
    public List<String> mockBullets() {
        List<String> bullets = new ArrayList<>();
        JsonNode so = kpi.path("pendingSalesOrders");
        JsonNode po = kpi.path("pendingPurchaseOrders");
        bullets.add("Đơn bán chờ xuất kho: " + so.path("totalOrders").asLong() + " đơn, trong đó "
                + so.path("urgentOrders").asLong() + " đơn cần lấy hàng gấp. Đơn mua chờ tiếp nhận: "
                + po.path("totalOrders").asLong() + " đơn, " + po.path("arrivingTodayCount").asLong()
                + " lô dự kiến đến hôm nay.");
        JsonNode stock = kpi.path("availableStock");
        JsonNode utilization = kpi.path("warehouseUtilization");
        bullets.add("Tồn kho khả dụng: " + format(stock.path("totalQuantity")) + " "
                + textOr(stock.path("unit"), "") + "; tỷ lệ lấp đầy kho "
                + format(utilization.path("occupancyRatePercentage")) + "% ("
                + utilization.path("usedLocations").asLong() + "/" + utilization.path("totalLocations").asLong()
                + " vị trí).");
        if (inOut != null) {
            JsonNode totals = inOut.path("totals");
            if (totals.path("quantityIn").isNumber() && totals.path("quantityOut").isNumber()) {
                bullets.add("Từ đầu tháng: nhập " + format(totals.path("quantityIn")) + ", xuất "
                        + format(totals.path("quantityOut")) + ", tồn cuối " + format(totals.path("closingQuantity"))
                        + " (đơn vị gốc của từng mặt hàng).");
            } else {
                bullets.add("Nhập, xuất từ đầu tháng chưa xác định được: "
                        + textOr(inOut.path("dataNote"), "sổ kho chưa đủ số liệu cho cả tháng.")
                        + " Tồn cuối hiện tại: " + format(totals.path("closingQuantity")) + ".");
            }
        } else {
            bullets.add("Nhập, xuất từ đầu tháng: chưa có số liệu (" + inOutNote + ").");
        }
        if (financialsVisible()) {
            JsonNode receivables = kpi.path("receivables");
            JsonNode payables = kpi.path("payables");
            bullets.add("Công nợ phải thu " + money(receivables.path("amount")) + " ("
                    + receivables.path("overdueCustomersCount").asLong() + " khách hàng có dư nợ đến hạn); công nợ "
                    + "phải trả " + money(payables.path("amount")) + " ("
                    + payables.path("dueSoonSuppliersCount").asLong() + " nhà cung cấp đến hạn trong tuần).");
            JsonNode revenue = kpi.path("monthlyRevenue");
            JsonNode valuation = kpi.path("inventoryValuation");
            bullets.add("Doanh số xuất bán tháng " + money(revenue.path("amount")) + ", "
                    + lowerFirst(textOr(revenue.path("subText"), "chưa có so sánh với tháng trước")) + ". Giá trị "
                    + "tồn kho: " + (valuation.path("amount").isNumber() ? money(valuation.path("amount"))
                    : lowerFirst(textOr(valuation.path("subText"), "chưa tính được"))) + ".");
        } else {
            bullets.add("Các chỉ số tài chính (công nợ, doanh số, giá trị tồn kho) không hiển thị với vai trò của "
                    + "bạn.");
        }
        return bullets;
    }

    /** Một đến hai hành động gợi ý theo quy tắc cố định. */
    public List<String> mockActions() {
        List<String> actions = new ArrayList<>();
        long urgent = kpi.path("pendingSalesOrders").path("urgentOrders").asLong();
        long arriving = kpi.path("pendingPurchaseOrders").path("arrivingTodayCount").asLong();
        double occupancy = kpi.path("warehouseUtilization").path("occupancyRatePercentage").asDouble();
        long overdue = financialsVisible() ? kpi.path("receivables").path("overdueCustomersCount").asLong() : 0;
        if (urgent > 0) {
            actions.add("Ưu tiên điều phối lấy hàng cho " + urgent + " đơn bán gấp.");
        }
        if (arriving > 0) {
            actions.add("Chuẩn bị vị trí và nhân sự nhận " + arriving + " lô hàng dự kiến đến hôm nay.");
        }
        if (occupancy >= URGENT_OCCUPANCY_PERCENT) {
            actions.add("Kho đã lấp đầy " + format(kpi.path("warehouseUtilization").path("occupancyRatePercentage"))
                    + "%: cân nhắc điều chuyển hàng hoặc giải phóng vị trí.");
        }
        if (overdue > 0) {
            actions.add("Nhắc thu nợ " + overdue + " khách hàng có dư nợ đến hạn.");
        }
        if (actions.isEmpty()) {
            actions.add("Theo dõi các đơn chờ xử lý ở menu \"Bán hàng và xuất kho\" và \"Mua hàng và nhập kho\".");
        }
        return List.copyOf(actions.subList(0, Math.min(2, actions.size())));
    }

    private static void number(ObjectNode target, String field, JsonNode value) {
        if (value.isNumber()) {
            target.put(field, value.decimalValue());
        } else {
            target.putNull(field);
        }
    }

    private static String textOr(JsonNode node, String fallback) {
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? fallback : node.asText();
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /** Số kiểu Việt Nam ("1.250,5"); không phải số thì "không xác định". */
    static String format(JsonNode value) {
        if (!value.isNumber()) {
            return "không xác định";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        BigDecimal decimal = value.decimalValue();
        return new DecimalFormat("#,##0.##", symbols).format(decimal);
    }

    private static String money(JsonNode value) {
        return value.isNumber() ? format(value) + " đ" : "không xác định";
    }
}
