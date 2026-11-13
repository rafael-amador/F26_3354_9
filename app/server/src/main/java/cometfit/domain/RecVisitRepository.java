package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecVisitRepository extends JpaRepository<RecVisit, Long> {
    List<RecVisit> findByStudentIdOrderByTimestampDesc(Long studentId);

    long countByStudentIdAndTimestampBetween(Long studentId, Instant from, Instant to);
}
