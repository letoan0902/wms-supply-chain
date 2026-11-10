package vn.wms.auth.subscription.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.auth.company.entity.Company;
import vn.wms.common.persistence.AuditableEntity;

import java.time.Instant;

/**
 * Thực thể Thuê bao / Đăng ký dịch vụ (Subscription) của một doanh nghiệp.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_code", referencedColumnName = "code", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Kiểm tra xem thuê bao đã quá hạn hay chưa.
     */
    public boolean isExpired(Instant now) {
        if ("FREE".equals(plan.getCode())) {
            return false;
        }
        if (trialEndsAt != null && now.isAfter(trialEndsAt)) {
            return true;
        }
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return true;
        }
        return false;
    }
}
