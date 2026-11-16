package vn.wms.inventory.stocktake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.inventory.cache.FakeStringRedis;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tồn khả dụng theo phạm vi kho của người gọi (WMS-20.4): {@code GET /api/inventory/stock/atp} và
 * {@code POST /api/inventory/stock/atp/batch} trả 403 với kho ngoài phạm vi, không truyền kho thì vai trò giới hạn theo
 * kho chỉ nhận tổng các kho được gán; ADMIN, ACCOUNTANT giữ nguyên tổng mọi kho. Bộ nhớ đệm Redis bật để chứng minh số
 * đệm "mọi kho" của ADMIN không lọt sang người dùng giới hạn theo kho và ngược lại. Endpoint nội bộ
 * {@code GET /internal/inventory/stock/atp} (order-service gọi khi lập, xác nhận đơn bán) không lọc theo kho, kể cả với
 * tài khoản SALES chưa được gán kho.
 */
@SpringBootTest(properties = "app.stock-cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StockAtpWarehouseScopeTest.FakeRedisConfig.class)
class StockAtpWarehouseScopeTest extends StocktakeTestSupport {

    private static final String ATP = "/api/inventory/stock/atp";
    private static final String BATCH = "/api/inventory/stock/atp/batch";
    private static final String INTERNAL_ATP = "/internal/inventory/stock/atp";

    @TestConfiguration
    static class FakeRedisConfig {
        @Bean
        FakeStringRedis fakeStringRedis() {
            return new FakeStringRedis(System::currentTimeMillis);
        }

        @Bean
        @Primary
        StringRedisTemplate fakeStringRedisTemplate(FakeStringRedis fake) {
            return fake.template();
        }
    }

    @Autowired
    private FakeStringRedis fakeRedis;

    private TestUser salesWithoutWarehouse;
    private TestUser managerOfBoth;

    /** PRODUCT_1: kho 1 tồn 10 giữ 2, kho 2 tồn 30 giữ 5. PRODUCT_2: chỉ kho 2 tồn 7. */
    @BeforeEach
    void setUpStock() {
        fakeRedis.clear();
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "2.000");
        stock(COMPANY_A, binW2, PRODUCT_1, null, "30.000", "5.000");
        stock(COMPANY_A, binW2, PRODUCT_2, null, "7.000", "0");
        salesWithoutWarehouse = new TestUser("sales01", "SALES", "");
        managerOfBoth = new TestUser("manager12", "WAREHOUSE_MANAGER", warehouse1.getId() + "," + warehouse2.getId());
    }

    private JsonNode atp(TestUser user, Long warehouseId) throws Exception {
        String url = ATP + "?productId=" + PRODUCT_1 + (warehouseId != null ? "&warehouseId=" + warehouseId : "");
        return data(getAs(url, COMPANY_A, user).andExpect(status().isOk()));
    }

    private static void assertTotals(JsonNode node, String onHand, String reserved, String available) {
        assertThat(node.path("onHandQuantity").decimalValue()).isEqualByComparingTo(onHand);
        assertThat(node.path("reservedQuantity").decimalValue()).isEqualByComparingTo(reserved);
        assertThat(node.path("atpQuantity").decimalValue()).isEqualByComparingTo(available);
    }

    @Test
    @DisplayName("Kho ngoài phạm vi trả 403 cho Quản lý kho, Nhân viên kho; kho được gán vẫn đọc được")
    void warehouseOutsideScopeIsForbidden() throws Exception {
        getAs(ATP + "?productId=" + PRODUCT_1 + "&warehouseId=" + warehouse2.getId(), COMPANY_A, manager1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
        getAs(ATP + "?productId=" + PRODUCT_1 + "&warehouseId=" + warehouse2.getId(), COMPANY_A, staff1)
                .andExpect(status().isForbidden());
        assertTotals(atp(manager1, warehouse1.getId()), "10", "2", "8");
        assertTotals(atp(manager2, warehouse2.getId()), "30", "5", "25");
    }

    @Test
    @DisplayName("Không truyền kho: người dùng giới hạn theo kho chỉ thấy tổng các kho được gán, số đệm không lẫn với ADMIN")
    void withoutWarehouseScopedUserSeesOnlyAssignedWarehouses() throws Exception {
        // ADMIN đọc trước để bộ nhớ đệm có số "mọi kho"
        assertTotals(atp(admin, null), "40", "7", "33");
        JsonNode scoped = atp(manager1, null);
        assertTotals(scoped, "10", "2", "8");
        assertThat(scoped.path("warehouseId").isNull()).isTrue();
        assertTotals(atp(staff1, null), "10", "2", "8");
        assertTotals(atp(manager2, null), "30", "5", "25");
        assertTotals(atp(managerOfBoth, null), "40", "7", "33");
        // Đọc lại sau người dùng giới hạn: ADMIN, ACCOUNTANT vẫn nhận tổng mọi kho
        assertTotals(atp(admin, null), "40", "7", "33");
        assertTotals(atp(accountant, null), "40", "7", "33");
        assertTotals(atp(accountant, warehouse2.getId()), "30", "5", "25");
    }

    @Test
    @DisplayName("SALES chưa được gán kho nhận 403 ở API người dùng như các API kho khác")
    void salesWithoutWarehouseIsForbiddenOnUserApi() throws Exception {
        getAs(ATP + "?productId=" + PRODUCT_1, COMPANY_A, salesWithoutWarehouse)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Batch ATP: kho ngoài phạm vi trả 403, không truyền kho thì chỉ cộng các kho được gán")
    void batchFollowsTheSameScope() throws Exception {
        postJson(BATCH, Map.of("warehouseId", warehouse2.getId(), "productIds", List.of(PRODUCT_1)), COMPANY_A, manager1)
                .andExpect(status().isForbidden());

        postJson(BATCH, Map.of("productIds", List.of(PRODUCT_2, PRODUCT_1)), COMPANY_A, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].onHandQuantity", is(7.0)))
                .andExpect(jsonPath("$.data.items[1].onHandQuantity", is(40.0)));

        JsonNode scoped = data(postJson(BATCH, Map.of("productIds", List.of(PRODUCT_2, PRODUCT_1)), COMPANY_A, manager1)
                .andExpect(status().isOk()));
        assertThat(scoped.path("warehouseId").isNull()).isTrue();
        assertThat(scoped.path("items")).hasSize(2);
        assertThat(scoped.path("items").get(0).path("productId").asLong()).isEqualTo(PRODUCT_2);
        assertTotals(scoped.path("items").get(0), "0", "0", "0");
        assertThat(scoped.path("items").get(1).path("productId").asLong()).isEqualTo(PRODUCT_1);
        assertTotals(scoped.path("items").get(1), "10", "2", "8");

        JsonNode both = data(postJson(BATCH, Map.of("productIds", List.of(PRODUCT_1, PRODUCT_2)), COMPANY_A, managerOfBoth)
                .andExpect(status().isOk()));
        assertTotals(both.path("items").get(0), "40", "7", "33");
        assertTotals(both.path("items").get(1), "7", "0", "7");

        postJson(BATCH, Map.of("productIds", List.of(PRODUCT_1)), COMPANY_A, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].atpQuantity", is(33.0)));
    }

    @Test
    @DisplayName("Endpoint nội bộ cho order-service không lọc theo kho: SALES chưa gán kho vẫn kiểm tra được ATP khi xác nhận đơn")
    void internalAtpIsNotScoped() throws Exception {
        JsonNode allWarehouses = data(getAs(INTERNAL_ATP + "?productId=" + PRODUCT_1, COMPANY_A, salesWithoutWarehouse)
                .andExpect(status().isOk()));
        assertTotals(allWarehouses, "40", "7", "33");
        JsonNode warehouse2Only = data(getAs(INTERNAL_ATP + "?productId=" + PRODUCT_1 + "&warehouseId=" + warehouse2.getId(),
                COMPANY_A, salesWithoutWarehouse).andExpect(status().isOk()));
        assertTotals(warehouse2Only, "30", "5", "25");
        assertThat(warehouse2Only.path("warehouseId").asLong()).isEqualTo(warehouse2.getId());
        // Gọi không có người dùng (chỉ doanh nghiệp) cũng được
        assertTotals(data(getAs(INTERNAL_ATP + "?productId=" + PRODUCT_1, COMPANY_A, null).andExpect(status().isOk())),
                "40", "7", "33");
    }
}
