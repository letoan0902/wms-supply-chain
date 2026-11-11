package vn.wms.product.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.bulk.BulkRowResult;
import vn.wms.product.dto.BulkProductItem;
import vn.wms.product.dto.CategoryResponse;
import vn.wms.product.dto.CreateCategoryRequest;
import vn.wms.product.dto.CreateUomRequest;
import vn.wms.product.dto.ProductResponse;
import vn.wms.product.dto.UomResponse;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import java.util.Optional;

/**
 * Lưu một dòng của API nhập hàng hóa hàng loạt trong transaction riêng ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>Tách thành bean riêng thay vì một phương thức của {@link ProductBulkService} vì Spring chỉ áp
 * {@code @Transactional} khi gọi qua proxy: gọi nội bộ trong cùng lớp (self-invocation) sẽ không mở transaction mới
 * và một dòng lỗi có thể làm hỏng cả lần nhập. Dòng lỗi ném ngoại lệ: transaction của dòng đó rollback (kể cả danh
 * mục, đơn vị tính vừa tự tạo cho dòng), các dòng đã lưu trước đó không bị ảnh hưởng.
 */
@Component
public class ProductBulkRowImporter {

    /** Độ dài cột mã của bảng categories và uoms. */
    private static final int CATEGORY_CODE_LENGTH = 50;
    private static final int UOM_CODE_LENGTH = 20;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final UomService uomService;

    public ProductBulkRowImporter(ProductRepository productRepository,
                                  CategoryRepository categoryRepository,
                                  UomRepository uomRepository,
                                  ProductService productService,
                                  CategoryService categoryService,
                                  UomService uomService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.productService = productService;
        this.categoryService = categoryService;
        this.uomService = uomService;
    }

    /**
     * Tạo sản phẩm của một dòng đã qua kiểm tra dữ liệu, hoặc bỏ qua nếu SKU đã có trong doanh nghiệp.
     *
     * @param row  thứ tự dòng, từ 1
     * @param item dòng nhập
     * @param sku  SKU đã chuẩn hóa (cắt khoảng trắng, viết hoa) như {@link ProductService#create}
     * @return {@code CREATED} hoặc {@code SKIPPED}; lỗi nghiệp vụ được ném ra dưới dạng {@code ApiException}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkRowResult importRow(int row, BulkProductItem item, String sku) {
        Optional<Product> existing = productRepository.findBySku(sku);
        if (existing.isPresent()) {
            return BulkRowResult.skipped(row, existing.get().getId(), sku,
                    "Mã SKU '" + sku + "' đã tồn tại, bỏ qua không ghi đè");
        }

        Long categoryId = isBlank(item.categoryName()) ? null : resolveCategoryId(item.categoryName().trim());
        Long uomId = resolveUomId(item.uomName().trim());

        // Dùng lại đúng nghiệp vụ tạo một sản phẩm: kiểm tra trùng SKU, mã vạch, giới hạn gói, ghi mức sử dụng
        ProductResponse created = productService.create(item.toCreateRequest(categoryId, uomId));
        return BulkRowResult.created(row, created.id(), created.sku());
    }

    /**
     * Danh mục của doanh nghiệp có tên (hoặc mã) trùng, không phân biệt hoa thường; chưa có thì tạo mới. Danh mục
     * tạo ở dòng trước đã được commit nên các dòng sau trong cùng file tìm thấy và dùng lại.
     */
    private Long resolveCategoryId(String name) {
        Optional<Category> found = categoryRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .or(() -> categoryRepository.findFirstByCodeIgnoreCaseOrderByIdAsc(name));
        if (found.isPresent()) {
            return found.get().getId();
        }
        String code = CatalogCodeGenerator.uniqueCode(name, CATEGORY_CODE_LENGTH, "DM",
                categoryRepository::existsByCode);
        CategoryResponse created = categoryService.create(new CreateCategoryRequest(code, name, null));
        return created.id();
    }

    /** Đơn vị tính của doanh nghiệp có tên (hoặc mã) trùng, không phân biệt hoa thường; chưa có thì tạo mới. */
    private Long resolveUomId(String name) {
        Optional<Uom> found = uomRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .or(() -> uomRepository.findFirstByCodeIgnoreCaseOrderByIdAsc(name));
        if (found.isPresent()) {
            return found.get().getId();
        }
        String code = CatalogCodeGenerator.uniqueCode(name, UOM_CODE_LENGTH, "DVT", uomRepository::existsByCode);
        UomResponse created = uomService.create(new CreateUomRequest(code, name));
        return created.id();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
