package vn.wms.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.wms.inventory.client.ProductClient;

/**
 * Inventory Service (cổng 8083): kho, vị trí (zone/rack/bin), tồn kho, điều chuyển và kiểm kê.
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackageClasses = ProductClient.class)
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
