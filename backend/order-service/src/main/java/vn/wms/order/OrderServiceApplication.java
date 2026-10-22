package vn.wms.order;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import vn.wms.order.client.ProductClient;

/**
 * Order Service (cổng 8084): nhà cung cấp, khách hàng, đơn mua hàng (PO/GRN) và đơn bán hàng (SO/GDN).
 */
@SpringBootApplication
@EnableFeignClients(basePackageClasses = ProductClient.class)
@OpenAPIDefinition(info = @Info(title = "WMS Order Service API", version = "v1",
        description = "Nhà cung cấp, khách hàng, đơn mua (PO/GRN) và đơn bán (SO/GDN)"))
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
