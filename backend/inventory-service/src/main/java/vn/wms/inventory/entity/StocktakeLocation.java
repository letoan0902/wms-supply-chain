package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

/**
 * Một vị trí ô/kệ thuộc phạm vi đợt kiểm kê. {@code frozenBinLocationId} bằng ID vị trí khi vị trí đang bị
 * đóng băng và bằng null khi đã mở; ràng buộc duy nhất (company_id, frozen_bin_location_id) bảo đảm một vị trí
 * chỉ bị một đợt đóng băng tại một thời điểm.
 */
@Entity
@Table(name = "stocktake_locations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stocktake_locations_session_bin", columnNames = {"company_id", "session_id", "bin_location_id"}),
        @UniqueConstraint(name = "uk_stocktake_locations_frozen_bin", columnNames = {"company_id", "frozen_bin_location_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StocktakeLocation extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_location_id", nullable = false)
    private BinLocation binLocation;

    @Column(name = "frozen_bin_location_id")
    private Long frozenBinLocationId;

    public StocktakeLocation(StocktakeSession session, BinLocation binLocation) {
        this.session = session;
        this.binLocation = binLocation;
    }

    public boolean isFrozen() {
        return frozenBinLocationId != null;
    }
}
