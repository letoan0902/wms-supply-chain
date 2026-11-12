package vn.wms.inventory.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static vn.wms.inventory.alert.StockAlertScanResponse.Status.ALREADY_SENT;
import static vn.wms.inventory.alert.StockAlertScanResponse.Status.FAILED;
import static vn.wms.inventory.alert.StockAlertScanResponse.Status.NOTHING_FOUND;
import static vn.wms.inventory.alert.StockAlertScanResponse.Status.PUBLISHED;
import static vn.wms.inventory.alert.StockAlertTestSupport.page;
import static vn.wms.inventory.alert.StockAlertTestSupport.product;

/** Tác vụ quét: bản tổng hợp theo kho và loại, chống gửi trùng trong ngày, tách doanh nghiệp, lỗi không lan. */
class StockAlertScanIntegrationTest extends StockAlertIntegrationTestBase {

    @Autowired
    StockAlertScanService scanService;

    @Autowired
    StockAlertScheduler scheduler;

    @Autowired
    StockAlertRunGuard guard;

    /** Sự kiện đã "phát", kèm doanh nghiệp trong TenantContext lúc phát (phải trùng companyId của sự kiện). */
    private final List<Published> published = new ArrayList<>();

    private record Published(Long contextCompanyId, StockAlertSummaryEvent event) {
    }

    private void recordPublishedEvents() {
        published.clear();
        doAnswer(invocation -> {
            published.add(new Published(TenantContext.getCompanyId(), invocation.getArgument(1)));
            return "msg-" + published.size();
        }).when(eventPublisher).publish(eq(WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY), any());
    }

    private Map<StockAlertKind, StockAlertScanResponse.Status> statuses(StockAlertScanResponse response) {
        Map<StockAlertKind, StockAlertScanResponse.Status> map = new java.util.EnumMap<>(StockAlertKind.class);
        response.results().forEach(r -> map.put(r.kind(), r.status()));
        return map;
    }

    @Test
    @DisplayName("Một bản tổng hợp cho mỗi kho và loại, các dòng nghiêm trọng nhất nằm trong items")
    void publishesOneSummaryPerWarehouseAndKind() {
        recordPublishedEvents();
        long bin1 = data.warehouseWithBin(COMPANY_A, "WH-1", "Kho số 1");
        long bin2 = data.warehouseWithBin(COMPANY_A, "WH-2", "Kho số 2");
        for (int i = 0; i < 12; i++) {
            data.stock(COMPANY_A, bin1, 200L + i, "L" + i, "1.5", "0", TODAY.plusDays(1 + i), false);
        }
        data.stock(COMPANY_A, bin2, 300L, "X", "2", "0", TODAY.plusDays(4), false);
        data.stock(COMPANY_A, bin2, 301L, "Y", "3", "0", TODAY, true);
        data.stock(COMPANY_A, bin2, 302L, null, "1", "0", null, false);
        catalogs.put(COMPANY_A, page(product(302L, "SKU-302", "2", "5")));

        StockAlertScanResponse response = TenantContext.callAs(COMPANY_A,
                () -> scanService.scanCurrentCompany("admin"));

        assertThat(statuses(response)).containsEntry(StockAlertKind.NEAR_EXPIRY, PUBLISHED)
                .containsEntry(StockAlertKind.EXPIRED, PUBLISHED)
                .containsEntry(StockAlertKind.LOW_STOCK, PUBLISHED)
                .containsEntry(StockAlertKind.DEAD_STOCK, NOTHING_FOUND);
        assertThat(published).hasSize(4);
        assertThat(published).allSatisfy(p -> {
            assertThat(p.event().companyId()).isEqualTo(COMPANY_A);
            assertThat(p.contextCompanyId()).isEqualTo(COMPANY_A);
            assertThat(p.event().alertDate()).isEqualTo(TODAY);
        });

        StockAlertSummaryEvent nearExpiry1 = published.stream().map(Published::event)
                .filter(e -> e.kind() == StockAlertKind.NEAR_EXPIRY && "WH-1".equals(e.warehouseCode()))
                .findFirst().orElseThrow();
        assertThat(nearExpiry1.totalItems()).isEqualTo(12);
        assertThat(nearExpiry1.items()).hasSize(10);
        assertThat(nearExpiry1.items().get(0).daysLeft()).isEqualTo(1L);
        assertThat(nearExpiry1.totalQuantity()).isEqualByComparingTo("18");
        assertThat(nearExpiry1.thresholdDays()).isEqualTo(30);
        assertThat(nearExpiry1.severity()).isEqualTo(StockAlertSeverity.WARNING);

        StockAlertSummaryEvent lowStock = published.stream().map(Published::event)
                .filter(e -> e.kind() == StockAlertKind.LOW_STOCK).findFirst().orElseThrow();
        assertThat(lowStock.warehouseName()).isEqualTo("Kho số 2");
        assertThat(lowStock.severity()).isEqualTo(StockAlertSeverity.CRITICAL);
        assertThat(lowStock.items().get(0).skuCode()).isEqualTo("SKU-302");
        assertThat(lowStock.thresholdDays()).isNull();

        // Dấu chống gửi trùng đã ghi cho ba loại có cảnh báo, không ghi cho loại không có
        List<StockAlertRun> runs = TenantContext.callAs(COMPANY_A, () -> runRepository.findByAlertDate(TODAY));
        assertThat(runs).extracting(StockAlertRun::getAlertKind)
                .containsExactlyInAnyOrder(StockAlertKind.NEAR_EXPIRY, StockAlertKind.EXPIRED, StockAlertKind.LOW_STOCK);
        assertThat(runs).allSatisfy(run -> assertThat(run.getStatus()).isEqualTo(StockAlertRun.STATUS_COMPLETED));
    }

    @Test
    @DisplayName("Chạy lại trong ngày (quét tay sau tác vụ 00:00, hai instance) không phát lại")
    void secondRunOnSameDayDoesNotPublishAgain() {
        recordPublishedEvents();
        long bin = data.warehouseWithBin(COMPANY_A, "WH-1", "Kho số 1");
        data.stock(COMPANY_A, bin, 400L, "L1", "1", "0", TODAY.plusDays(2), false);

        TenantContext.callAs(COMPANY_A, () -> scanService.scanCurrentCompany(StockAlertScanService.SCHEDULER));
        assertThat(published).hasSize(1);

        StockAlertScanResponse second = TenantContext.callAs(COMPANY_A, () -> scanService.scanCurrentCompany("admin"));
        assertThat(statuses(second)).containsEntry(StockAlertKind.NEAR_EXPIRY, ALREADY_SENT);
        assertThat(published).hasSize(1);

        // Instance thứ hai giữ chỗ cùng (doanh nghiệp, ngày, loại): không được
        Optional<Long> claim = TenantContext.callAs(COMPANY_A,
                () -> guard.claim(TODAY, StockAlertKind.NEAR_EXPIRY, "instance-2"));
        assertThat(claim).isEmpty();
        // Ngày hôm sau thì được
        Optional<Long> tomorrow = TenantContext.callAs(COMPANY_A,
                () -> guard.claim(TODAY.plusDays(1), StockAlertKind.NEAR_EXPIRY, "instance-2"));
        assertThat(tomorrow).isPresent();
    }

    @Test
    @DisplayName("Tác vụ chạy từng doanh nghiệp với đúng ngữ cảnh; doanh nghiệp lỗi không chặn doanh nghiệp khác")
    void schedulerIsolatesCompaniesAndSurvivesFailures() {
        long binA = data.warehouseWithBin(COMPANY_A, "WH-A", "Kho của A");
        long binB = data.warehouseWithBin(COMPANY_B, "WH-B", "Kho của B");
        data.stock(COMPANY_A, binA, 500L, "A1", "1", "0", TODAY.minusDays(1), true);
        data.stock(COMPANY_B, binB, 500L, "B1", "2", "0", TODAY.minusDays(1), true);

        // Broker từ chối mọi sự kiện của doanh nghiệp A
        published.clear();
        doAnswer(invocation -> {
            if (COMPANY_A.equals(TenantContext.getCompanyId())) {
                throw new AmqpConnectException(new java.net.ConnectException("broker không phản hồi"));
            }
            published.add(new Published(TenantContext.getCompanyId(), invocation.getArgument(1)));
            return "msg";
        }).when(eventPublisher).publish(eq(WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY), any());

        Map<Long, StockAlertScanResponse> results = scheduler.scanAllCompanies();

        assertThat(statuses(results.get(COMPANY_A))).containsEntry(StockAlertKind.EXPIRED, FAILED);
        assertThat(statuses(results.get(COMPANY_B))).containsEntry(StockAlertKind.EXPIRED, PUBLISHED);
        // Cơ sở dữ liệu test dùng chung với lớp test khác: chỉ xét hai doanh nghiệp của lớp này
        List<Published> ofOurs = ours();
        assertThat(ofOurs).hasSize(1);
        assertThat(ofOurs.get(0).event().companyId()).isEqualTo(COMPANY_B);
        assertThat(ofOurs.get(0).contextCompanyId()).isEqualTo(COMPANY_B);
        assertThat(ofOurs.get(0).event().items()).extracting(i -> i.lotNo()).containsExactly("B1");
        // A được gỡ dấu để lần quét sau thử lại, B giữ dấu
        assertThat(TenantContext.callAs(COMPANY_A, () -> runRepository.findByAlertDate(TODAY))).isEmpty();
        assertThat(TenantContext.callAs(COMPANY_B, () -> runRepository.findByAlertDate(TODAY))).hasSize(1);

        // Broker trở lại: lần quét sau gửi được cho A, không gửi lại cho B
        recordPublishedEvents();
        Map<Long, StockAlertScanResponse> retry = scheduler.scanAllCompanies();
        assertThat(statuses(retry.get(COMPANY_A))).containsEntry(StockAlertKind.EXPIRED, PUBLISHED);
        assertThat(statuses(retry.get(COMPANY_B))).containsEntry(StockAlertKind.EXPIRED, ALREADY_SENT);
        assertThat(ours()).extracting(p -> p.event().companyId()).containsExactly(COMPANY_A);
        assertThat(ours()).allSatisfy(p -> assertThat(p.contextCompanyId()).isEqualTo(p.event().companyId()));
    }

    private List<Published> ours() {
        return published.stream()
                .filter(p -> COMPANY_A.equals(p.event().companyId()) || COMPANY_B.equals(p.event().companyId()))
                .toList();
    }
}
