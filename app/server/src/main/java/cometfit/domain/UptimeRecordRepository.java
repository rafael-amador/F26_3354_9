package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UptimeRecordRepository extends JpaRepository<UptimeRecord, Long> {
    List<UptimeRecord> findByCheckedAtAfterOrderByCheckedAtDesc(Instant after);
}
