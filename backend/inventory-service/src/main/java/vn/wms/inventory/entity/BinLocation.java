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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

/**
 * Vị trí lưu trữ trong kho theo cấu trúc zone / rack / bin (khu - kệ - ô).
 * Mã vị trí (code) dạng ví dụ WH01-A-01-03, duy nhất toàn hệ thống.
 */
@Entity
@Table(name = "bin_locations")
@Getter
@Setter
@NoArgsConstructor
public class BinLocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 20)
    private String zone;

    @Column(nullable = false, length = 20)
    private String rack;

    @Column(nullable = false, length = 20)
    private String bin;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false)
    private boolean active = true;
}
