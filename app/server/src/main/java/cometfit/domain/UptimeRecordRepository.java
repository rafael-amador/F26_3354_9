package cometfit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UptimeRecordRepository extends JpaRepository<UptimeRecord, Long> {
    List<UptimeRecord> findByCheckedAtAfterOrderByCheckedAtDesc(Instant after);

    @Modifying
    @Query("delete from UptimeRecord r where r.checkedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
