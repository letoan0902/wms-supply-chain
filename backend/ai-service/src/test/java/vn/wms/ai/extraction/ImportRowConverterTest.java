package vn.wms.ai.extraction;

import org.junit.jupiter.api.Test;
import vn.wms.ai.dto.ImportRequest;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đổi dòng người dùng đã sửa thành items của API nhập hàng loạt. */
class ImportRowConverterTest {

    private static ImportRequest.Row row(Map<String, Object> values) {
        return new ImportRequest.Row(1, values);
    }

    @Test
    void keepsOnlyKnownFieldsDropsBlanksAndConvertsTypes() {
        Map<String, Object> values = new HashMap<>();
        values.put("code", " KH01 ");
        values.put("name", "Công ty An");
        values.put("customerType", "Bán lẻ");
        values.put("phone", 901234567);
        values.put("creditLimit", "50.000.000");
        values.put("initialDebt", 1.5);
        values.put("email", "");
        values.put("notes", null);
        values.put("companyId", 99);

        List<Map<String, Object>> items = ImportRowConverter.toItems(ExtractionDataType.CUSTOMER, List.of(row(values)));

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsOnlyKeys("code", "name", "customerType", "phone", "creditLimit",
                "initialDebt");
        assertThat(items.get(0)).containsEntry("code", "KH01").containsEntry("customerType", "RETAIL")
                .containsEntry("phone", "901234567")
                .containsEntry("creditLimit", new BigDecimal("50000000"))
                .containsEntry("initialDebt", new BigDecimal("1.5"));
    }

    @Test
    void invalidNumberOrEnumRejectsWholeImportWithCellErrors() {
        Map<String, Object> first = new HashMap<>(Map.of("sku", "SP1", "purchasePrice", "liên hệ"));
        Map<String, Object> second = new HashMap<>(Map.of("sku", "SP2", "vatRate", List.of(1)));

        assertThatThrownBy(() -> ImportRowConverter.toItems(ExtractionDataType.PRODUCT, List.of(row(first),
                row(second))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getData()).isEqualTo(Map.of(
                            "rows[0].values.purchasePrice", "Giá nhập phải là số",
                            "rows[1].values.vatRate", "Thuế suất VAT (%) phải là số"));
                });

        Map<String, Object> customer = new HashMap<>(Map.of("code", "KH1", "customerType", "VIP"));
        assertThatThrownBy(() -> ImportRowConverter.toItems(ExtractionDataType.CUSTOMER, List.of(row(customer))))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getData()).isEqualTo(Map.of(
                        "rows[0].values.customerType", "Loại khách hàng phải là một trong: WHOLESALE, RETAIL")));
    }
}
