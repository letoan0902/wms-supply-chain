package vn.wms.product;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product Service (cổng 8082): danh mục sản phẩm - SKU, danh mục, đơn vị tính, barcode.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "WMS Product Service API", version = "v1",
        description = "SKU, danh mục, đơn vị tính và barcode"))
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
