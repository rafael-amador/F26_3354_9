// UC-2 Track Activity and Rec Center Visits — SD-2:
//   Connect and sync device: choose Connect Device -> request health permissions -> grant permission ->
//     linkDevice(deviceId) -> [server pairs] ; loop [periodic sync] pushSteps(steps, heartRate, timestamp)   (FR-4)
//   Rec center check-in: swipe student card -> recordVisit -> awardPoints -> "visit logged, points earned" ->
//     "show visit history and points"                                                                       (FR-7)
import React, { useEffect, useRef, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { progress, shortTime } from '../logic';
import { FakeSmartDevice } from '../smartDevice';
import type { DeviceLink } from '../types';
import { Banner, Button, Card, colors, MaintenanceNotice, ProgressBar, Row, s, Stat, Toggle } from '../ui';
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
  return (
    <ScrollView>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}

      <Card title="Smart devices">
        {links.length === 0 ? <Text style={s.muted}>No device connected.</Text> : null}
        {links.map((l) => (
          <View key={l.linkId} style={s.listItem}>
            <Text style={s.body}>
              {l.deviceId} ({l.deviceType}, {l.platform})
            </Text>
            <Text style={s.muted}>Linked {shortTime(l.linkedAt)}</Text>
            <Row>
              <View style={{ flex: 1 }}>
                <Button label="Sync now" onPress={() => sync(l)} busy={busy === 'sync'} testID="sync" />
              </View>
              <View style={{ flex: 1 }}>
                <Button kind="secondary" label="Unlink" onPress={() => unlink(l)} />
              </View>
            </Row>
          </View>
        ))}
        {links.length > 0 ? <Toggle label="Sync automatically every minute" value={autoSync} onChange={setAutoSync} /> : null}

        {asking ? (
          <View style={{ backgroundColor: colors.infoBg, padding: 12, borderRadius: 8, marginTop: 8 }}>
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
        ) : (
          <Button label="Connect smart device" kind={links.length ? 'secondary' : 'primary'} onPress={() => setAsking(true)} busy={busy === 'link'} testID="connect-device" />
        )}
        <Text style={s.muted}>Device is simulated in this build (stub of a watch / phone).</Text>
      </Card>

      <Card title="Today's movement">
        {sum ? (
          <>
            <Text style={s.body}>
              {sum.steps} / {sum.stepGoal} steps
            </Text>
            <ProgressBar value={progress(sum.steps, sum.stepGoal)} color={colors.good} />
          </>
        ) : null}
        {(recordsQ.data ?? []).slice(0, 5).map((r) => (
          <Text key={r.id} style={s.muted}>
            {shortTime(r.timestamp)} · +{r.steps} steps{r.heartRate ? ` · ${r.heartRate} bpm` : ''}
          </Text>
        ))}
      </Card>

      <Card title="UREC visits & points">
        {hist ? (
          <Row>
            <Stat label="Points" value={String(hist.balance)} />
            <Stat label="Visits (7 days)" value={String(hist.visitsThisWeek)} />
            <Stat label="All visits" value={String(hist.visits.length)} />
          </Row>
        ) : null}
        <Button label="Simulate UREC card swipe" onPress={swipe} busy={busy === 'swipe'} testID="swipe" />
        <Text style={s.muted}>Card reader is simulated in this build. Swiping in at UREC earns points.</Text>
        <Text style={[s.body, { marginTop: 10, fontWeight: '600' }]}>Visit history</Text>
        {(hist?.visits ?? []).length === 0 ? <Text style={s.muted}>No visits yet.</Text> : null}
        {(hist?.visits ?? []).slice(0, 10).map((v) => (
          <Text key={v.visitId} style={s.muted}>
            {shortTime(v.timestamp)} · {v.readerId}
          </Text>
        ))}
        <Text style={[s.body, { marginTop: 10, fontWeight: '600' }]}>Recent points</Text>
        {(hist?.transactions ?? []).slice(0, 10).map((t) => (
          <Text key={t.id} style={s.muted}>
            {shortTime(t.timestamp)} · +{t.amount} ({t.reason === 'VISIT' ? 'UREC visit' : 'Daily step goal'})
          </Text>
        ))}
      </Card>
    </ScrollView>
  );
}
