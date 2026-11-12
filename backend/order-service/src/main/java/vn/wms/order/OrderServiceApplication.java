package vn.wms.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import vn.wms.order.client.ProductClient;

/**
 * Order Service (cổng 8084): nhà cung cấp, khách hàng, đơn mua hàng (PO/GRN) và đơn bán hàng (SO/GDN).
 */
@SpringBootApplication
@EnableFeignClients(basePackageClasses = ProductClient.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
