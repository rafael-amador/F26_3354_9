package cometfit.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    Optional<TrustedDevice> findByTokenHash(String tokenHash);

    long countByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from TrustedDevice t where t.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("delete from TrustedDevice t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
