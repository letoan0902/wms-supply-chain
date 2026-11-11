package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.FrozenLocationConflict;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StocktakeLocation;
import vn.wms.inventory.entity.StocktakeSession;
import vn.wms.inventory.repository.StocktakeLocationRepository;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chốt chặn dùng chung cho cơ chế đóng băng vị trí khi kiểm kê (WMS-14.1, UC-09 luồng ngoại lệ 1a, ERR-04).
 * Các luồng thay đổi tồn kho gọi một dòng duy nhất:
 * <ul>
 *   <li>{@link #assertNotFrozen(Long, Long, String)}: luồng chỉ định vị trí cụ thể (nhập vào ô, xuất từ ô,
 *       nhận hàng điều chuyển vào ô) bị từ chối bằng lỗi 409 nêu rõ mã vị trí và mã đợt kiểm kê.</li>
 *   <li>{@link #excludeFrozen(List)}: luồng tự phân bổ theo FEFO/FIFO (giữ chỗ cho đơn mới, xuất không chỉ định ô)
 *       bỏ qua các dòng tồn đang đóng băng và lấy hàng ở vị trí khác; không đủ hàng thì luồng đó tự báo thiếu.</li>
 * </ul>
 * Khi đợt có giới hạn sản phẩm, vị trí chỉ bị đóng băng đối với các sản phẩm thuộc phạm vi.
 * Gọi trong transaction của nghiệp vụ, trước khi khóa và đổi dòng tồn.
 */
@Component
public class StocktakeFreezeGuard {

    private static final Logger log = LoggerFactory.getLogger(StocktakeFreezeGuard.class);

    private final StocktakeLocationRepository stocktakeLocationRepository;

    public StocktakeFreezeGuard(StocktakeLocationRepository stocktakeLocationRepository) {
        this.stocktakeLocationRepository = stocktakeLocationRepository;
    }

    /**
     * Ném lỗi 409 (CONFLICT, kèm {@link FrozenLocationConflict} trong {@code data}) nếu sản phẩm tại vị trí đang bị
     * đóng băng bởi một đợt kiểm kê.
     *
     * @param binLocationId vị trí ô/kệ; null thì bỏ qua
     * @param productId     sản phẩm của thao tác; null nghĩa là chặn theo vị trí bất kể sản phẩm
     * @param action        tên thao tác để ghi vào thông báo, ví dụ "nhập kho", "xuất kho"
     */
    public void assertNotFrozen(Long binLocationId, Long productId, String action) {
        if (binLocationId == null) {
            return;
        }
        findConflict(binLocationId, productId).ifPresent(conflict -> {
            String message = String.format(
                    "Vị trí %s đang đóng băng để kiểm kê (đợt %s), không thể thực hiện thao tác %s!",
                    conflict.binLocationCode(), conflict.sessionCode(), action != null ? action : "thay đổi tồn kho");
            throw ApiException.withData(ErrorCode.CONFLICT, message, conflict);
        });
    }

    /**
     * Thông tin xung đột nếu sản phẩm tại vị trí đang bị đóng băng, dùng cho kiểm tra trước không ném lỗi
     * (order-service hỏi trước khi lập phiếu nhập, WMS-20.4).
     *
     * @param binLocationId vị trí ô/kệ; null thì không có xung đột
     * @param productId     sản phẩm của thao tác; null nghĩa là xét theo vị trí bất kể sản phẩm
     */
    public Optional<FrozenLocationConflict> findConflict(Long binLocationId, Long productId) {
        if (binLocationId == null) {
            return Optional.empty();
        }
        return findBlockingLocation(binLocationId, productId).map(location -> {
            StocktakeSession session = location.getSession();
            return new FrozenLocationConflict(session.getId(), session.getCode(), binLocationId,
                    location.getBinLocation().getCode(), productId);
        });
    }

    /**
     * Trả về danh sách mới đã bỏ các dòng tồn nằm ở vị trí đang đóng băng (theo sản phẩm của từng dòng).
     * Danh sách rỗng hoặc null được trả lại nguyên trạng.
     */
    public List<StockItem> excludeFrozen(List<StockItem> stockItems) {
        if (stockItems == null || stockItems.isEmpty()) {
            return stockItems;
        }
        Set<Long> binIds = new LinkedHashSet<>();
        for (StockItem item : stockItems) {
            if (item.getBinLocation() != null) {
                binIds.add(item.getBinLocation().getId());
            }
        }
        if (binIds.isEmpty()) {
            return stockItems;
        }
        List<StocktakeLocation> frozen = stocktakeLocationRepository.findFrozenByBinLocationIdsForShare(binIds);
        if (frozen.isEmpty()) {
            return stockItems;
        }
        Map<Long, StocktakeSession> sessionByBin = new HashMap<>();
        for (StocktakeLocation location : frozen) {
            sessionByBin.put(location.getFrozenBinLocationId(), location.getSession());
        }
        List<StockItem> result = stockItems.stream()
                .filter(item -> {
                    StocktakeSession session = item.getBinLocation() != null
                            ? sessionByBin.get(item.getBinLocation().getId()) : null;
                    return session == null || !session.coversProduct(item.getProductId());
                })
                .toList();
        if (result.size() < stockItems.size()) {
            log.info("Bỏ qua {} dòng tồn đang đóng băng kiểm kê khi tự phân bổ hàng", stockItems.size() - result.size());
        }
        return result;
    }

    /** Vị trí có đang bị đóng băng đối với sản phẩm này không (dùng cho kiểm tra không ném lỗi). */
    public boolean isFrozen(Long binLocationId, Long productId) {
        return binLocationId != null && findBlockingLocation(binLocationId, productId).isPresent();
    }

    private Optional<StocktakeLocation> findBlockingLocation(Long binLocationId, Long productId) {
        return stocktakeLocationRepository.findFrozenByBinLocationIdsForShare(List.of(binLocationId)).stream()
                .filter(location -> productId == null || location.getSession().coversProduct(productId))
                .findFirst();
    }
}
