package vn.wms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway (Spring Cloud Gateway): điểm vào duy nhất cho frontend.
 * Định tuyến /api/** tới các service qua Eureka (lb://ten-service), xử lý CORS và (tùy chọn) kiểm tra JWT.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
