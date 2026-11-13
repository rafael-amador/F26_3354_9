package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealEntryRepository extends JpaRepository<MealEntry, Long> {
    List<MealEntry> findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(Long studentId, Instant from, Instant to);
}
