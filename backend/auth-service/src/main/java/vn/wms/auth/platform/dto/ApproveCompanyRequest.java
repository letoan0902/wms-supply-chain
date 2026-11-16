package vn.wms.auth.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Thân (tùy chọn) của {@code POST /api/auth/platform/companies/{id}/approve}. Không gửi thân hoặc để trống mọi trường
 * thì cấp gói dùng thử 30 ngày.
 *
 * @param plan      {@code TRIAL} (mặc định) hoặc {@code PRO}
 * @param cycle     chu kỳ của gói PRO: {@code MONTHLY} (30 ngày) hoặc {@code YEARLY} (365 ngày); bắt buộc với PRO,
 *                  không dùng với TRIAL
 * @param trialDays số ngày dùng thử của gói TRIAL, từ 1 đến 90, mặc định 30; không dùng với PRO
 */
public record ApproveCompanyRequest(
        String plan,
        String cycle,
        @Min(value = 1, message = "Số ngày dùng thử từ 1 đến 90")
        @Max(value = 90, message = "Số ngày dùng thử từ 1 đến 90")
        Integer trialDays) {
}
