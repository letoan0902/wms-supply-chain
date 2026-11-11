package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import vn.wms.ai.client.AiTool;
import vn.wms.ai.client.AiToolCall;
import vn.wms.ai.client.AiToolExecutor;
import vn.wms.ai.client.AiToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ công cụ cố định, CHỈ ĐỌC, của trợ lý AI (WMS-24.4). Không có công cụ nào tạo, sửa, xóa dữ liệu: trường hợp xấu
 * nhất khi Claude bị lừa bởi nội dung trong tên hàng hay ghi chú chỉ là một câu trả lời sai, không phải một thao tác.
 * Tên công cụ lạ hoặc tham số sai trả kết quả lỗi để Claude tự giải thích, không ném ngoại lệ.
 */
@Component
public class AssistantTools implements AiToolExecutor {

    public static final String FIND_PRODUCT = "find_product";
    public static final String GET_STOCK = "get_stock";
    public static final String GET_WAREHOUSE_STOCK = "get_warehouse_stock";
    public static final String LIST_WAREHOUSES = "list_warehouses";
    public static final String GET_PURCHASE_ORDER = "get_purchase_order";
    public static final String GET_SALES_ORDER = "get_sales_order";

    /** Định nghĩa gửi Claude, giữ nguyên thứ tự và nội dung giữa các lần gọi để bộ nhớ đệm prompt dùng lại được. */
    static final List<AiTool> DEFINITIONS = List.of(
            new AiTool(FIND_PRODUCT, "Tìm hàng hóa của doanh nghiệp theo một phần mã SKU, tên hoặc mã vạch. Gọi khi "
                    + "người dùng nhắc tới tên hàng mà không có mã SKU chính xác, hoặc khi get_stock báo không tìm "
                    + "thấy SKU. Trả tối đa 10 mặt hàng (sku, name, uom, active).",
                    Map.of("keyword", with("Một phần mã SKU, tên hàng hoặc mã vạch")), List.of("keyword")),
            new AiTool(GET_STOCK, "Tồn kho của một mã SKU: tồn thực tế (onHandQuantity), đang giữ cho đơn bán "
                    + "(reservedQuantity), khả dụng (availableQuantity). Gọi khi người dùng hỏi còn bao nhiêu, tồn "
                    + "bao nhiêu của một mặt hàng. Không truyền warehouse thì tính trên mọi kho của doanh nghiệp.",
                    Map.of("sku", with("Mã SKU chính xác"),
                            "warehouse", with("Không bắt buộc: mã kho, tên kho hoặc ID kho")),
                    List.of("sku")),
            new AiTool(GET_WAREHOUSE_STOCK, "Tồn hiện tại của một kho theo từng mặt hàng (nhiều nhất trước, tối đa 20 "
                    + "dòng) kèm số mặt hàng và tổng số lượng. Gọi khi người dùng hỏi kho nào đó đang có những gì, "
                    + "tồn bao nhiêu.",
                    Map.of("warehouse", with("Mã kho, tên kho hoặc ID kho"),
                            "keyword", with("Không bắt buộc: lọc theo mã SKU hoặc tên hàng")),
                    List.of("warehouse")),
            new AiTool(LIST_WAREHOUSES, "Danh sách kho người dùng được xem (id, code, name, active). Gọi khi cần "
                    + "biết mã kho hoặc khi người dùng hỏi có những kho nào.", Map.of(), List.of()),
            new AiTool(GET_PURCHASE_ORDER, "Trạng thái và nội dung chính của một đơn mua hàng theo số chứng từ, ví dụ "
                    + "PO-20261110-0001: trạng thái, nhà cung cấp, kho nhận, ngày, tổng tiền, các dòng hàng.",
                    Map.of("poNumber", with("Số đơn mua, dạng PO-YYYYMMDD-XXXX")), List.of("poNumber")),
            new AiTool(GET_SALES_ORDER, "Trạng thái và nội dung chính của một đơn bán hàng theo số chứng từ, ví dụ "
                    + "SO-20261110-0001: trạng thái, khách hàng, kho xuất, ngày, tổng tiền, các dòng hàng.",
                    Map.of("soNumber", with("Số đơn bán, dạng SO-YYYYMMDD-XXXX")), List.of("soNumber")));

    private final AssistantLookups lookups;
    private final ObjectMapper objectMapper;

    public AssistantTools(AssistantLookups lookups, ObjectMapper objectMapper) {
        this.lookups = lookups;
        this.objectMapper = objectMapper;
    }

    public List<AiTool> definitions() {
        return DEFINITIONS;
    }

    @Override
    public AiToolResult execute(AiToolCall call) {
        AssistantLookups.Lookup lookup = switch (call.name()) {
            case FIND_PRODUCT -> lookups.findProducts(call.text("keyword"));
            case GET_STOCK -> lookups.getStock(call.text("sku"), call.text("warehouse"));
            case GET_WAREHOUSE_STOCK -> lookups.getWarehouseStock(call.text("warehouse"), call.text("keyword"));
            case LIST_WAREHOUSES -> lookups.listWarehouses();
            case GET_PURCHASE_ORDER -> lookups.getPurchaseOrder(call.text("poNumber"));
            case GET_SALES_ORDER -> lookups.getSalesOrder(call.text("soNumber"));
            default -> null;
        };
        if (lookup == null) {
            return AiToolResult.error(error("UNKNOWN_TOOL", "Không có công cụ tên này. Chỉ dùng các công cụ đã "
                    + "khai báo; trợ lý chỉ đọc dữ liệu, không tạo, sửa hay xóa."));
        }
        if (lookup.ok()) {
            return AiToolResult.ok(lookup.data().toString());
        }
        return AiToolResult.error(error(lookup.status().name(), lookup.message()));
    }

    private String error(String status, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", status);
        node.put("message", message);
        return node.toString();
    }

    /** Lược đồ một tham số chuỗi, thứ tự khóa cố định. */
    private static Map<String, Object> with(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return Collections.unmodifiableMap(schema);
    }
}
