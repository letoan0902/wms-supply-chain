package vn.wms.auth.account.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.account.entity.AccountToken;
import vn.wms.auth.account.entity.AccountTokenType;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    /** Tra mã theo bản băm và khóa dòng để hai lần bấm cùng lúc không cùng dùng được một mã. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountToken t WHERE t.tokenHash = :tokenHash")
    Optional<AccountToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** Vô hiệu các mã kích hoạt chưa dùng của một doanh nghiệp (khi phát mã mới hoặc đã kích hoạt xong). */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now WHERE t.companyId = :companyId AND t.type = :type "
            + "AND t.usedAt IS NULL")
    int revokeOutstandingForCompany(@Param("companyId") Long companyId, @Param("type") AccountTokenType type,
                                    @Param("now") Instant now);

    /** Vô hiệu các mã chưa dùng của một tài khoản (khi phát mã đặt lại mới hoặc đã đặt lại xong). */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.type = :type "
            + "AND t.usedAt IS NULL")
    int revokeOutstandingForUser(@Param("userId") Long userId, @Param("type") AccountTokenType type,
                                 @Param("now") Instant now);
}
