package ro.mathlms.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {

    Optional<TotpRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    long countByUserIdAndUsedAtIsNull(Long userId);

    @Modifying
    @Query("delete from TotpRecoveryCode c where c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
