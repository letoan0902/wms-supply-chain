package vn.wms.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.service.DocumentNumberService;

import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tái hiện lỗi cạn kết nối khi tạo chứng từ đồng thời (WMS-20.4, chạy tải WMS-19.2): trước đây mỗi lần cấp số mở thêm
 * một transaction {@code REQUIRES_NEW} trên cùng nhóm kết nối chính trong khi transaction lập chứng từ đang giữ một
 * kết nối. Khi số request tạo chứng từ cùng lúc bằng kích thước nhóm, mọi luồng giữ một kết nối và chờ kết nối thứ
 * hai: nhóm kết nối treo tới hết thời gian chờ.
 *
 * <p>Test thu nhóm kết nối chính còn 4 (chờ tối đa 3 giây), cho 4 luồng chắc chắn cùng giữ kết nối rồi mới cấp số và
 * chạy 16 luồng cùng lúc. Cấp số qua nhóm kết nối riêng ({@code document-sequence}) thì mọi lần cấp thành công, số
 * liên tiếp và không trùng trong từng doanh nghiệp. Dùng cơ sở dữ liệu H2 riêng để không đụng dữ liệu của test khác.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order_pool_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=3000"
})
@ActiveProfiles("test")
class DocumentNumberPoolStarvationTest {

    private static final int THREADS = 16;
    /** Bằng kích thước nhóm kết nối chính: đủ để mọi kết nối chính bị giữ cùng lúc. */
    private static final int HOLDERS = 4;
    private static final long COMPANY_A = 1L;
    private static final long COMPANY_B = 2L;

    @Autowired
    private DocumentNumberService documentNumberService;
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private InventoryGateway inventoryGateway;
    @MockBean
    private WmsEventPublisher wmsEventPublisher;

    private final String prefix = "SO-POOL" + (System.nanoTime() % 100_000_000L) + "-";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM document_sequences WHERE seq_prefix = ?", prefix);
    }

    @Test
    @DisplayName("16 luồng tạo chứng từ cùng lúc với nhóm kết nối chính 4: không luồng nào chờ kết nối thứ hai, số liên tiếp, không trùng theo doanh nghiệp")
    void concurrentAllocationInsideTransactionsDoesNotStarvePrimaryPool() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch holding = new CountDownLatch(HOLDERS);
        CountDownLatch done = new CountDownLatch(THREADS);
        Map<Long, List<String>> numbersByCompany = new ConcurrentHashMap<>();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < THREADS; i++) {
                long companyId = i % 2 == 0 ? COMPANY_A : COMPANY_B;
                executor.submit(() -> {
                    try {
                        start.await();
                        String number = TenantContext.callAs(companyId, () -> transaction.execute(status -> {
                            // Giống service lập chứng từ: transaction đã giữ một kết nối của nhóm chính trước khi cấp số
                            salesOrderRepository.count();
                            holding.countDown();
                            awaitQuietly(holding);
                            return documentNumberService.next(prefix,
                                    p -> salesOrderRepository.findTopBySoNumberStartingWithOrderBySoNumberDesc(p)
                                            .map(SalesOrder::getSoNumber),
                                    salesOrderRepository::existsBySoNumber);
                        }));
                        numbersByCompany.computeIfAbsent(companyId, k -> Collections.synchronizedList(new ArrayList<>()))
                                .add(number);
                    } catch (Throwable ex) {
                        failures.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures.stream().filter(DocumentNumberPoolStarvationTest::isPoolTimeout).toList())
                .as("không luồng nào hết thời gian chờ kết nối của nhóm chính")
                .isEmpty();
        assertThat(failures).isEmpty();
        List<String> expected = IntStream.rangeClosed(1, THREADS / 2)
                .mapToObj(n -> String.format("%s%04d", prefix, n))
                .collect(Collectors.toList());
        assertThat(numbersByCompany.get(COMPANY_A)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(numbersByCompany.get(COMPANY_B)).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isPoolTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
