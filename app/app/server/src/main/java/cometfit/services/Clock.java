package cometfit.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local-day boundaries for "daily" summaries and goals (UC-1 "View Daily Summary & Goals"; zone: A-9). */
@Component
public class Clock {
    private final ZoneId zone;

    public Clock(@Value("${cometfit.zone}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() { return zone; }

    public LocalDate today() { return LocalDate.now(zone); }

    public Instant startOf(LocalDate d) { return d.atStartOfDay(zone).toInstant(); }

    /** Exclusive end of the day minus one nanosecond, for inclusive "between" queries. */
    public Instant endOf(LocalDate d) { return d.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1); }

    public LocalDate dateOf(Instant i) { return i.atZone(zone).toLocalDate(); }
}
