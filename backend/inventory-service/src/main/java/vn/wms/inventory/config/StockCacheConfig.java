package vn.wms.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import vn.wms.inventory.cache.StockCacheProperties;

/** Đăng ký cấu hình {@code app.stock-cache.*} của bộ nhớ đệm tồn kho (WMS-19.1). */
@Configuration
@EnableConfigurationProperties(StockCacheProperties.class)
public class StockCacheConfig {
}
