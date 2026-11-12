package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.internal.DocumentDispatchRequest;
import vn.wms.inventory.dto.internal.DocumentDispatchResponse;

/**
 * Lớp ngoài (không có transaction) của trừ tồn đồng bộ theo phiếu xuất (WMS-20.4). Hai lời gọi cùng khóa chạy đồng
 * thời (order-service gửi lại khi lần đầu hết thời gian chờ mà inventory-service vẫn đang xử lý): lời gọi sau vấp ràng
 * buộc duy nhất {@code (company_id, message_id)} của {@code stock_dispatches} khi lời gọi đầu đã commit, transaction
 * của nó rollback; ở đây đọc lại nhật ký trong transaction mới và trả kết quả {@code alreadyProcessed} thay vì lỗi 500.
 */
@Service
public class DocumentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDispatchService.class);

    private final StockDeductionService stockDeductionService;

    public DocumentDispatchService(StockDeductionService stockDeductionService) {
        this.stockDeductionService = stockDeductionService;
    }

    public DocumentDispatchResponse dispatch(DocumentDispatchRequest request) {
        try {
            return stockDeductionService.deductForDocument(request);
        } catch (DataIntegrityViolationException ex) {
            log.info("Khóa trừ tồn {} vừa được một yêu cầu khác ghi, trả kết quả đã xử lý", request.dispatchKey());
            return stockDeductionService.findProcessedDispatch(request.dispatchKey())
                    .orElseThrow(() -> ApiException.conflict("Phiếu xuất " + request.dispatchKey()
                            + " đang được trừ tồn bởi một yêu cầu khác, vui lòng thử lại"));
        }
    }
}
