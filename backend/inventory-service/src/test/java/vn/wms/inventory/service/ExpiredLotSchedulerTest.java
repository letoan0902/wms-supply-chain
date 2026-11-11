package vn.wms.inventory.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.AutoLockResponse;
import vn.wms.inventory.repository.StockItemRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredLotSchedulerTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private LotService lotService;

    @InjectMocks
    private ExpiredLotScheduler scheduler;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Mỗi doanh nghiệp chạy trong đúng ngữ cảnh của mình, một doanh nghiệp lỗi không chặn doanh nghiệp khác")
    void runsEachCompanyInItsOwnContextAndContinuesAfterFailure() {
        when(stockItemRepository.findAllCompanyIds()).thenReturn(Arrays.asList(1L, null, 2L, 3L));
        List<Long> companiesSeen = new ArrayList<>();
        when(lotService.autoLockExpiredLots()).thenAnswer(invocation -> {
            Long companyId = TenantContext.getCompanyId();
            companiesSeen.add(companyId);
            if (companyId == 2L) {
                throw new IllegalStateException("Lỗi giả lập của doanh nghiệp 2");
            }
            return new AutoLockResponse(companyId.intValue(), "ok", Instant.now());
        });

        int totalLocked = scheduler.autoLockExpiredLotsForAllCompanies();

        assertThat(companiesSeen).containsExactly(1L, 2L, 3L);
        assertThat(totalLocked).isEqualTo(1 + 3);
        assertThat(TenantContext.isSet()).isFalse();
    }
}
