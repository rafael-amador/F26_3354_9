package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterEntryRepository extends JpaRepository<WaterEntry, Long> {
    List<WaterEntry> findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(Long studentId, Instant from, Instant to);
    long countByStudentId(Long studentId);
}
