package cometfit.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** TD-5 (2) sign-in challenges. */
public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {
    Optional<LoginChallenge> findByTokenHash(String tokenHash);

    long countByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from LoginChallenge c where c.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
