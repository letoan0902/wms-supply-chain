package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    boolean existsBySku(String sku);
}
