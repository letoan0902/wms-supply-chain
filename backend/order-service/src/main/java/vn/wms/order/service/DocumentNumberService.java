package vn.wms.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.persistence.RequestHeaderAuditorAware;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.config.DocumentSequencePool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Cấp số chứng từ (PO, SO, GRN, GDN, PB) theo từng doanh nghiệp, dạng {@code <tiền tố><4 chữ số>}, ví dụ
 * {@code PO-20261110-0001} (WMS-22.4b).
 *
 * <p>Bộ đếm nằm trong bảng {@code document_sequences}, mỗi doanh nghiệp một dòng cho mỗi tiền tố. Số mới được cấp
 * trong một transaction JDBC ngắn trên nhóm kết nối riêng {@link DocumentSequencePool} (không phải nhóm kết nối chính
 * mà transaction lập chứng từ đang giữ): khóa dòng bộ đếm ({@code SELECT ... FOR UPDATE}), tăng, commit ngay rồi mới
 * trả về.
 * <ul>
 *   <li>hai yêu cầu đồng thời, kể cả ở hai phiên bản service khác nhau, không bao giờ nhận cùng một số;</li>
 *   <li>khóa chỉ giữ trong vài mili giây, không kéo dài theo transaction lập chứng từ (có thể gọi sang service khác);</li>
 *   <li>chứng từ lập thất bại để lại một số bị bỏ qua, không gây trùng số;</li>
 *   <li>request lập chứng từ không bao giờ vừa giữ một kết nối của nhóm chính vừa chờ kết nối thứ hai của cùng nhóm
 *       (WMS-20.4: trước đây dùng {@code REQUIRES_NEW} trên nhóm chính, tải đồng thời làm nhóm treo tới hết thời gian
 *       chờ, xem docs/PERFORMANCE.md mục 5).</li>
 * </ul>
 * Lần đầu dùng một tiền tố, bộ đếm bắt đầu từ số lớn nhất đã có trong doanh nghiệp để không cấp lại số của dữ liệu
 * tạo trước khi có bảng này; truy vấn đó và việc kiểm tra số đã dùng chạy qua JPA trên kết nối của người gọi, lúc
 * không giữ kết nối cấp số nào. Số do người dùng tự nhập trùng với số sắp cấp thì được bỏ qua và cấp số kế tiếp.
 *
 * <p>SQL ở đây là SQL gốc nên Hibernate không tự lọc theo doanh nghiệp: mọi câu đều có điều kiện {@code company_id}
 * lấy từ {@link TenantContext#requireCompanyId()}.
 */
@Service
public class DocumentNumberService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNumberService.class);

    /** Số lần thử lại khi hai yêu cầu cùng tạo dòng bộ đếm đầu tiên của một tiền tố. */
    private static final int MAX_ATTEMPTS = 3;

    private static final String SELECT_FOR_UPDATE =
            "SELECT last_number FROM document_sequences WHERE company_id = ? AND seq_prefix = ? FOR UPDATE";
    private static final String UPDATE =
            "UPDATE document_sequences SET last_number = ?, updated_at = ?, updated_by = ?"
                    + " WHERE company_id = ? AND seq_prefix = ?";
    private static final String INSERT =
            "INSERT INTO document_sequences (company_id, seq_prefix, last_number, created_at, updated_at,"
                    + " created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final DocumentSequencePool pool;
    private final ObjectProvider<AuditorAware<String>> auditorAware;

    public DocumentNumberService(DocumentSequencePool pool, ObjectProvider<AuditorAware<String>> auditorAware) {
        this.pool = pool;
        this.auditorAware = auditorAware;
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
        long companyId = TenantContext.requireCompanyId();
        String candidate;
        do {
            candidate = String.format("%s%04d", prefix, nextValue(companyId, prefix, latestWithPrefix));
        } while (alreadyUsed.test(candidate));
        return candidate;
    }

    private long nextValue(long companyId, String prefix, Function<String, Optional<String>> latestWithPrefix) {
        for (int attempt = 1; ; attempt++) {
            try {
                OptionalLong incremented = incrementExisting(companyId, prefix);
                if (incremented.isPresent()) {
                    return incremented.getAsLong();
                }
                // Chưa có dòng bộ đếm: tìm số lớn nhất đã lưu trên kết nối của người gọi, lúc không giữ kết nối cấp số
                long start = latestWithPrefix.apply(prefix)
                        .map(number -> parseSuffix(number, prefix))
                        .orElse(0L);
                return insertFirst(companyId, prefix, start + 1);
            } catch (SQLException ex) {
                if (ex instanceof SQLTransientConnectionException) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Hệ thống đang bận cấp số chứng từ, vui lòng thử lại", ex);
                }
                if (!isRetryable(ex)) {
                    throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không cấp được số chứng từ", ex);
                }
                // Hai yêu cầu cùng tạo dòng bộ đếm đầu tiên: yêu cầu thua thử lại và sẽ khóa được dòng vừa tạo.
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Hệ thống đang bận cấp số chứng từ, vui lòng thử lại", ex);
                }
                log.info("Tranh chấp khi khởi tạo bộ đếm số chứng từ {}, thử lại lần {}", prefix, attempt + 1);
            }
        }
    }

    /** Khóa dòng bộ đếm, tăng và commit. Rỗng (đã rollback, không giữ khóa) khi doanh nghiệp chưa có dòng cho tiền tố. */
    private OptionalLong incrementExisting(long companyId, String prefix) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            try {
                Long current = null;
                try (PreparedStatement select = connection.prepareStatement(SELECT_FOR_UPDATE)) {
                    select.setLong(1, companyId);
                    select.setString(2, prefix);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            current = rs.getLong(1);
                        }
                    }
                }
                if (current == null) {
                    connection.rollback();
                    return OptionalLong.empty();
                }
                long value = current + 1;
                try (PreparedStatement update = connection.prepareStatement(UPDATE)) {
                    update.setLong(1, value);
                    update.setTimestamp(2, Timestamp.from(Instant.now()), utcCalendar());
                    update.setString(3, currentAuditor());
                    update.setLong(4, companyId);
                    update.setString(5, prefix);
                    update.executeUpdate();
                }
                connection.commit();
                return OptionalLong.of(value);
            } catch (SQLException | RuntimeException ex) {
                rollbackQuietly(connection, ex);
                throw ex;
            }
        }
    }

    /** Tạo dòng bộ đếm đầu tiên. Vi phạm khóa duy nhất nghĩa là yêu cầu khác vừa tạo trước: người gọi thử lại. */
    private long insertFirst(long companyId, String prefix, long value) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                Timestamp now = Timestamp.from(Instant.now());
                String auditor = currentAuditor();
                insert.setLong(1, companyId);
                insert.setString(2, prefix);
                insert.setLong(3, value);
                insert.setTimestamp(4, now, utcCalendar());
                insert.setTimestamp(5, now, utcCalendar());
                insert.setString(6, auditor);
                insert.setString(7, auditor);
                insert.executeUpdate();
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException ex) {
                rollbackQuietly(connection, ex);
                throw ex;
            }
        }
    }

    /** Vi phạm ràng buộc (SQLSTATE 23xxx) hoặc transaction bị hủy do tranh chấp khóa (40xxx): thử lại được. */
    private static boolean isRetryable(SQLException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("23") || sql.getSQLState().startsWith("40"))) {
                return true;
            }
        }
        return false;
    }

    private static void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    /** Cột thời gian lưu theo UTC, giống cấu hình {@code hibernate.jdbc.time_zone: UTC} của entity. */
    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private String currentAuditor() {
        AuditorAware<String> auditor = auditorAware.getIfAvailable();
        return Optional.ofNullable(auditor)
                .flatMap(AuditorAware::getCurrentAuditor)
                .orElse(RequestHeaderAuditorAware.SYSTEM_AUDITOR);
    }

    private static long parseSuffix(String number, String prefix) {
        try {
            return Long.parseLong(number.substring(prefix.length()));
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
