package vn.wms.auth.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByCompanyId(Long companyId);

    List<Subscription> findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus status, Instant now);

    List<Subscription> findAllByStatusAndExpiresAtBefore(SubscriptionStatus status, Instant now);
}
