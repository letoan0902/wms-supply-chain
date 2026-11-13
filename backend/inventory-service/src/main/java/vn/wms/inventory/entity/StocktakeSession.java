package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Đợt kiểm kê của một kho (Stocktake, WMS-14.1). Mã đợt duy nhất trong từng doanh nghiệp.
 * Phạm vi sản phẩm (nếu có) lưu dạng danh sách ID ngăn cách bởi dấu phẩy: khi có phạm vi sản phẩm,
 * vị trí chỉ bị đóng băng đối với các sản phẩm đó.
 */
@Entity
@Table(name = "stocktake_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uk_stocktake_sessions_company_code", columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class StocktakeSession extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StocktakeStatus status = StocktakeStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private StocktakeScopeType scopeType;

    /** Danh sách khu (zone) khi phạm vi là ZONES, ngăn cách bởi dấu phẩy. */
    @Column(name = "scope_zones", length = 500)
    private String scopeZones;

    /** Danh sách ID sản phẩm giới hạn phạm vi (ngăn cách bởi dấu phẩy); null nghĩa là mọi sản phẩm. */
    @Column(name = "scope_product_ids", length = 2000)
    private String scopeProductIds;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "started_by", length = 50)
    private String startedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_by", length = 50)
    private String finishedBy;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "manager_approved_by", length = 50)
    private String managerApprovedBy;

    @Column(name = "manager_approved_at")
    private Instant managerApprovedAt;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "closed_by", length = 50)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    public List<String> getZoneList() {
        if (scopeZones == null || scopeZones.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopeZones.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setZoneList(Collection<String> zones) {
        this.scopeZones = (zones == null || zones.isEmpty()) ? null : String.join(",", zones);
    }

    public Set<Long> getProductIdSet() {
        if (scopeProductIds == null || scopeProductIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopeProductIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setProductIdSet(Collection<Long> productIds) {
        this.scopeProductIds = (productIds == null || productIds.isEmpty())
                ? null
                : productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Đợt có giới hạn sản phẩm hay không. */
    public boolean isProductScoped() {
        return scopeProductIds != null && !scopeProductIds.isBlank();
    }

    /** Sản phẩm có thuộc phạm vi của đợt không (đợt không giới hạn sản phẩm thì mọi sản phẩm đều thuộc). */
    public boolean coversProduct(Long productId) {
        return !isProductScoped() || (productId != null && getProductIdSet().contains(productId));
    }
}
