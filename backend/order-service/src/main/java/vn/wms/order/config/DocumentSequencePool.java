package vn.wms.order.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Nhóm kết nối riêng, rất nhỏ, chỉ dùng để cấp số chứng từ ({@code DocumentNumberService}), WMS-20.4.
 *
 * <p>Service lập chứng từ cấp số khi transaction của nó đang giữ một kết nối của nhóm chính. Nếu việc cấp số lấy thêm
 * kết nối từ chính nhóm đó thì mỗi request cần hai kết nối, và khi số request đồng thời bằng kích thước nhóm thì mọi
 * luồng giữ một kết nối và chờ kết nối thứ hai: nhóm treo tới hết thời gian chờ (docs/PERFORMANCE.md, mục 5). Tách
 * nhóm làm đồ thị chờ không còn vòng: luồng giữ kết nối chính có thể chờ kết nối cấp số, còn luồng giữ kết nối cấp số
 * chỉ chờ khóa dòng bộ đếm (giữ vài mili giây) rồi trả kết nối ngay.
 *
 * <p>Cùng thông số kết nối với nhóm chính ({@link DataSourceProperties}: URL, tài khoản, mật khẩu, driver). Cố ý KHÔNG
 * đăng ký thành bean kiểu {@code DataSource}: có bean như vậy thì Spring Boot thôi tự cấu hình nguồn dữ liệu chính,
 * JPA và Flyway sẽ dùng nhầm nhóm này. Nhóm khởi tạo khi lấy kết nối lần đầu (sau khi Flyway đã chạy) và đóng khi
 * service dừng.
 */
@Component
public class DocumentSequencePool implements DisposableBean {

    static final String POOL_NAME = "document-sequence";
    /** Kết nối cấp số chỉ bị giữ vài mili giây, chờ lâu hơn thế này là có sự cố thật. */
    private static final long CONNECTION_TIMEOUT_MS = 5_000;

    private final HikariDataSource dataSource;

    public DocumentSequencePool(DataSourceProperties properties,
                                @Value("${app.document-sequence.pool-size:3}") int poolSize) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(POOL_NAME);
        ds.setJdbcUrl(properties.determineUrl());
        ds.setUsername(properties.determineUsername());
        ds.setPassword(properties.determinePassword());
        String driver = properties.determineDriverClassName();
        if (driver != null) {
            ds.setDriverClassName(driver);
        }
        ds.setMaximumPoolSize(Math.max(1, poolSize));
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        // Mỗi lần cấp số là một transaction ngắn tự commit; READ COMMITTED để SELECT ... FOR UPDATE trên dòng chưa
        // tồn tại không giữ khóa khoảng (gap lock) của MySQL.
        ds.setAutoCommit(false);
        ds.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        this.dataSource = ds;
    }

    /** Kết nối của nhóm cấp số, auto-commit tắt. Người gọi phải commit hoặc rollback rồi đóng (trả về nhóm). */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void destroy() {
        dataSource.close();
    }
}
