package vn.wms.common.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkResultTest {

    @Test
    void ofCountsEachStatusAndSortsByRow() {
        BulkResult result = BulkResult.of(List.of(
                BulkRowResult.failed(3, "SKU-3", "Tên sản phẩm không được để trống"),
                BulkRowResult.created(1, 10L, "SKU-1"),
                BulkRowResult.skipped(2, 7L, "SKU-2", "Mã SKU 'SKU-2' đã tồn tại"),
                BulkRowResult.created(4, 11L, "SKU-4")));

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).extracting(BulkRowResult::row).containsExactly(1, 2, 3, 4);
    }

    @Test
    void rowFactoriesSetStatusIdAndMessage() {
        assertThat(BulkRowResult.created(1, 5L, "A"))
                .isEqualTo(new BulkRowResult(1, BulkRowStatus.CREATED, 5L, "A", null));
        assertThat(BulkRowResult.skipped(2, 6L, "B", "đã có"))
                .isEqualTo(new BulkRowResult(2, BulkRowStatus.SKIPPED, 6L, "B", "đã có"));
        assertThat(BulkRowResult.failed(3, null, "lỗi"))
                .isEqualTo(new BulkRowResult(3, BulkRowStatus.FAILED, null, null, "lỗi"));
    }

    @Test
    void serializesWithFieldNamesOfTheSpec() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                BulkResult.of(List.of(BulkRowResult.created(1, 9L, "SKU-1")))));

        assertThat(json.path("total").asInt()).isEqualTo(1);
        assertThat(json.path("created").asInt()).isEqualTo(1);
        assertThat(json.path("skipped").asInt()).isZero();
        assertThat(json.path("failed").asInt()).isZero();
        JsonNode row = json.path("results").get(0);
        assertThat(row.path("row").asInt()).isEqualTo(1);
        assertThat(row.path("status").asText()).isEqualTo("CREATED");
        assertThat(row.path("id").asLong()).isEqualTo(9L);
        assertThat(row.path("code").asText()).isEqualTo("SKU-1");
        assertThat(row.has("message")).isTrue();
    }

    @Test
    void requireItemsAcceptsOneToFiveHundredRows() {
        assertThat(BulkRequest.requireItems(new BulkRequest<>(List.of("a")))).containsExactly("a");
        List<String> max = Collections.nCopies(BulkRequest.MAX_ITEMS, "x");
        assertThat(BulkRequest.requireItems(new BulkRequest<>(max))).hasSize(500);
    }

    @Test
    void requireItemsRejectsMissingEmptyOrOversizedBody() {
        List<String> tooMany = new ArrayList<>(Collections.nCopies(BulkRequest.MAX_ITEMS + 1, "x"));

        for (BulkRequest<String> request : List.of(new BulkRequest<String>(null), new BulkRequest<>(List.<String>of()),
                new BulkRequest<>(tooMany))) {
            assertThatThrownBy(() -> BulkRequest.requireItems(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
        assertThatThrownBy(() -> BulkRequest.requireItems(null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
