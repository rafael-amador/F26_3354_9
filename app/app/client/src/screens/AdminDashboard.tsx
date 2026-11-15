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
import { Banner, Button, Card, colors, Row, s, Stat } from '../ui';

const REFRESH_MS = 10_000;

export default function AdminDashboard({ onLogout }: { onLogout: () => void }) {
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

  return (
    <ScrollView contentContainerStyle={{ padding: 16, maxWidth: 900, width: '100%', alignSelf: 'center' }}>
      <Row style={{ justifyContent: 'space-between', marginBottom: 8 }}>
        <View>
          <Text style={s.h1}>CometFit Admin Dashboard</Text>
          {access ? (
            <Text style={s.muted}>
              {access.username} · {access.adminRole} · permissions: {access.permissions.join(', ')}
            </Text>
          ) : null}
        </View>
        <View style={{ width: 120 }}>
          <Button kind="secondary" label="Sign out" onPress={onLogout} />
        </View>
      </Row>
      {error ? <Banner kind="error">{error}</Banner> : null}

      {report ? (
        <Card title={`Server uptime (last ${report.rangeHours} h)`}>
          <Row>
            <Stat label="Overall uptime" value={`${report.overallUptimePercent.toFixed(2)}%`} />
            <Stat label="Current status" value={report.allUp ? 'All up' : 'Degraded'} />
            <Stat label="Check interval" value={`${report.checkIntervalSec}s`} />
          </Row>
          <Row>
            {[1, 24, 168].map((h) => (
              <View key={h} style={{ flex: 1 }}>
                <Button kind={hours === h ? 'primary' : 'secondary'} label={h === 168 ? '7 days' : `${h} h`} onPress={() => setHours(h)} />
              </View>
            ))}
            <View style={{ flex: 1 }}>
              <Button kind="secondary" label="Check now" onPress={() => run('check', api.checkNow)} busy={busy === 'check'} />
            </View>
          </Row>
          <View style={{ marginTop: 8 }}>
            {report.services.map((sv) => (
              <View key={sv.serviceName} style={[s.listItem, { flexDirection: 'row', alignItems: 'center' }]}>
                <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: sv.up ? colors.good : colors.bad, marginRight: 8 }} />
                <View style={{ flex: 1 }}>
                  <Text style={s.body}>{sv.serviceName}</Text>
                  <Text style={s.muted}>
                    {sv.up ? 'UP' : 'DOWN'} · {sv.uptimePercent.toFixed(2)}% · {sv.lastResponseMs} ms · checked {shortTime(sv.lastCheckedAt)} · {sv.checks} checks
                  </Text>
                </View>
                <View style={{ width: 150 }}>
                  <Button
                    kind={sv.up ? 'secondary' : 'primary'}
                    label={sv.up ? 'Simulate outage' : 'End outage'}
                    onPress={() => run('o-' + sv.serviceName, () => api.simulateOutage(sv.serviceName, sv.up))}
                    busy={busy === 'o-' + sv.serviceName}
                  />
                </View>
              </View>
            ))}
            <Text style={s.muted}>"Simulate outage" is a demo stub that makes a component fail its health check.</Text>
          </View>
        </Card>
      ) : null}

      <Card title="Downtime alerts">
        {alerts.length === 0 ? <Text style={s.muted}>No alerts.</Text> : null}
        {alerts.slice(0, 10).map((a, i) => (
          <Banner key={a.raisedAt + i} kind={a.recovered ? 'good' : 'error'}>
            {shortTime(a.raisedAt)} — {a.message}
          </Banner>
        ))}
      </Card>

      {report ? (
        <Card title="Incident history">
          {report.incidents.length === 0 ? <Text style={s.muted}>No incidents in this period.</Text> : null}
          {report.incidents.slice(0, 20).map((i, idx) => (
            <Text key={i.serviceName + i.startedAt + idx} style={s.body}>
              {i.serviceName}: down from {shortTime(i.startedAt)} {i.ongoing ? '(ongoing)' : `to ${shortTime(i.endedAt)}`} · {formatDuration(i.durationSec)}
            </Text>
          ))}
        </Card>
      ) : null}

      <Card title="App modules">
        <Text style={s.muted}>
          Turning a module off shows students a maintenance notice for that part of the app; everything else keeps working.
        </Text>
        {modules.map((m) => (
          <View key={m.moduleId} style={[s.listItem, { flexDirection: 'row', alignItems: 'center' }]}>
            <View style={{ flex: 1 }}>
              <Text style={s.body}>{m.name}</Text>
              <Text style={[s.muted, { color: m.enabled ? colors.good : colors.bad }]}>{m.enabled ? 'Enabled' : 'Disabled (maintenance)'}</Text>
            </View>
            <View style={{ width: 130 }}>
              <Button
                kind={m.enabled ? 'danger' : 'primary'}
                label={m.enabled ? 'Disable' : 'Re-enable'}
                onPress={() => run('m-' + m.moduleId, () => (m.enabled ? api.disableModule(m.moduleId) : api.enableModule(m.moduleId)))}
                busy={busy === 'm-' + m.moduleId}
                testID={`toggle-${m.moduleId}`}
              />
            </View>
          </View>
        ))}
      </Card>
    </ScrollView>
  );
}
