package vn.wms.auth.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình liên kết dùng một lần gửi qua email (tiền tố {@code app.account-email}, WMS-23.6).
 *
 * @param async            {@code true} (mặc định): yêu cầu gửi lại kích hoạt và quên mật khẩu được xử lý ở luồng nền,
 *                         API trả lời ngay với cùng nội dung và cùng thời gian dù email có tồn tại hay không. Test đặt
 *                         {@code false} để xử lý ngay trên luồng gọi.
 * @param activationTtl    hiệu lực của liên kết kích hoạt doanh nghiệp, mặc định 24 giờ
 * @param passwordResetTtl hiệu lực của liên kết đặt lại mật khẩu, mặc định 30 phút
 * @param resendInterval   khoảng cách tối thiểu giữa hai lần gửi cho cùng một email, mặc định 1 phút
 */
@ConfigurationProperties(prefix = "app.account-email")
public record AccountEmailProperties(
        @DefaultValue("true") boolean async,
        @DefaultValue("24h") Duration activationTtl,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue("1m") Duration resendInterval) {
}
