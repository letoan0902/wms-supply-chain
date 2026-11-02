package vn.wms.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.product.dto.CreateUomRequest;
import vn.wms.product.dto.UomResponse;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.UomRepository;

import java.util.List;

@Service
public class UomService {

    private final UomRepository uomRepository;

    public UomService(UomRepository uomRepository) {
        this.uomRepository = uomRepository;
    }

    @Transactional(readOnly = true)
    public List<UomResponse> listAll() {
        return uomRepository.findAll().stream()
                .map(UomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UomResponse getById(Long id) {
        Uom uom = uomRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn vị tính với id: " + id));
        return UomResponse.from(uom);
    }

    @Transactional
    public UomResponse create(CreateUomRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (uomRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã đơn vị tính '" + cleanCode + "' đã tồn tại");
        }

        Uom uom = new Uom();
        uom.setCode(cleanCode);
        uom.setName(request.name().trim());
        return UomResponse.from(uomRepository.save(uom));
    }
}
