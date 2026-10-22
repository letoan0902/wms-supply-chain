package vn.wms.order.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.repository.PurchaseOrderRepository;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    /** Danh sách đơn mua có phân trang. Cần transaction vì supplier được nạp lazy khi map sang DTO. */
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(Pageable pageable) {
        return PageResponse.from(purchaseOrderRepository.findAll(pageable).map(PurchaseOrderResponse::from));
    }
}
