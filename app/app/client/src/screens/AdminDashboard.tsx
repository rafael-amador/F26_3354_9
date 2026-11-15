// UC-4 Monitor and Control System — SD-4 "AdminDashboard (View)":
//   login (password + TOTP) -> authorize(admin, permissions) -> access granted          (UC-4 "Authorize Admin")
//   view uptime metrics -> getUptimeReport() -> uptime % and incident history            (FR-1)
//   disable module (e.g. Friends) -> disableModule(moduleId) ; re-enable -> enableModule  (FR-5)
//   alt [service down] send downtime alert                                                (UC-4 "Raise Downtime Alert")
import React, { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { formatDuration, shortTime } from '../logic';
import type { AdminAccess, DowntimeAlert, ModuleState, UptimeReport } from '../types';
import { Banner, Button, Card, colors, Row, s, ScreenHeader, Segmented } from '../ui';

const REFRESH_MS = 10_000;

export default function AdminDashboard({ onLogout, name }: { onLogout: () => void; name?: string }) {
  const [access, setAccess] = useState<AdminAccess | null>(null);
  const [report, setReport] = useState<UptimeReport | null>(null);
  const [alerts, setAlerts] = useState<DowntimeAlert[]>([]);
  const [modules, setModules] = useState<ModuleState[]>([]);
  const [hours, setHours] = useState(24);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  async function load() {
    try {
      const [a, r, al, m] = await Promise.all([api.adminAccess(), api.uptime(hours), api.alerts(), api.adminModules()]);
      setAccess(a);
      setReport(r);
      setAlerts(al);
      setModules(m);
      setError(null);
    } catch (e) {
      setError((e as ApiError).message);
    }
  }

  useEffect(() => {
    load();
    const t = setInterval(load, REFRESH_MS);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hours]);

  async function run(key: string, action: () => Promise<unknown>) {
    setBusy(key);
    try {
      await action();
      await load();
    } catch (e) {
      setError((e as ApiError).message);
    } finally {
      setBusy(null);
    }
  }

  // TD-4 §2: "The developer dashboard (UC-4) keeps its own screen in the same visual style" — prototype header with
  // subtitle and initials avatar, dark-green summary card (like the Dashboard streak card), white cards, pill switch.
  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 24, maxWidth: 900, width: '100%', alignSelf: 'center' }}>
      <ScreenHeader brand title="Developer dashboard" subtitle="Uptime, alerts and app modules." name={name ?? access?.username ?? 'dev'} />
      <View style={{ paddingHorizontal: 20 }}>
      {access ? (
        <Text style={[s.small, { marginBottom: 10 }]}>
          {access.username} · {access.adminRole} · permissions: {access.permissions.join(', ')}
        </Text>
      ) : null}
      {error ? <Banner kind="error">{error}</Banner> : null}

      {report ? (
        <>
          <View style={ad.hero} testID="uptime-card">
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={ad.heroLabel}>SERVER UPTIME · LAST {report.rangeHours === 168 ? '7 DAYS' : `${report.rangeHours} H`}</Text>
              <View style={[ad.pill, { backgroundColor: report.allUp ? 'rgba(255,255,255,0.15)' : colors.accent }]}>
                <Text style={ad.pillText}>{report.allUp ? 'ALL UP' : 'DEGRADED'}</Text>
              </View>
            </View>
            <Text style={ad.heroNumber}>{report.overallUptimePercent.toFixed(2)}%</Text>
            <Text style={ad.heroText}>Health checks every {report.checkIntervalSec}s across {report.services.length} components.</Text>
          </View>
          <Row style={{ marginBottom: 4 }}>
            <Segmented
              options={[{ id: '1', label: '1 h' }, { id: '24', label: '24 h' }, { id: '168', label: '7 days' }]}
              value={String(hours) as '1' | '24' | '168'}
              onChange={(v) => setHours(Number(v))}
            />
            <View style={{ flex: 1 }} />
            <View style={{ width: 130, marginBottom: 14 }}>
              <Button kind="secondary" label="Check now" onPress={() => run('check', api.checkNow)} busy={busy === 'check'} />
            </View>
          </Row>
          <Card title="Components">
            {report.services.map((sv, i) => (
              <View key={sv.serviceName} style={[ad.row, i < report.services.length - 1 && ad.line, { flexWrap: 'wrap' }]}>
                <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: sv.up ? colors.good : colors.bad, marginRight: 10 }} />
                <View style={{ flex: 1, minWidth: 200 }}>
                  <Text style={ad.title}>{sv.serviceName}</Text>
                  <Text style={s.small}>
                    {sv.up ? 'UP' : 'DOWN'} · {sv.uptimePercent.toFixed(2)}% · {sv.lastResponseMs} ms · checked {shortTime(sv.lastCheckedAt)} · {sv.checks} checks
                  </Text>
                </View>
                <View style={{ width: 150 }}>
                  <Button
                    kind={sv.up ? 'secondary' : 'accent'}
                    label={sv.up ? 'Simulate outage' : 'End outage'}
                    onPress={() => run('o-' + sv.serviceName, () => api.simulateOutage(sv.serviceName, sv.up))}
                    busy={busy === 'o-' + sv.serviceName}
                  />
                </View>
              </View>
            ))}
            <Text style={[s.small, { marginTop: 6 }]}>"Simulate outage" is a demo stub that makes a component fail its health check.</Text>
          </Card>
        </>
      ) : null}

      <Card title="Downtime alerts" right={`${alerts.length}`}>
        {alerts.length === 0 ? <Text style={s.muted}>No alerts.</Text> : null}
        {alerts.slice(0, 10).map((a, i) => (
          <Banner key={a.raisedAt + i} kind={a.recovered ? 'good' : 'error'}>
            {shortTime(a.raisedAt)} — {a.message}
          </Banner>
        ))}
      </Card>

      {report ? (
        <Card title="Incident history" right={`${report.incidents.length}`}>
          {report.incidents.length === 0 ? <Text style={s.muted}>No incidents in this period.</Text> : null}
          {report.incidents.slice(0, 20).map((i, idx, all) => (
            <View key={i.serviceName + i.startedAt + idx} style={[ad.row, idx < all.length - 1 && ad.line]}>
              <View style={{ flex: 1 }}>
                <Text style={ad.title}>{i.serviceName}</Text>
                <Text style={s.small}>
                  down from {shortTime(i.startedAt)} {i.ongoing ? '(ongoing)' : `to ${shortTime(i.endedAt)}`}
                </Text>
              </View>
              <Text style={[ad.title, { color: i.ongoing ? colors.bad : colors.heading }]}>{formatDuration(i.durationSec)}</Text>
            </View>
          ))}
        </Card>
      ) : null}

      <Card title="App modules">
        <Text style={s.muted}>
          Turning a module off shows students a maintenance notice for that part of the app; everything else keeps working.
        </Text>
        {modules.map((m, i) => (
          <View key={m.moduleId} style={[ad.row, i < modules.length - 1 && ad.line]}>
            <View style={{ flex: 1 }}>
              <Text style={ad.title}>{m.name}</Text>
              <Text style={[s.small, { color: m.enabled ? colors.good : colors.bad }]}>{m.enabled ? 'Enabled' : 'Disabled (maintenance)'}</Text>
            </View>
            <View style={{ width: 130 }}>
              <Button
                kind={m.enabled ? 'outlineDanger' : 'primary'}
                label={m.enabled ? 'Disable' : 'Re-enable'}
                onPress={() => run('m-' + m.moduleId, () => (m.enabled ? api.disableModule(m.moduleId) : api.enableModule(m.moduleId)))}
                busy={busy === 'm-' + m.moduleId}
                testID={`toggle-${m.moduleId}`}
              />
            </View>
          </View>
        ))}
      </Card>

      <Button big kind="outlineDanger" label="Log out" onPress={onLogout} testID="logout" />
      </View>
    </ScrollView>
  );
}

const ad = {
  hero: { backgroundColor: colors.primary, borderRadius: 20, padding: 20, marginBottom: 12 },
  heroLabel: { color: 'rgba(255,255,255,0.85)', fontSize: 12, fontWeight: '700' as const, letterSpacing: 0.6 },
  pill: { borderRadius: 12, paddingHorizontal: 14, paddingVertical: 4 },
  pillText: { color: '#FFF', fontSize: 12, fontWeight: '700' as const },
  heroNumber: { color: '#FFF', fontSize: 52, fontWeight: '800' as const, marginTop: 8 },
  heroText: { color: 'rgba(255,255,255,0.9)', fontSize: 14, marginTop: 2 },
  row: { flexDirection: 'row' as const, alignItems: 'center' as const, paddingVertical: 10 },
  line: { borderBottomWidth: 1, borderBottomColor: colors.border },
  title: { fontSize: 15, fontWeight: '600' as const, color: colors.heading },
};
