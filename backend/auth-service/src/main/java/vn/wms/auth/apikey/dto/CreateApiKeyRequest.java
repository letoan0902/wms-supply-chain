package vn.wms.auth.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/auth/api-keys}.
 *
 * @param name tên gợi nhớ của key (hệ thống ngoài nào dùng), 2 đến 100 ký tự
 */
public record CreateApiKeyRequest(
        @NotBlank(message = "Tên API key không được để trống")
        @Size(min = 2, max = 100, message = "Tên API key phải từ 2 đến 100 ký tự")
        String name) {
}
