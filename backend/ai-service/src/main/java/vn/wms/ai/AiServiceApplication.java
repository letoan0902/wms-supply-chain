package vn.wms.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI Service (cổng 8086): gọi Claude qua SDK chính thức của Anthropic, kiểm tra và đếm lượt AI theo gói của doanh
 * nghiệp (docs/SAAS_SPEC.md, mục WMS-24.1). Không có khóa AI thì chạy chế độ giả lập để nhóm phát triển giao diện.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
