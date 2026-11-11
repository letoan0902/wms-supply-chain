package vn.wms.auth.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Thực thể Gói dịch vụ (Plan) trên nền tảng WMS SaaS.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @Column(nullable = false, length = 20)
    private String code; // FREE, TRIAL, PRO

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_monthly", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceYearly;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_warehouses", nullable = false)
    private Integer maxWarehouses;

    @Column(name = "max_skus", nullable = false)
    private Integer maxSkus;

    @Column(name = "max_ai_requests", nullable = false)
    private Integer maxAiRequests;

    @Column(name = "api_access", nullable = false)
    private Boolean apiAccess;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;
}
