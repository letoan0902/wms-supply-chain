package vn.wms.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.product.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "  AND (:active IS NULL OR p.active = :active)")
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         @Param("active") Boolean active,
                         Pageable pageable);
}
