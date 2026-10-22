package vn.wms.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okWrapsDataAndMarksSuccess() {
        ApiResponse<String> response = ApiResponse.ok("du-lieu");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo(ApiResponse.OK_MESSAGE);
        assertThat(response.data()).isEqualTo("du-lieu");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void okWithoutDataHasNoPayload() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
    }

    @Test
    void errorCarriesErrorCodeNameAndMessage() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.NOT_FOUND, "Không tìm thấy kho");

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("NOT_FOUND");
        assertThat(response.message()).isEqualTo("Không tìm thấy kho");
        assertThat(response.data()).isNull();
    }

    @Test
    void errorCodeFromHttpStatusFallsBackToGenericCodes() {
        assertThat(ErrorCode.fromHttpStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.fromHttpStatus(400)).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromHttpStatus(418)).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromHttpStatus(503)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
