package vn.wms.common.tenant.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/api/sample-items")
public class SampleItemController {

    private final SampleItemRepository repository;

    public SampleItemController(SampleItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<String>> list() {
        return ApiResponse.ok(repository.findAll().stream().map(SampleItem::getCode).sorted().toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<String> get(@PathVariable Long id) {
        return ApiResponse.ok(repository.findById(id)
                .map(SampleItem::getCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi " + id)));
    }

    @GetMapping("/current-company")
    public ApiResponse<Long> currentCompany() {
        return ApiResponse.ok(TenantContext.getCompanyId());
    }
}
