package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);
}
