package vn.wms.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.product.dto.CategoryResponse;
import vn.wms.product.dto.CreateCategoryRequest;
import vn.wms.product.entity.Category;
import vn.wms.product.repository.CategoryRepository;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục với id: " + id));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (categoryRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã danh mục '" + cleanCode + "' đã tồn tại");
        }

        Category category = new Category();
        category.setCode(cleanCode);
        category.setName(request.name().trim());
        category.setDescription(request.description() != null ? request.description().trim() : null);

        return CategoryResponse.from(categoryRepository.save(category));
    }
}
