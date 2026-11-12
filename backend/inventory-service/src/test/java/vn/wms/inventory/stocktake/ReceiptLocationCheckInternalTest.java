package vn.wms.inventory.stocktake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hợp đồng của endpoint nội bộ {@code POST /internal/inventory/receipts/location-check} mà order-service gọi trước
 * khi lưu phiếu nhập kho (WMS-20.4, TC-STK-01.10): thân yêu cầu đúng tên trường order-service gửi, vị trí xác định
 * như listener {@code order.received}, chỉ cần doanh nghiệp (không cần người dùng), dữ liệu tách theo doanh nghiệp.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceiptLocationCheckInternalTest extends StocktakeTestSupport {

    private static final String URL = "/internal/inventory/receipts/location-check";

    private Map<String, Object> body(Long warehouseId, Long productId, Long locationId, String locationCode) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", productId);
        line.put("locationId", locationId);
        line.put("locationCode", locationCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("lines", List.of(line));
        return body;
    }

    @Test
    @DisplayName("Vị trí đang đóng băng trả về xung đột có mã vị trí và mã đợt; vị trí khác trả rỗng")
    void reportsFrozenLocationAndPassesFreeOne() throws Exception {
        long sessionId = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        String sessionCode = data(getAs("/api/inventory/stocktakes/" + sessionId, COMPANY_A, manager1)).path("code").asText();

        postJson(URL, body(warehouse1.getId(), PRODUCT_1, binA1.getId(), null), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sessionId", is((int) sessionId)))
                .andExpect(jsonPath("$.data[0].sessionCode", is(sessionCode)))
                .andExpect(jsonPath("$.data[0].binLocationId", is(binA1.getId().intValue())))
                .andExpect(jsonPath("$.data[0].binLocationCode", is(binA1.getCode())))
                .andExpect(jsonPath("$.data[0].productId", is(PRODUCT_1.intValue())));

        postJson(URL, body(warehouse1.getId(), PRODUCT_1, binA2.getId(), null), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Dòng theo mã vị trí hoặc không chọn vị trí được xác định như listener nhập kho (không chọn thì ô đầu tiên của kho)")
    void resolvesLocationLikeTheReceiptListener() throws Exception {
        createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);

        postJson(URL, body(warehouse1.getId(), PRODUCT_2, null, binA1.getCode()), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        // Không chọn vị trí: listener cất vào ô đầu tiên theo khu, kệ, ô (binA1 = ZA-R01-B01) nên cũng bị chặn
        postJson(URL, body(warehouse1.getId(), PRODUCT_2, null, null), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].binLocationId", is(binA1.getId().intValue())));
        // Vị trí của kho khác không thuộc kho nhận: listener bỏ qua và dùng ô đầu tiên của kho nhận
        postJson(URL, body(warehouse1.getId(), PRODUCT_2, binW2.getId(), null), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Không đợt kiểm kê nào thì trả rỗng; thiếu warehouseId hoặc dòng thì 400")
    void emptyWhenNothingFrozenAndValidatesBody() throws Exception {
        postJson(URL, body(warehouse1.getId(), PRODUCT_1, binA1.getId(), null), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        postJson(URL, Map.of("lines", List.of()), COMPANY_A, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tách doanh nghiệp: doanh nghiệp B không kiểm tra được kho của A (404), đợt kiểm kê của A không ảnh hưởng B")
    void isolatedPerCompany() throws Exception {
        createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);

        postJson(URL, body(warehouse1.getId(), PRODUCT_1, binA1.getId(), null), COMPANY_B, null)
                .andExpect(status().isNotFound());
        postJson(URL, body(warehouseB.getId(), PRODUCT_1, binOfCompanyB.getId(), null), COMPANY_B, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
