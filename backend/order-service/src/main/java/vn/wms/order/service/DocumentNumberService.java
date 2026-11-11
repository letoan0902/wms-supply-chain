package vn.wms.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.entity.DocumentSequence;
import vn.wms.order.repository.DocumentSequenceRepository;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Cấp số chứng từ (PO, SO, GRN, GDN, PB) theo từng doanh nghiệp, dạng {@code <tiền tố><4 chữ số>}, ví dụ
 * {@code PO-20261110-0001} (WMS-22.4b).
 *
 * <p>Bộ đếm nằm trong bảng {@code document_sequences}, mỗi doanh nghiệp một dòng cho mỗi tiền tố. Số mới được cấp
 * trong một transaction riêng ({@code REQUIRES_NEW}) có khóa dòng bộ đếm, commit ngay rồi mới trả về:
 * <ul>
 *   <li>hai yêu cầu đồng thời, kể cả ở hai phiên bản service khác nhau, không bao giờ nhận cùng một số;</li>
 *   <li>khóa chỉ giữ trong vài mili giây, không kéo dài theo transaction lập chứng từ (có thể gọi sang service khác);</li>
 *   <li>chứng từ lập thất bại để lại một số bị bỏ qua, không gây trùng số.</li>
 * </ul>
 * Lần đầu dùng một tiền tố, bộ đếm bắt đầu từ số lớn nhất đã có trong doanh nghiệp để không cấp lại số của dữ liệu
 * tạo trước khi có bảng này. Số do người dùng tự nhập trùng với số sắp cấp thì được bỏ qua và cấp số kế tiếp.
 */
@Service
public class DocumentNumberService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNumberService.class);

    /** Số lần thử lại khi hai yêu cầu cùng tạo dòng bộ đếm đầu tiên của một tiền tố. */
    private static final int MAX_ATTEMPTS = 3;

    private final DocumentSequenceRepository sequenceRepository;
    private final TransactionTemplate newTransaction;

    public DocumentNumberService(DocumentSequenceRepository sequenceRepository,
                                 PlatformTransactionManager transactionManager) {
        this.sequenceRepository = sequenceRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Cấp số chứng từ tiếp theo cho doanh nghiệp hiện tại.
     *
     * @param prefix              tiền tố gồm loại chứng từ và ngày, ví dụ {@code PO-20261110-}
     * @param latestWithPrefix    tìm số chứng từ lớn nhất đã lưu với tiền tố này trong doanh nghiệp (khởi tạo bộ đếm)
     * @param alreadyUsed         kiểm tra một số đã được dùng trong doanh nghiệp hay chưa
     * @throws ApiException {@code COMPANY_REQUIRED} khi chưa xác định doanh nghiệp
     */
    public String next(String prefix,
                       Function<String, Optional<String>> latestWithPrefix,
                       Predicate<String> alreadyUsed) {
        TenantContext.requireCompanyId();
        String candidate;
        do {
            candidate = String.format("%s%04d", prefix, nextValue(prefix, latestWithPrefix));
        } while (alreadyUsed.test(candidate));
        return candidate;
    }

    private long nextValue(String prefix, Function<String, Optional<String>> latestWithPrefix) {
        for (int attempt = 1; ; attempt++) {
            try {
                Long value = newTransaction.execute(status -> {
                    Optional<DocumentSequence> existing = sequenceRepository.findByPrefixForUpdate(prefix);
                    if (existing.isPresent()) {
                        return existing.get().increment();
                    }
                    long start = latestWithPrefix.apply(prefix)
                            .map(number -> parseSuffix(number, prefix))
                            .orElse(0L);
                    DocumentSequence created = new DocumentSequence(prefix, start + 1);
                    sequenceRepository.saveAndFlush(created);
                    return created.getLastNumber();
                });
                return value;
            } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
                // Hai yêu cầu cùng tạo dòng bộ đếm đầu tiên: yêu cầu thua thử lại và sẽ khóa được dòng vừa tạo.
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Hệ thống đang bận cấp số chứng từ, vui lòng thử lại", ex);
                }
                log.info("Tranh chấp khi khởi tạo bộ đếm số chứng từ {}, thử lại lần {}", prefix, attempt + 1);
            }
        }
    }

    private static long parseSuffix(String number, String prefix) {
        try {
            return Long.parseLong(number.substring(prefix.length()));
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
