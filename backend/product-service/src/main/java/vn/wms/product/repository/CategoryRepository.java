package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);

    /** Tìm danh mục theo tên, không phân biệt hoa thường (nhập hàng loạt dùng tên danh mục). */
    Optional<Category> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    /** Tìm danh mục theo mã, không phân biệt hoa thường. */
    Optional<Category> findFirstByCodeIgnoreCaseOrderByIdAsc(String code);
}
