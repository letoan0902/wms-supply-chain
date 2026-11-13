package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Định tuyến câu hỏi của trợ lý giả lập: số chứng từ, SKU, kho, mục hướng dẫn; số liệu lấy đúng từ tra cứu. */
class MockAssistantTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AssistantLookups lookups = mock(AssistantLookups.class);
    private final MockAssistant assistant = new MockAssistant(lookups, new UserGuide());

    private static AssistantLookups.Lookup ok(String json) throws Exception {
        return AssistantLookups.Lookup.of((ObjectNode) MAPPER.readTree(json));
    }

    @Test
    void skuQuestionRunsStockLookupAndQuotesRealNumbers() throws Exception {
        when(lookups.getStock("SP-001", null)).thenReturn(ok("""
                {"sku":"SP-001","name":"Nước suối","uom":"Thùng","scope":"Mọi kho của doanh nghiệp",
                 "onHandQuantity":1250.5,"reservedQuantity":20,"availableQuantity":1230.5}"""));

        MockAssistant.Answer answer = assistant.answer("Tồn của SP-001 còn bao nhiêu?");

        assertThat(answer.lookup()).isEqualTo(AssistantTools.GET_STOCK);
        assertThat(answer.text()).startsWith(MockAssistant.MOCK_NOTICE)
                .contains("Mã SKU SP-001 (Nước suối) trên mọi kho của doanh nghiệp: tồn thực tế 1.250,5 Thùng, "
                        + "đang giữ cho đơn bán 20 Thùng, còn khả dụng 1.230,5 Thùng.");
    }

    @Test
    void skuWithWarehouseCodeLooksUpStockInThatWarehouse() throws Exception {
        when(lookups.getStock("abc123", "WH01")).thenReturn(ok("""
                {"sku":"ABC123","name":"Gạo","uom":"Bao","warehouse":{"id":3,"code":"WH01","name":"Kho Hà Nội"},
                 "onHandQuantity":5,"reservedQuantity":0,"availableQuantity":5}"""));

        MockAssistant.Answer answer = assistant.answer("sku abc123 trong kho WH01 còn mấy bao?");

        assertThat(answer.lookup()).isEqualTo(AssistantTools.GET_STOCK);
        assertThat(answer.text()).contains("tại kho WH01 - Kho Hà Nội: tồn thực tế 5 Bao");
    }

    @Test
    void unknownSkuIsReportedAsNotFoundWithoutInventingNumbers() {
        when(lookups.getStock("XYZ-999", null)).thenReturn(AssistantLookups.Lookup.failed(
                ServiceDataClient.Status.NOT_FOUND,
                "Không tìm thấy hàng hóa có mã SKU XYZ-999 trong dữ liệu của doanh nghiệp bạn."));

        MockAssistant.Answer answer = assistant.answer("Tồn XYZ-999?");

        assertThat(answer.text()).endsWith("Không tìm thấy hàng hóa có mã SKU XYZ-999 trong dữ liệu của doanh nghiệp bạn.");
    }

    @Test
    void purchaseAndSalesOrderNumbersRunOrderLookups() throws Exception {
        when(lookups.getPurchaseOrder("PO-20261110-0001")).thenReturn(ok("""
                {"poNumber":"PO-20261110-0001","statusLabel":"Chờ duyệt","supplierName":"NCC A",
                 "warehouseName":"Kho Hà Nội","orderDate":"2026-11-10","expectedDate":null,"items":[{},{}]}"""));
        when(lookups.getSalesOrder("so-20261111-0002")).thenReturn(AssistantLookups.Lookup.failed(
                ServiceDataClient.Status.FORBIDDEN, ServiceDataClient.FORBIDDEN_MESSAGE));

        MockAssistant.Answer po = assistant.answer("Đơn PO-20261110-0001 tới đâu rồi?");
        MockAssistant.Answer so = assistant.answer("trạng thái so-20261111-0002");

        assertThat(po.lookup()).isEqualTo(AssistantTools.GET_PURCHASE_ORDER);
        assertThat(po.text()).contains("Đơn mua PO-20261110-0001: trạng thái Chờ duyệt; nhà cung cấp NCC A; "
                + "kho nhận Kho Hà Nội; ngày lập 2026-11-10; ngày nhận dự kiến chưa có; 2 dòng hàng.");
        assertThat(so.lookup()).isEqualTo(AssistantTools.GET_SALES_ORDER);
        assertThat(so.text()).endsWith("Bạn không có quyền xem dữ liệu này.");
    }

    @Test
    void warehouseQuestionRunsWarehouseStockLookup() throws Exception {
        when(lookups.getWarehouseStock("WH01", null)).thenReturn(ok("""
                {"warehouse":{"id":3,"code":"WH01","name":"Kho Hà Nội"},"date":"2026-11-12","productCount":2,
                 "totalQuantity":30,"items":[{"sku":"A1","name":"Hàng A","uom":"Cái","quantity":20},
                 {"sku":"B2","name":"Hàng B","uom":null,"quantity":10}]}"""));

        MockAssistant.Answer answer = assistant.answer("tồn kho WH01");

        assertThat(answer.lookup()).isEqualTo(AssistantTools.GET_WAREHOUSE_STOCK);
        assertThat(answer.text()).contains("Kho WH01 - Kho Hà Nội (tồn cuối ngày 2026-11-12): 2 mặt hàng, "
                + "tổng số lượng 30.").contains("- A1 (Hàng A): 20 Cái").contains("- B2 (Hàng B): 10");
    }

    @Test
    void stockQuestionWithoutSkuOrWarehouseListsWarehouses() throws Exception {
        when(lookups.listWarehouses()).thenReturn(ok("""
                {"count":1,"warehouses":[{"id":3,"code":"WH01","name":"Kho Hà Nội","active":true}]}"""));

        MockAssistant.Answer answer = assistant.answer("còn tồn bao nhiêu?");

        assertThat(answer.lookup()).isEqualTo(AssistantTools.LIST_WAREHOUSES);
        assertThat(answer.text()).contains("WH01 - Kho Hà Nội");
    }

    @Test
    void howToQuestionReturnsGuideSectionWithoutLookups() {
        MockAssistant.Answer answer = assistant.answer("Tạo kho thế nào?");

        assertThat(answer.lookup()).isNull();
        assertThat(answer.text()).startsWith(MockAssistant.MOCK_NOTICE)
                .contains("mục \"Tạo kho và vị trí lưu trữ\"").contains("Thêm kho mới");
        verifyNoInteractions(lookups);
    }

    @Test
    void unmatchedQuestionListsWhatTheAssistantCanDo() {
        MockAssistant.Answer answer = assistant.answer("zzzz qqqq");

        assertThat(answer.text()).contains("Chưa tìm thấy mục hướng dẫn phù hợp").contains("PO-");
        verifyNoInteractions(lookups);
    }

    @Test
    void plainWordsAreNotTakenAsSku() {
        assertThat(MockAssistant.skuCandidate("sku là gì?", null)).isNull();
        assertThat(MockAssistant.skuCandidate("Ngày 11/11 năm 2026 có gì?", null)).isNull();
        assertThat(MockAssistant.warehouseReference("tạo kho hàng mới")).isNull();
        assertThat(MockAssistant.warehouseReference("tồn kho 3")).isEqualTo("3");
    }

    @Test
    void numbersAreFormattedVietnameseStyleAndNullIsUnknown() throws Exception {
        assertThat(MockAssistant.number(MAPPER.readTree("1234567.125"))).isEqualTo("1.234.567,125");
        assertThat(MockAssistant.number(MAPPER.readTree("null"))).isEqualTo("không xác định");
        verify(lookups, org.mockito.Mockito.never()).getStock(anyString(), isNull());
        verify(lookups, org.mockito.Mockito.never()).getWarehouseStock(any(), any());
    }
}
