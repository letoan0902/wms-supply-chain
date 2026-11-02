package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.product.dto.CategoryResponse;
import vn.wms.product.dto.CreateCategoryRequest;
import vn.wms.product.service.CategoryService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping({"/api/products/categories", "/api/categories"})
@Tag(name = "Categories", description = "Quản lý danh mục ngành hàng / nhóm hàng hóa")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các danh mục ngành hàng")
    public ApiResponse<List<CategoryResponse>> listAll() {
        return ApiResponse.ok(categoryService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin danh mục theo ID")
    public ApiResponse<CategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới danh mục ngành hàng")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/products/categories/" + created.id()))
                .body(ApiResponse.ok(created));
    }
}
