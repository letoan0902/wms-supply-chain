package vn.wms.inventory.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình của gói cảnh báo tồn kho (WMS-15.1): nạp {@link StockAlertProperties} và đăng ký Feign client riêng của gói
 * (lớp ứng dụng chỉ quét gói {@code vn.wms.inventory.client}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockAlertProperties.class)
@EnableFeignClients(clients = AlertProductClient.class)
public class StockAlertConfig {
}
