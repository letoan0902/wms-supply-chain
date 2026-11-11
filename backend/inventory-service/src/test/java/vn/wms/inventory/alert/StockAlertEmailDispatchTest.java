package vn.wms.inventory.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static vn.wms.inventory.alert.StockAlertTestSupport.page;
import static vn.wms.inventory.alert.StockAlertTestSupport.product;

/**
 * Yêu cầu gửi email cảnh báo (WMS-15.2): một email cho mỗi người nhận và loại, chỉ chứa kho người đó phụ trách, người
 * nhận tra trong đúng doanh nghiệp sở hữu tồn kho, không gửi lại trong ngày, lỗi tra người nhận không chặn bản tổng hợp.
 */
class StockAlertEmailDispatchTest extends StockAlertIntegrationTestBase {

    @Autowired
    StockAlertScanService scanService;

    @Autowired
    StockAlertScheduler scheduler;

    private final List<StockAlertDigestEvent> digests = new ArrayList<>();
    private final List<Long> lookupCompanies = new ArrayList<>();
    private long warehouse1;
    private long warehouse2;
    private long warehouseB;

    private static final AlertRecipient ADMIN = new AlertRecipient("Quản trị A", "admin@congty-a.vn", "ADMIN");
    private static final AlertRecipient MANAGER_1 = new AlertRecipient("Quản lý kho 1", "ql1@congty-a.vn", "WAREHOUSE_MANAGER");
    private static final AlertRecipient MANAGER_2 = new AlertRecipient("Quản lý kho 2", "ql2@congty-a.vn", "WAREHOUSE_MANAGER");
    private static final AlertRecipient ADMIN_B = new AlertRecipient("Quản trị B", "admin@congty-b.vn", "ADMIN");

    @BeforeEach
    void setUpData() {
        long bin1 = data.warehouseWithBin(COMPANY_A, "WH-1", "Kho số 1");
        long bin2 = data.warehouseWithBin(COMPANY_A, "WH-2", "Kho số 2");
        long binB = data.warehouseWithBin(COMPANY_B, "WH-1", "Kho của B");
        warehouse1 = data.warehouseIdOfBin(COMPANY_A, bin1);
        warehouse2 = data.warehouseIdOfBin(COMPANY_A, bin2);
        warehouseB = data.warehouseIdOfBin(COMPANY_B, binB);

        data.stock(COMPANY_A, bin1, 600L, "A-C1", "2.5", "0", TODAY.plusDays(5), false);
        data.stock(COMPANY_A, bin2, 601L, "A-C2", "1", "0", TODAY.plusDays(9), false);
        data.stock(COMPANY_A, bin2, 602L, null, "3", "0", null, false);
        long idle = data.stock(COMPANY_A, bin1, 603L, null, "4", "0", null, false);
        data.lastChangedOn(idle, TODAY.minusDays(120));
        data.stock(COMPANY_B, binB, 600L, "B-C1", "7", "0", TODAY.plusDays(2), false);
        catalogs.put(COMPANY_A, page(product(602L, "SKU-602", "5", "10")));

        recipients.put(recipientKey(COMPANY_A, warehouse1), List.of(ADMIN, MANAGER_1));
        recipients.put(recipientKey(COMPANY_A, warehouse2), List.of(ADMIN, MANAGER_2));
        recipients.put(recipientKey(COMPANY_B, warehouseB), List.of(ADMIN_B));

        digests.clear();
        lookupCompanies.clear();
        doAnswer(invocation -> {
            StockAlertDigestEvent digest = invocation.getArgument(1);
            assertThat(TenantContext.getCompanyId()).isEqualTo(digest.companyId());
            digests.add(digest);
            return "digest-" + digests.size();
        }).when(eventPublisher).publish(eq(WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST), any());
        when(recipientClient.alertRecipients(anyLong())).thenAnswer(invocation -> {
            lookupCompanies.add(TenantContext.getCompanyId());
            return vn.wms.common.api.ApiResponse.ok(recipients.getOrDefault(
                    recipientKey(TenantContext.getCompanyId(), invocation.getArgument(0)), List.of()));
        });
    }

    private List<StockAlertDigestEvent> digestsOf(String email, StockAlertKind kind) {
        return digests.stream().filter(d -> d.recipientEmail().equals(email) && d.kind() == kind).toList();
    }

    @Test
    @DisplayName("Admin nhận một email gồm mọi kho, quản lý chỉ nhận kho của mình; hàng tồn lâu không gửi email")
    void oneEmailPerRecipientAndKindWithOnlyTheirWarehouses() {
        StockAlertScanResponse response = TenantContext.callAs(COMPANY_A,
                () -> scanService.scanCurrentCompany(StockAlertScanService.SCHEDULER));

        List<StockAlertDigestEvent> adminNearExpiry = digestsOf("admin@congty-a.vn", StockAlertKind.NEAR_EXPIRY);
        assertThat(adminNearExpiry).hasSize(1);
        assertThat(adminNearExpiry.get(0).sections()).extracting(StockAlertSummaryEvent::warehouseCode)
                .containsExactlyInAnyOrder("WH-1", "WH-2");
        assertThat(adminNearExpiry.get(0).recipientRole()).isEqualTo("ADMIN");
        assertThat(adminNearExpiry.get(0).alertDate()).isEqualTo(TODAY);

        List<StockAlertDigestEvent> manager1 = digestsOf("ql1@congty-a.vn", StockAlertKind.NEAR_EXPIRY);
        assertThat(manager1).hasSize(1);
        assertThat(manager1.get(0).sections()).extracting(StockAlertSummaryEvent::warehouseCode).containsExactly("WH-1");
        assertThat(manager1.get(0).sections().get(0).items()).extracting(i -> i.lotNo()).containsExactly("A-C1");
        assertThat(digestsOf("ql2@congty-a.vn", StockAlertKind.NEAR_EXPIRY).get(0).sections())
                .extracting(StockAlertSummaryEvent::warehouseCode).containsExactly("WH-2");

        // Tồn thấp chỉ ở kho 2: admin và quản lý kho 2, không có quản lý kho 1
        assertThat(digests.stream().filter(d -> d.kind() == StockAlertKind.LOW_STOCK)
                .map(StockAlertDigestEvent::recipientEmail)).containsExactlyInAnyOrder("admin@congty-a.vn", "ql2@congty-a.vn");
        // Hàng tồn lâu không nằm trong ALERT_EMAIL_KINDS mặc định
        assertThat(digests).noneMatch(d -> d.kind() == StockAlertKind.DEAD_STOCK);
        assertThat(response.results()).filteredOn(r -> r.kind() == StockAlertKind.DEAD_STOCK)
                .extracting(StockAlertScanResponse.KindResult::status, StockAlertScanResponse.KindResult::emailCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(StockAlertScanResponse.Status.PUBLISHED, 0));
        assertThat(response.results()).filteredOn(r -> r.kind() == StockAlertKind.NEAR_EXPIRY)
                .extracting(StockAlertScanResponse.KindResult::emailCount).containsExactly(3);
        // Mỗi kho chỉ tra người nhận một lần cho mọi loại, luôn trong doanh nghiệp A, không ai thuộc B
        assertThat(lookupCompanies).hasSize(2).containsOnly(COMPANY_A);
        assertThat(digests).allSatisfy(d -> assertThat(d.companyId()).isEqualTo(COMPANY_A));
        assertThat(digests).noneMatch(d -> d.recipientEmail().equals("admin@congty-b.vn"));

        // Số email được ghi vào dấu của ngày
        List<StockAlertRun> runs = TenantContext.callAs(COMPANY_A, () -> runRepository.findByAlertDate(TODAY));
        assertThat(runs).filteredOn(r -> r.getAlertKind() == StockAlertKind.NEAR_EXPIRY)
                .extracting(StockAlertRun::getEmailCount).containsExactly(3);
    }

    @Test
    @DisplayName("Chạy lại trong ngày không gửi lại email; email của B chỉ tới người của B")
    void noDuplicateEmailsAndCompaniesStaySeparate() {
        scheduler.scanAllCompanies();
        int afterFirstRun = digests.size();
        scheduler.scanAllCompanies();

        List<StockAlertDigestEvent> ours = digests.stream()
                .filter(d -> COMPANY_A.equals(d.companyId()) || COMPANY_B.equals(d.companyId())).toList();
        assertThat(digests).hasSize(afterFirstRun);
        assertThat(ours).filteredOn(d -> COMPANY_B.equals(d.companyId()))
                .extracting(StockAlertDigestEvent::recipientEmail).containsExactly("admin@congty-b.vn");
        assertThat(ours).filteredOn(d -> COMPANY_B.equals(d.companyId()))
                .allSatisfy(d -> assertThat(d.sections()).extracting(StockAlertSummaryEvent::warehouseName)
                        .containsExactly("Kho của B"));
        assertThat(ours).filteredOn(d -> COMPANY_A.equals(d.companyId()))
                .noneMatch(d -> d.sections().stream().anyMatch(s -> "Kho của B".equals(s.warehouseName())));
    }

    @Test
    @DisplayName("auth-service lỗi: bản tổng hợp vẫn phát, email ghi chú lỗi, không gỡ dấu")
    void recipientLookupFailureDoesNotBlockSummaries() {
        when(recipientClient.alertRecipients(anyLong())).thenThrow(new IllegalStateException("auth-service không phản hồi"));

        StockAlertScanResponse response = TenantContext.callAs(COMPANY_A,
                () -> scanService.scanCurrentCompany(StockAlertScanService.SCHEDULER));

        assertThat(digests).isEmpty();
        assertThat(response.results()).filteredOn(r -> r.kind() == StockAlertKind.NEAR_EXPIRY)
                .allSatisfy(r -> {
                    assertThat(r.status()).isEqualTo(StockAlertScanResponse.Status.PUBLISHED);
                    assertThat(r.emailCount()).isZero();
                    assertThat(r.message()).contains("Không gửi được email");
                });
        assertThat(TenantContext.callAs(COMPANY_A, () -> runRepository.findByAlertDate(TODAY)))
                .filteredOn(r -> r.getAlertKind() == StockAlertKind.NEAR_EXPIRY)
                .allSatisfy(r -> assertThat(r.getNote()).contains("auth-service không phản hồi"));
    }
}
