package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findTop20ByStudentIdOrderByTimestampDesc(Long studentId);

    boolean existsByStudentIdAndReasonAndTimestampBetween(Long studentId, String reason, Instant from, Instant to);
}
