package vn.wms.ai.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Dựng số liệu cho bản tóm tắt: không đưa thẻ bị che, giữ null kèm lời giải thích, câu giả lập dùng đúng số. */
class DashboardFactsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode kpi(boolean financial) throws Exception {
        return DashboardFactsTestData.kpi(financial);
    }

    private static JsonNode inOut(boolean complete) throws Exception {
        return DashboardFactsTestData.inOut(complete);
    }

    @Test
    void maskedFinancialCardsAreNotSentAsZeros() throws Exception {
        DashboardFacts facts = new DashboardFacts(kpi(false), inOut(true), null);

        ObjectNode json = facts.toJson(MAPPER);

        assertThat(facts.financialsVisible()).isFalse();
        assertThat(json.path("hiddenForRole").toString()).contains("Công nợ phải thu", "Công nợ phải trả",
                "Giá trị tồn kho", "Doanh số xuất bán tháng");
        assertThat(json.path("metrics")).hasSize(4);
        assertThat(json.path("metrics").toString()).doesNotContain("Công nợ", "Doanh số");
        assertThat(facts.mockBullets()).noneMatch(bullet -> bullet.contains("Công nợ phải thu "))
                .anyMatch(bullet -> bullet.contains("không hiển thị với vai trò của bạn"));
    }

    @Test
    void nullFiguresStayNullWithExplanation() throws Exception {
        DashboardFacts facts = new DashboardFacts(kpi(true), inOut(false), null);

        ObjectNode json = facts.toJson(MAPPER);

        JsonNode valuation = null;
        JsonNode revenue = null;
        for (JsonNode metric : json.path("metrics")) {
            if ("Giá trị tồn kho".equals(metric.path("name").asText())) {
                valuation = metric;
            }
            if ("Doanh số xuất bán tháng".equals(metric.path("name").asText())) {
                revenue = metric;
            }
        }
        assertThat(valuation).isNotNull();
        assertThat(valuation.path("value").isNull()).isTrue();
        assertThat(valuation.path("note").asText()).isEqualTo("Chưa tính được: tồn kho chưa có giá vốn");
        assertThat(revenue.path("value").decimalValue()).isEqualByComparingTo("45000000");
        assertThat(revenue.path("growthPercentVsLastMonth").isNull()).isTrue();
        JsonNode movement = json.path("inOutThisMonth");
        assertThat(movement.path("quantityIn").isNull()).isTrue();
        assertThat(movement.path("closingQuantity").decimalValue()).isEqualByComparingTo("189.75");
        assertThat(movement.path("note").asText()).contains("Sổ kho bắt đầu ghi");
        assertThat(json.path("hiddenForRole")).isEmpty();
    }

    @Test
    void missingInOutReportIsExplained() throws Exception {
        DashboardFacts facts = new DashboardFacts(kpi(false), null, "vai trò của người xem không được xem báo cáo");

        assertThat(facts.toJson(MAPPER).path("inOutThisMonth").path("available").asBoolean(true)).isFalse();
        assertThat(facts.mockBullets()).anyMatch(bullet -> bullet.contains("chưa có số liệu (vai trò"));
    }

    @Test
    void mockBulletsAndActionsUseTheRealNumbers() throws Exception {
        DashboardFacts facts = new DashboardFacts(kpi(true), inOut(true), null);

        assertThat(facts.mockBullets()).hasSize(5).containsExactly(
                "Đơn bán chờ xuất kho: 7 đơn, trong đó 2 đơn cần lấy hàng gấp. Đơn mua chờ tiếp nhận: 4 đơn, "
                        + "1 lô dự kiến đến hôm nay.",
                "Tồn kho khả dụng: 1.250,5 sản phẩm; tỷ lệ lấp đầy kho 91,5% (183/200 vị trí).",
                "Từ đầu tháng: nhập 500, xuất 320,25, tồn cuối 189,75 (đơn vị gốc của từng mặt hàng).",
                "Công nợ phải thu 12.500.000 đ (3 khách hàng có dư nợ đến hạn); công nợ phải trả 8.000.000 đ "
                        + "(2 nhà cung cấp đến hạn trong tuần).",
                "Doanh số xuất bán tháng 45.000.000 đ, chưa tính được tăng trưởng: tháng trước không có doanh số. "
                        + "Giá trị tồn kho: chưa tính được: tồn kho chưa có giá vốn.");
        assertThat(facts.mockActions()).containsExactly("Ưu tiên điều phối lấy hàng cho 2 đơn bán gấp.",
                "Chuẩn bị vị trí và nhân sự nhận 1 lô hàng dự kiến đến hôm nay.");
    }
}
