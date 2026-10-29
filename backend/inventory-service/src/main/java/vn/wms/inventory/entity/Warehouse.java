package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

/**
 * Kho hàng (Warehouse) - Quản lý danh mục đa kho, sức chứa và liên hệ (WMS-05).
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(name = "manager_name", length = 100)
    private String managerName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Sức chứa thiết kế của kho (ví dụ: số vị trí ô/kệ hoặc pallet tối đa). */
    @Column(nullable = false)
    private Integer capacity = 0;

    @Column(nullable = false)
    private boolean active = true;

    public Warehouse(String code, String name, String address, String managerName, String contactPhone, Integer capacity) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.managerName = managerName;
        this.contactPhone = contactPhone;
        this.capacity = capacity != null ? capacity : 0;
        this.active = true;
    }
}
