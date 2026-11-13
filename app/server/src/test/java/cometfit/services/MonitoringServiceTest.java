package cometfit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import cometfit.domain.UptimeRecord;

/** FR-1 "uptime % and incident history" (SD-4): incident detection from UptimeRecords. */
class MonitoringServiceTest {
    private static UptimeRecord r(long sec, boolean up) {
        return new UptimeRecord(Instant.ofEpochSecond(sec), "db", up, 1);
    }

    @Test
    void consecutiveDownChecksFormOneIncident() {
        // newest first, as returned by the repository
        List<UptimeRecord> recs = List.of(r(500, true), r(400, false), r(300, false), r(200, true), r(100, true));
        List<MonitoringService.Incident> inc = MonitoringService.incidentsOf("db", recs);
        assertThat(inc).hasSize(1);
        assertThat(inc.get(0).startedAt()).isEqualTo(Instant.ofEpochSecond(300));
        assertThat(inc.get(0).endedAt()).isEqualTo(Instant.ofEpochSecond(500));
        assertThat(inc.get(0).durationSec()).isEqualTo(200);
        assertThat(inc.get(0).ongoing()).isFalse();
    }

    @Test
    void downAtTheLatestCheckIsAnOngoingIncident() {
        List<MonitoringService.Incident> inc = MonitoringService.incidentsOf("db", List.of(r(300, false), r(200, true)));
        assertThat(inc).hasSize(1);
        assertThat(inc.get(0).ongoing()).isTrue();
        assertThat(inc.get(0).endedAt()).isNull();
    }

    @Test
    void allUpHasNoIncidents() {
        assertThat(MonitoringService.incidentsOf("db", List.of(r(2, true), r(1, true)))).isEmpty();
    }
}
