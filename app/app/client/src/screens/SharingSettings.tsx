// UC-3 "Set Data-Sharing Consent" — SD-3 "choose fields to share -> setConsent(fields)"; NFR Legislative-Privacy.
// TD-4 §2: sharing settings live on the Account tab (moved from the Social screen in stage 3).
import React, { useState } from 'react';
import { Text } from 'react-native';
import { api, ApiError } from '../api';
import type { Consent } from '../types';
import { Banner, Button, MaintenanceNotice, s, Toggle } from '../ui';
import { useAsync } from '../useAsync';

export default function SharingSettings() {
  const q = useAsync(() => api.consent(), []);
  const [c, setC] = useState<Consent | null>(null);
  const [msg, setMsg] = useState<{ kind: 'good' | 'error'; text: string } | null>(null);
  if (q.error?.isMaintenance) return <MaintenanceNotice name="Friends & Leaderboard" />;
  if (q.error) return <Banner kind="error">{q.error.message}</Banner>;
  const cur = c ?? q.data;
  if (!cur) return <Text style={s.muted}>Loading…</Text>;
  const set = (k: keyof Consent) => (v: boolean) => setC({ ...cur, [k]: v });

  async function save() {
    try {
      const saved = await api.setConsent(cur!);
      q.setData(saved);
      setC(null);
      setMsg({ kind: 'good', text: 'Sharing settings saved.' });
    } catch (e) {
      setMsg({ kind: 'error', text: (e as ApiError).message });
    }
  }
  return (
    <>
      <Text style={s.muted}>What my friends can see. Nothing is shared unless you turn it on here.</Text>
      <Toggle label="Calories" value={cur.shareCalories} onChange={set('shareCalories')} />
      <Toggle label="Water intake" value={cur.shareWater} onChange={set('shareWater')} />
      <Toggle label="Steps" value={cur.shareSteps} onChange={set('shareSteps')} />
      <Toggle label="UREC visits" value={cur.shareVisits} onChange={set('shareVisits')} />
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}
      <Button label="Save sharing settings" onPress={save} testID="save-consent" />
    </>
  );
}
