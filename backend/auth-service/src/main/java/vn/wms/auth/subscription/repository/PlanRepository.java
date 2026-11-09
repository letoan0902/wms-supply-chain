package vn.wms.auth.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.auth.subscription.entity.Plan;

@Repository
public interface PlanRepository extends JpaRepository<Plan, String> {
}
