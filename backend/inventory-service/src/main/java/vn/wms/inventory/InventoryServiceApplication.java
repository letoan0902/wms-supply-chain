package vn.wms.inventory;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import vn.wms.inventory.client.ProductClient;

/**
 * Inventory Service (cổng 8083): kho, vị trí (zone/rack/bin), tồn kho, điều chuyển và kiểm kê.
 */
@SpringBootApplication
@EnableFeignClients(basePackageClasses = ProductClient.class)
@OpenAPIDefinition(info = @Info(title = "WMS Inventory Service API", version = "v1",
        description = "Kho, vị trí lưu trữ, tồn kho, điều chuyển và kiểm kê"))
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
