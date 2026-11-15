// UC-2 Track Activity and Rec Center Visits — SD-2:
//   Connect and sync device: choose Connect Device -> request health permissions -> grant permission ->
//     linkDevice(deviceId) -> [server pairs] ; loop [periodic sync] pushSteps(steps, heartRate, timestamp)   (FR-4)
//   Rec center check-in: swipe student card -> recordVisit -> awardPoints -> "visit logged, points earned" ->
//     "show visit history and points"                                                                       (FR-7)
// TD-4: layout of spec/05-ui-prototype/5-activities.png — big dark-green action button, a three-number stats card,
// "Completed" list with values on the right. "Upcoming classes" has no FR/UC and no data, so it is left out (TD-4 §3,
// A-50); the "+ Log exercise" button syncs the smart device (A-50).
import React, { useEffect, useRef, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { clockTime, progress, shortTime } from '../logic';
import { FakeSmartDevice } from '../smartDevice';
import type { DeviceLink } from '../types';
import { Banner, Button, Card, colors, fmt, MaintenanceNotice, ProgressBar, Row, s, Stat, Toggle } from '../ui';
import { useAsync } from '../useAsync';

const SYNC_EVERY_MS = 60_000;

export default function ActivityScreen({ onChanged }: { onChanged: () => void }) {
  const devicesQ = useAsync(() => api.devices(), []);
  const recordsQ = useAsync(() => api.activityRecords(), []);
  const visitsQ = useAsync(() => api.visitHistory(), []);
  const summaryQ = useAsync(() => api.summary().catch(() => null), []);
  const [asking, setAsking] = useState(false);
  const [msg, setMsg] = useState<{ kind: 'good' | 'error' | 'info'; text: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [autoSync, setAutoSync] = useState(false);
  const devicesRef = useRef<Record<string, FakeSmartDevice>>({});

  const links = devicesQ.data ?? [];

  function adapterFor(link: DeviceLink) {
    if (!devicesRef.current[link.deviceId]) {
      devicesRef.current[link.deviceId] = new FakeSmartDevice(link.deviceId, link.platform, link.linkId * 7919);
    }
    return devicesRef.current[link.deviceId];
  }

  async function sync(link: DeviceLink, quiet = false) {
    const reading = adapterFor(link).readSinceLastSync();
    setBusy('sync');
    try {
      const r = await api.pushSteps({ deviceId: link.deviceId, steps: reading.steps, heartRate: reading.heartRate, timestamp: reading.timestamp });
      const pts = r.pointsAwarded ? ` Step goal reached: +${r.pointsAwarded.amount} points!` : '';
      if (!quiet || pts) setMsg({ kind: 'good', text: `Synced ${reading.steps} steps, ${reading.heartRate} bpm. ${r.stepsToday} steps today.${pts}` });
      recordsQ.reload();
      visitsQ.reload();
      summaryQ.reload();
      onChanged();
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    } finally {
      setBusy(null);
    }
  }

  // SD-2 loop [periodic sync]
  useEffect(() => {
    if (!autoSync || links.length === 0) return;
    const t = setInterval(() => links.forEach((l) => sync(l, true)), SYNC_EVERY_MS);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoSync, links.map((l) => l.deviceId).join(',')]);

  if (devicesQ.error?.isMaintenance) return <MaintenanceNotice name="Activity & UREC Visits" />;

  async function grantAndLink(granted: boolean) {
    setAsking(false);
    if (!granted) {
      setMsg({ kind: 'info', text: 'Health permission was not granted, so no device was connected.' });
      return;
    }
    setBusy('link');
    const deviceId = `SIM-WATCH-${Math.floor(1000 + Math.random() * 9000)}`;
    try {
      const link = await api.linkDevice({ deviceId, deviceType: 'watch', platform: 'simulated', healthPermissionGranted: true });
      setMsg({ kind: 'good', text: `Connected ${link.deviceId}. Tap "Sync now" or turn on automatic sync.` });
      devicesQ.reload();
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    } finally {
      setBusy(null);
    }
  }

  async function unlink(link: DeviceLink) {
    try {
      await api.unlinkDevice(link.deviceId);
      devicesQ.reload();
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    }
  }

  async function swipe() {
    setBusy('swipe');
    try {
      const r = await api.simulateSwipe();
      setMsg({ kind: 'good', text: `${r.message}. Balance: ${r.balance} points.` });
      visitsQ.reload();
      onChanged();
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    } finally {
      setBusy(null);
    }
  }

  const sum = summaryQ.data;
  const hist = visitsQ.data;
  const records = recordsQ.data ?? [];
  const syncAll = () => links.forEach((l) => sync(l));
  return (
    <ScrollView>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}

      {/* Prototype "+ Log exercise": activity is logged by syncing the smart device (FR-4, SD-2), A-50 */}
      <Button
        big
        label={links.length ? '+ Sync activity from device' : '+ Connect smart device'}
        onPress={links.length ? syncAll : () => setAsking(true)}
        busy={busy === 'sync' || busy === 'link'}
        testID={links.length ? 'sync' : 'connect-device'}
      />

      {/* UC-2 «include» Grant Health Permissions (SD-2 "request health permissions -> grant permission") */}
      {asking ? (
        <View style={[s.card, { backgroundColor: colors.soft, marginTop: 8 }]}>
          <Text style={[s.body, { fontWeight: '700' }]}>Allow CometFit to read health data?</Text>
          <Text style={s.body}>CometFit would like to read your steps and heart rate from your device.</Text>
          <Text style={s.muted}>(Simulated system prompt — browser demo has no HealthKit / Health Connect.)</Text>
          <Row>
            <View style={{ flex: 1 }}>
              <Button label="Allow" onPress={() => grantAndLink(true)} testID="allow-health" />
            </View>
            <View style={{ flex: 1 }}>
              <Button kind="secondary" label="Don't allow" onPress={() => grantAndLink(false)} />
            </View>
          </Row>
        </View>
      ) : null}

      <View style={[s.card, { flexDirection: 'row', marginTop: 8 }]} testID="activity-stats">
        <Stat label="Steps today" value={sum ? fmt(sum.steps) : '–'} />
        <Stat label="UREC visits (7 days)" value={hist ? String(hist.visitsThisWeek) : '–'} />
        <Stat label="Points" value={hist ? fmt(hist.balance) : '–'} />
      </View>
      {sum ? (
        <View style={{ marginTop: -4, marginBottom: 12 }}>
          <ProgressBar value={progress(sum.steps, sum.stepGoal)} color={colors.primary} />
          <Text style={s.small}>
            {fmt(sum.steps)} of {fmt(sum.stepGoal)} steps{sum.latestHeartRate ? ` · latest heart rate ${sum.latestHeartRate} bpm` : ''}
          </Text>
        </View>
      ) : null}

      <Text style={a.section}>Completed today</Text>
      <Card>
        {records.length === 0 ? <Text style={s.muted}>No activity synced today.</Text> : null}
        {records.slice(0, 8).map((r, i, all) => (
          <View key={r.id} style={[a.row, i < all.length - 1 && a.line]}>
            <View style={{ flex: 1 }}>
              <Text style={a.title}>Device sync</Text>
              <Text style={s.small}>{clockTime(r.timestamp)}{r.heartRate ? ` · ${r.heartRate} bpm` : ''}</Text>
            </View>
            <Text style={a.value}>+{fmt(r.steps)} steps</Text>
          </View>
        ))}
      </Card>

      <Text style={a.section}>UREC visits</Text>
      <Card>
        <Button kind="accent" label="Check in at UREC →" onPress={swipe} busy={busy === 'swipe'} testID="swipe" />
        <Text style={[s.small, { marginBottom: 6 }]}>Card reader is simulated in this build. Swiping in at UREC earns points.</Text>
        {(hist?.visits ?? []).length === 0 ? <Text style={s.muted}>No visits yet.</Text> : null}
        {(hist?.visits ?? []).slice(0, 10).map((v, i, all) => (
          <View key={v.visitId} style={[a.row, i < all.length - 1 && a.line]}>
            <View style={{ flex: 1 }}>
              <Text style={a.title}>UREC visit</Text>
              <Text style={s.small}>{shortTime(v.timestamp)} · {v.readerId}</Text>
            </View>
          </View>
        ))}
      </Card>

      <Text style={a.section}>Recent points</Text>
      <Card>
        {(hist?.transactions ?? []).length === 0 ? <Text style={s.muted}>No points yet.</Text> : null}
        {(hist?.transactions ?? []).slice(0, 10).map((t, i, all) => (
          <View key={t.id} style={[a.row, i < all.length - 1 && a.line]}>
            <View style={{ flex: 1 }}>
              <Text style={a.title}>{t.reason === 'VISIT' ? 'UREC visit' : 'Daily step goal'}</Text>
              <Text style={s.small}>{shortTime(t.timestamp)}</Text>
            </View>
            <Text style={a.value}>+{t.amount} pts</Text>
          </View>
        ))}
      </Card>

      <Text style={a.section}>Smart devices</Text>
      <Card>
        {links.length === 0 ? <Text style={s.muted}>No device connected.</Text> : null}
        {links.map((l) => (
          <View key={l.linkId} style={[a.row, a.line, { flexWrap: 'wrap' }]}>
            <View style={{ flex: 1, minWidth: 150 }}>
              <Text style={a.title}>{l.deviceId}</Text>
              <Text style={s.small}>{l.deviceType}, {l.platform} · linked {shortTime(l.linkedAt)}</Text>
            </View>
            <View style={{ width: 96 }}>
              <Button kind="secondary" label="Sync now" onPress={() => sync(l)} busy={busy === 'sync'} />
            </View>
            <View style={{ width: 90, marginLeft: 6 }}>
              <Button kind="outlineDanger" label="Unlink" onPress={() => unlink(l)} />
            </View>
          </View>
        ))}
        {links.length > 0 ? <Toggle label="Sync automatically every minute" value={autoSync} onChange={setAutoSync} /> : null}
        {!asking && links.length ? (
          <Button kind="secondary" label="Connect another device" onPress={() => setAsking(true)} busy={busy === 'link'} />
        ) : null}
        <Text style={s.small}>Device is simulated in this build (stub of a watch / phone).</Text>
      </Card>
    </ScrollView>
  );
}

const a = {
  section: { fontSize: 20, fontWeight: '700' as const, color: colors.heading, marginTop: 10, marginBottom: 8 },
  row: { flexDirection: 'row' as const, alignItems: 'center' as const, paddingVertical: 10 },
  line: { borderBottomWidth: 1, borderBottomColor: colors.border },
  title: { fontSize: 15, fontWeight: '600' as const, color: colors.heading },
  value: { fontSize: 14, fontWeight: '600' as const, color: colors.heading },
};
