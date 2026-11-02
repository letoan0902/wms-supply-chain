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

import java.math.BigDecimal;

/**
 * Vị trí lưu trữ trong kho theo cấu trúc 4 cấp: Warehouse - Zone - Rack - Bin (Kho - Khu - Kệ - Ô) (WMS-06).
 * Mã vị trí (code) định dạng WHxx-ZONE-Rxx-Bxx, duy nhất toàn hệ thống.
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

    /** Khu vực trong kho (ví dụ: ZA, ZB, KHOLANH). */
    @Column(nullable = false, length = 20)
    private String zone;

    /** Dãy kệ (ví dụ: R01, R02). */
    @Column(nullable = false, length = 20)
    private String rack;

    /** Ô kệ (ví dụ: B01, B02). */
    @Column(nullable = false, length = 20)
    private String bin;

    /** Mã vị trí duy nhất (ví dụ: WH01-ZA-R01-B01). */
    @Column(nullable = false, unique = true, length = 80)
    private String code;

    /** Tải trọng tối đa (kg). */
    @Column(name = "max_weight", precision = 10, scale = 2)
    private BigDecimal maxWeight = BigDecimal.valueOf(1000.00);

    /** Trạng thái vị trí: EMPTY (Trống), OCCUPIED (Đang có hàng), MAINTENANCE (Bảo trì/Khóa). */
    @Column(nullable = false, length = 20)
    private String status = "EMPTY";

    @Column(nullable = false)
    private boolean active = true;

    public BinLocation(Warehouse warehouse, String zone, String rack, String bin, BigDecimal maxWeight) {
        this.warehouse = warehouse;
        this.zone = zone != null ? zone.trim().toUpperCase() : "";
        this.rack = rack != null ? rack.trim().toUpperCase() : "";
        this.bin = bin != null ? bin.trim().toUpperCase() : "";
        this.code = buildCode(warehouse.getCode(), this.zone, this.rack, this.bin);
        this.maxWeight = maxWeight != null ? maxWeight : BigDecimal.valueOf(1000.00);
        this.status = "EMPTY";
        this.active = true;
    }

    /**
     * Quy ước sinh mã vị trí 4 cấp chuẩn hóa: WAREHOUSE-ZONE-RACK-BIN
     */
    public static String buildCode(String warehouseCode, String zone, String rack, String bin) {
        return String.format("%s-%s-%s-%s",
                warehouseCode != null ? warehouseCode.trim().toUpperCase() : "WH",
                zone != null ? zone.trim().toUpperCase() : "Z0",
                rack != null ? rack.trim().toUpperCase() : "R0",
                bin != null ? bin.trim().toUpperCase() : "B0");
    }
}
