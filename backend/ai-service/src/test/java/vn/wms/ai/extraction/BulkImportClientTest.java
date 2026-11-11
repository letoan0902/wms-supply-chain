package vn.wms.ai.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.ai.config.ServiceClientConfig;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Lời gọi API nhập hàng loạt của WMS-22.4b (chưa có trên nhánh này): đúng service, đúng đường dẫn, body
 * {@code { "items": [...] }}, chuyển tiếp danh tính người gọi và trả nguyên kết quả. Không gọi mạng
 * (MockRestServiceServer gắn vào builder có interceptor như bean thật, chỉ thiếu cân bằng tải Eureka).
 */
class BulkImportClientTest {

    private static final String RESULT = """
            {"success":true,"code":"OK","message":"Thành công","timestamp":"2026-11-12T03:00:00Z",
             "data":{"total":3,"created":1,"skipped":1,"failed":1,"results":[
               {"row":1,"status":"CREATED","id":501,"code":"SP001","message":null},
               {"row":2,"status":"SKIPPED","id":77,"code":"SP002","message":"Mã SKU đã tồn tại"},
               {"row":3,"status":"FAILED","id":null,"code":"SP003","message":"Tên sản phẩm không được để trống"}]}}
            """;

    private MockRestServiceServer server;
    private BulkImportClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = new ServiceClientConfig().loadBalancedRestClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BulkImportClient(builder, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "mua_hang_01");
        request.addHeader(UserContext.HEADER_USER_ROLES, "PURCHASING");
        request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "3,4");
        request.addHeader(ServiceClientConfig.HEADER_PLAN, "TRIAL");
        // Header doanh nghiệp do client tự đặt không được dùng: luôn lấy từ TenantContext
        request.addHeader(TenantContext.HEADER_COMPANY_ID, "999");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCompanyId(7L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    @Test
    void productsArePostedToProductServiceWithCallerIdentityAndResultIsReturnedUnchanged() {
        server.expect(requestTo("http://product-service/api/products/bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TenantContext.HEADER_COMPANY_ID, "7"))
                .andExpect(header(UserContext.HEADER_USER_NAME, "mua_hang_01"))
                .andExpect(header(UserContext.HEADER_USER_ROLES, "PURCHASING"))
                .andExpect(header(UserContext.HEADER_USER_WAREHOUSE_IDS, "3,4"))
                .andExpect(header(ServiceClientConfig.HEADER_PLAN, "TRIAL"))
                .andExpect(content().json("""
                        {"items":[{"sku":"SP001","name":"Nước","uomName":"Chai","sellingPrice":5000}]}""", true))
                .andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));

        BulkImportResult result = client.importItems(ExtractionDataType.PRODUCT, List.of(
                Map.of("sku", "SP001", "name", "Nước", "uomName", "Chai", "sellingPrice", new BigDecimal("5000"))));

        server.verify();
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).containsExactly(
                new BulkImportResult.RowResult(1, "CREATED", 501L, "SP001", null),
                new BulkImportResult.RowResult(2, "SKIPPED", 77L, "SP002", "Mã SKU đã tồn tại"),
                new BulkImportResult.RowResult(3, "FAILED", null, "SP003", "Tên sản phẩm không được để trống"));
    }

    @Test
    void suppliersAndCustomersGoToOrderService() {
        server.expect(requestTo("http://order-service/api/orders/suppliers/bulk"))
                .andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://order-service/api/orders/customers/bulk"))
                .andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));

        client.importItems(ExtractionDataType.SUPPLIER, List.of(Map.of("code", "NCC1")));
        client.importItems(ExtractionDataType.CUSTOMER, List.of(Map.of("code", "KH1")));

        server.verify();
    }

    @Test
    void businessErrorOfDownstreamIsPassedThroughWithCodeAndData() {
        server.expect(requestTo("http://product-service/api/products/bulk"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"success":false,"code":"SUBSCRIPTION_EXPIRED","message":"Gói dịch vụ đã hết hạn",
                         "data":{"plan":"TRIAL"},"timestamp":"2026-11-12T03:00:00Z"}"""));

        assertThatThrownBy(() -> client.importItems(ExtractionDataType.PRODUCT, List.of(Map.of("sku", "A"))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_EXPIRED);
                    assertThat(ex.getMessage()).isEqualTo("Gói dịch vụ đã hết hạn");
                    assertThat(ex.getData().toString()).contains("TRIAL");
                });
    }

    @Test
    void serverErrorOrMissingEndpointIsServiceUnavailable() {
        server.expect(requestTo("http://order-service/api/orders/customers/bulk")).andRespond(withServerError());
        assertThatThrownBy(() -> client.importItems(ExtractionDataType.CUSTOMER, List.of(Map.of("code", "K"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));

        server.reset();
        server.expect(requestTo("http://order-service/api/orders/suppliers/bulk"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.importItems(ExtractionDataType.SUPPLIER, List.of(Map.of("code", "N"))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("order-service");
                });
    }
}
