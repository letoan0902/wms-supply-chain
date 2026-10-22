package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.inventory.dto.WarehouseResponse;
import vn.wms.inventory.repository.WarehouseRepository;

import java.util.List;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /** Danh sách toàn bộ kho, sắp theo mã kho. Số lượng kho nhỏ nên không phân trang. */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses() {
        return warehouseRepository.findAllByOrderByCodeAsc().stream().map(WarehouseResponse::from).toList();
    }
}
