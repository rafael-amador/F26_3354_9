// Dashboard tab — TD-4 §2 "daily summary and UREC check-in/streak on Dashboard"; layout of
// spec/05-ui-prototype/2-dashboard.png: dark-green "CURRENT STREAK" card with orange progress segments and the next
// milestone, "This week" M..S circles, orange "Check in at UREC" button with its note, "Total visits" / "Personal best".
//   UREC check-in = UC-2 / SD-2 "swipe student card -> recordVisit -> awardPoints" (FR-7) through the stub card reader.
//   Streak / week / best are computed from the visit history (GET /api/activity/visits), A-49.
//   Daily summary = UC-1 "View Daily Summary & Goals" (FR-2 calories, FR-6 water + low-intake message, FR-4 steps).
// Each half shows its own maintenance notice when its module is off (FR-5, A-45).
import React, { useState } from 'react';
import { RefreshControl, ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { progress, streakStats, unpublishedMacrosNote } from '../logic';
import { Banner, Button, Card, colors, fmt, MaintenanceNotice, ProgressBar, Row, s, Stat } from '../ui';
import { useAsync } from '../useAsync';

export default function DashboardScreen({ refreshKey, onChanged }: { refreshKey: number; onChanged: () => void }) {
  const visitsQ = useAsync(() => api.visitHistory(), [refreshKey]);
  const summaryQ = useAsync(() => api.summary(), [refreshKey]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ kind: 'good' | 'error'; text: string } | null>(null);

  async function checkIn() {
    setBusy(true);
    try {
      const r = await api.simulateSwipe();
      setMsg({ kind: 'good', text: `${r.message}. Balance: ${r.balance} points.` });
      visitsQ.reload();
      onChanged();
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    } finally {
      setBusy(false);
    }
  }

  const reload = () => {
    visitsQ.reload();
    summaryQ.reload();
  };
  const st = visitsQ.data ? streakStats(visitsQ.data.visits.map((v) => v.timestamp)) : null;
  const sum = summaryQ.data;

  return (
    <ScrollView refreshControl={<RefreshControl refreshing={visitsQ.loading || summaryQ.loading} onRefresh={reload} />}>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}

      {visitsQ.error?.isMaintenance ? (
        <MaintenanceNotice name="Activity & UREC Visits" />
      ) : visitsQ.error ? (
        <Banner kind="error">{visitsQ.error.message}</Banner>
      ) : st ? (
        <>
          <View style={d.streak} testID="streak-card">
            <View style={d.streakTop}>
              <Text style={d.streakLabel}>CURRENT STREAK</Text>
              <View style={d.pill}><Text style={d.pillText}>UREC</Text></View>
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 8 }}>
              <Text style={d.streakNumber} testID="streak-days">{st.current}</Text>
              <Text style={d.streakUnit}>{st.current === 1 ? 'day' : 'days'}</Text>
            </View>
            <Text style={d.streakText}>
              {st.current === 0 ? 'Check in today to start a streak.' : st.checkedInToday ? 'Checked in today. See you tomorrow.' : 'Keep your momentum going.'}
            </Text>
            <View style={d.segments}>
              {Array.from({ length: 5 }, (_, i) => (
                <View key={i} style={[d.segment, { backgroundColor: i < st.current - (st.nextMilestone - 5) ? colors.accent : 'rgba(255,255,255,0.25)' }]} />
              ))}
            </View>
            <Text style={d.milestone}>Next milestone: {st.nextMilestone} days</Text>
          </View>

          <Card title="This week" right={`${st.visitsThisWeek} ${st.visitsThisWeek === 1 ? 'visit' : 'visits'}`}>
            <View style={d.weekRow}>
              {st.week.map((w, i) => (
                <View key={i} style={{ alignItems: 'center', gap: 6 }}>
                  <Text style={s.small}>{w.label}</Text>
                  <View style={[d.circle, w.state === 'done' ? d.circleDone : w.state === 'today' ? d.circleToday : d.circleEmpty]}>
                    <Text style={[d.circleText, w.state === 'done' ? { color: '#FFF' } : w.state === 'today' ? { color: colors.heading } : null]}>
                      {w.state === 'done' ? '✓' : w.state === 'today' ? String(st.current + 1) : '–'}
                    </Text>
                  </View>
                </View>
              ))}
            </View>
          </Card>

          <View style={{ marginTop: 4 }}>
            <Button big kind="accent" label={st.checkedInToday ? 'Check in at UREC again →' : 'Check in at UREC →'} onPress={checkIn} busy={busy} testID="checkin" />
          </View>
          <Text style={[s.small, { textAlign: 'center', marginTop: 6, lineHeight: 17 }]}>
            Check in when you arrive at the UREC Center.{'\n'}One check-in per day counts toward your streak.
          </Text>
          <Text style={[s.small, { textAlign: 'center', marginTop: 2 }]}>(Card reader simulated in this build.)</Text>

          <View style={d.totals}>
            <View style={{ flex: 1 }}>
              <Text style={d.totalNumber}>{fmt(st.total)}</Text>
              <Text style={s.muted}>Total visits</Text>
            </View>
            <View style={d.divider} />
            <View style={{ flex: 1, paddingLeft: 24 }}>
              <Text style={d.totalNumber}>{st.best} {st.best === 1 ? 'day' : 'days'}</Text>
              <Text style={s.muted}>Personal best</Text>
            </View>
          </View>
        </>
      ) : null}

      {/* UC-1 "View Daily Summary & Goals" */}
      {summaryQ.error?.isMaintenance ? (
        <MaintenanceNotice name="Nutrition & Hydration" />
      ) : summaryQ.error ? (
        <Banner kind="error">{summaryQ.error.message}</Banner>
      ) : sum ? (
        <Card title="Today" right={sum.date}>
          {sum.messages.map((m) => (
            <Banner key={m} kind={m.startsWith('Water goal reached') ? 'good' : 'warn'}>{m}</Banner>
          ))}
          <Text style={d.rowLabel}>Calories <Text style={s.muted}>{fmt(sum.calories)} / {fmt(sum.calorieGoal)} cal</Text></Text>
          <ProgressBar value={progress(sum.calories, sum.calorieGoal)} color={colors.food} track={colors.foodBg} />
          <Text style={d.rowLabel}>Water <Text style={s.muted}>{fmt(sum.waterMl)} / {fmt(sum.waterGoalMl)} ml · {(sum.waterMl / sum.bottleSizeMl).toFixed(1)} bottles</Text></Text>
          <ProgressBar value={progress(sum.waterMl, sum.waterGoalMl)} color={colors.water} track={colors.waterBg} />
          <Text style={d.rowLabel}>Steps <Text style={s.muted}>{fmt(sum.steps)} / {fmt(sum.stepGoal)}</Text></Text>
          <ProgressBar value={progress(sum.steps, sum.stepGoal)} color={colors.primary} />
          <Row style={{ marginTop: 6 }}>
            <Stat label="Protein" value={`${sum.proteinG} g`} />
            <Stat label="Carbs" value={`${sum.carbsG} g`} />
            <Stat label="Fat" value={`${sum.fatG} g`} />
            <Stat label="Heart rate" value={sum.latestHeartRate ? `${sum.latestHeartRate}` : '–'} />
          </Row>
          {/* TD-6 §3 / A-55: unpublished UTD Dining macros are counted as 0 g; say so */}
          {unpublishedMacrosNote(sum.mealsWithUnpublishedMacros) ? (
            <Text style={[s.small, { fontStyle: 'italic' }]}>{unpublishedMacrosNote(sum.mealsWithUnpublishedMacros)}</Text>
          ) : null}
          {!sum.latestHeartRate ? <Text style={s.small}>Connect a smart device in Activities to sync steps and heart rate.</Text> : null}
        </Card>
      ) : null}
    </ScrollView>
  );
}

const d = {
  streak: { backgroundColor: colors.primary, borderRadius: 20, padding: 20, marginBottom: 12 },
  streakTop: { flexDirection: 'row' as const, justifyContent: 'space-between' as const, alignItems: 'center' as const },
  streakLabel: { color: 'rgba(255,255,255,0.85)', fontSize: 12, fontWeight: '700' as const, letterSpacing: 0.6 },
  pill: { backgroundColor: 'rgba(255,255,255,0.15)', borderRadius: 12, paddingHorizontal: 16, paddingVertical: 4 },
  pillText: { color: '#FFF', fontSize: 12, fontWeight: '700' as const },
  streakNumber: { color: '#FFF', fontSize: 64, fontWeight: '800' as const, lineHeight: 70 },
  streakUnit: { color: '#FFF', fontSize: 26, fontWeight: '500' as const, marginLeft: 12 },
  streakText: { color: 'rgba(255,255,255,0.92)', fontSize: 15, marginTop: 4 },
  segments: { flexDirection: 'row' as const, gap: 8, marginTop: 18 },
  segment: { flex: 1, height: 6, borderRadius: 3 },
  milestone: { color: 'rgba(255,255,255,0.85)', fontSize: 12, marginTop: 12 },
  weekRow: { flexDirection: 'row' as const, justifyContent: 'space-between' as const },
  circle: { width: 36, height: 36, borderRadius: 18, alignItems: 'center' as const, justifyContent: 'center' as const },
  circleDone: { backgroundColor: colors.primary },
  circleToday: { backgroundColor: '#FDF1E6', borderWidth: 2, borderColor: colors.accent },
  circleEmpty: { backgroundColor: colors.chip },
  circleText: { fontSize: 14, fontWeight: '700' as const, color: colors.muted },
  totals: { flexDirection: 'row' as const, alignItems: 'center' as const, marginVertical: 18 },
  totalNumber: { fontSize: 24, fontWeight: '700' as const, color: colors.heading },
  divider: { width: 1, alignSelf: 'stretch' as const, backgroundColor: colors.border },
  rowLabel: { fontSize: 15, fontWeight: '600' as const, color: colors.heading, marginTop: 6 },
};
