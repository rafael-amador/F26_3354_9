package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    List<ActivityRecord> findByStudentIdAndTimestampBetweenOrderByTimestampDesc(Long studentId, Instant from, Instant to);
}
