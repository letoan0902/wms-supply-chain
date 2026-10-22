package vn.wms.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.auth.entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
