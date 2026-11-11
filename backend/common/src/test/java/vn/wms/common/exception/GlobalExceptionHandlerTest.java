package vn.wms.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitViolation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kiểm tra GlobalExceptionHandler bằng MockMvc standalone (không cần khởi động Spring context). */
class GlobalExceptionHandlerTest {

    private static final String EMPTY_NAME_JSON = """
            {"name": ""}
            """;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void apiExceptionIsMappedToItsHttpStatusAndErrorCode() throws Exception {
        mockMvc.perform(get("/demo/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm 42"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void apiExceptionWithDataReturnsItInDataField() throws Exception {
        mockMvc.perform(get("/demo/limit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Gói Khởi Đầu chỉ cho phép tối đa 1 kho."))
                .andExpect(jsonPath("$.data.limit").value("WAREHOUSES"))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.max").value(1))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    void bodyValidationErrorsAreReportedPerField() throws Exception {
        mockMvc.perform(post("/demo/validate").contentType(MediaType.APPLICATION_JSON).content(EMPTY_NAME_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.name").exists());
    }

    @Test
    void unsupportedMethodIsWrappedInApiResponse() throws Exception {
        mockMvc.perform(post("/demo/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void missingRequestParameterIsReportedInVietnamese() throws Exception {
        mockMvc.perform(get("/demo/need-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Thiếu tham số bắt buộc: id"));
    }

    @Test
    void wrongParameterTypeIsReportedInVietnamese() throws Exception {
        mockMvc.perform(get("/demo/need-param").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Giá trị của tham số id không hợp lệ"));
    }

    @Test
    void malformedJsonBodyIsReportedInVietnamese() throws Exception {
        mockMvc.perform(post("/demo/validate").contentType(MediaType.APPLICATION_JSON).content("{ khong phai json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nội dung yêu cầu không đúng định dạng"));
    }

    @Test
    void unexpectedExceptionIsHiddenBehindGenericMessage() throws Exception {
        mockMvc.perform(get("/demo/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống, vui lòng thử lại sau"));
    }

    @RestController
    @RequestMapping("/demo")
    static class DemoController {

        @GetMapping("/not-found")
        String notFound() {
            throw ApiException.notFound("Không tìm thấy sản phẩm 42");
        }

        @GetMapping("/limit")
        String limit() {
            throw ApiException.withData(ErrorCode.PLAN_LIMIT_EXCEEDED, "Gói Khởi Đầu chỉ cho phép tối đa 1 kho.",
                    new PlanLimitViolation(PlanLimit.WAREHOUSES, 1, 1, "FREE"));
        }

        @GetMapping("/need-param")
        String needParam(@RequestParam("id") long id) {
            return String.valueOf(id);
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("chi tiết nội bộ không được lộ ra ngoài");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody DemoBody body) {
            return body.name();
        }
    }

    record DemoBody(@NotBlank(message = "Tên không được để trống") String name) {
    }
}
