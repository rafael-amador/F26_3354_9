// FR-3 "view their own profile" + UC-3 "Edit Public Profile" (User.updateProfile, changePassword — §5.2);
// Student.setGoals(cal, water, steps) with NFR Safety "warning whenever an unsafe goal is set".
import React, { useEffect, useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { api, ApiError } from '../api';
import { goalWarnings, parsePositiveInt } from '../logic';
import type { Profile } from '../types';
import { Banner, Button, Card, Field, s, Toggle } from '../ui';
import { useAsync } from '../useAsync';

export default function ProfileScreen({ onLogout, onChanged }: { onLogout: () => void; onChanged: () => void }) {
  const q = useAsync(() => api.profile(), []);
  const p = q.data;
  return (
    <ScrollView>
      {q.error ? <Banner kind="error">{q.error.message}</Banner> : null}
      {p ? (
        <>
          <PublicProfileCard profile={p} onSaved={q.setData} />
          {p.role === 'STUDENT' ? <GoalsCard profile={p} onSaved={(x) => { q.setData(x); onChanged(); }} /> : null}
          <PasswordCard />
        </>
      ) : null}
      <Button kind="danger" label="Sign out" onPress={onLogout} testID="logout" />
      <Text style={[s.muted, { textAlign: 'center', marginTop: 8 }]}>
        CometFit is a general wellness app and does not diagnose or treat any medical condition.
      </Text>
    </ScrollView>
  );
}

function PublicProfileCard({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
  const [name, setName] = useState(profile.displayName);
  const [isPublic, setPublic] = useState(profile.isPublic);
  const [msg, setMsg] = useState<string | null>(null);
  async function save() {
    try {
      onSaved(await api.updateProfile({ displayName: name, isPublic }));
      setMsg('Profile saved.');
    } catch (e) {
      setMsg((e as ApiError).message);
    }
  }
  return (
    <Card title="My profile">
      <Text style={s.body}>@{profile.username}</Text>
      <Text style={s.muted}>{profile.email} (private)</Text>
      <Field label="Display name (public)" value={name} onChangeText={setName} />
      <Toggle label="Public profile (visible to non-friends)" value={isPublic} onChange={setPublic} />
      {msg ? <Text style={s.muted}>{msg}</Text> : null}
      <Button label="Save profile" onPress={save} disabled={!name.trim()} />
    </Card>
  );
}

function GoalsCard({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
  const [cal, setCal] = useState(String(profile.calorieGoal ?? ''));
  const [water, setWater] = useState(String(profile.waterGoalMl ?? ''));
  const [steps, setSteps] = useState(String(profile.stepGoal ?? ''));
  const [bottle, setBottle] = useState(String(profile.bottleSizeMl ?? ''));
  const [serverWarnings, setServerWarnings] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const c = parsePositiveInt(cal, 10000), w = parsePositiveInt(water, 10000), st = parsePositiveInt(steps, 100000);
  const b = parsePositiveInt(bottle, 5000);
  const live = c && w && st ? goalWarnings(c, w, st) : [];
  useEffect(() => setServerWarnings(null), [cal, water, steps]);

  async function save() {
    setError(null);
    try {
      const r = await api.setGoals({ calorieGoal: c!, waterGoalMl: w!, stepGoal: st!, bottleSizeMl: b ?? undefined });
      setServerWarnings(r.warnings);
      onSaved(r.profile);
    } catch (e) {
      setError((e as ApiError).message);
    }
  }
  return (
    <Card title="Daily goals">
      <Field label="Calories (kcal)" value={cal} onChangeText={setCal} keyboardType="number-pad" testID="goal-cal" />
      <Field label="Water (ml)" value={water} onChangeText={setWater} keyboardType="number-pad" testID="goal-water" />
      <Field label="Steps" value={steps} onChangeText={setSteps} keyboardType="number-pad" />
      <Field label="My water bottle size (ml)" value={bottle} onChangeText={setBottle} keyboardType="number-pad" />
      {live.map((x) => (
        <Banner key={x} kind="warn">⚠ {x}</Banner>
      ))}
      {error ? <Banner kind="error">{error}</Banner> : null}
      {serverWarnings ? (
        <Banner kind={serverWarnings.length ? 'warn' : 'good'}>
          {serverWarnings.length ? 'Goals saved, but please review the warnings above.' : 'Goals saved.'}
        </Banner>
      ) : null}
      <Button label="Save goals" onPress={save} disabled={!c || !w || !st || (bottle !== '' && !b)} testID="save-goals" />
    </Card>
  );
}

function PasswordCard() {
  const [oldPw, setOld] = useState('');
  const [newPw, setNew] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  async function save() {
    try {
      await api.changePassword({ oldPassword: oldPw, newPassword: newPw });
      setMsg('Password changed.');
      setOld('');
      setNew('');
    } catch (e) {
      setMsg((e as ApiError).message);
    }
  }
  return (
    <Card title="Change password">
      <Field label="Current password" value={oldPw} onChangeText={setOld} secureTextEntry />
      <Field label="New password (8+ characters)" value={newPw} onChangeText={setNew} secureTextEntry />
      {msg ? <Text style={s.muted}>{msg}</Text> : null}
      <Button kind="secondary" label="Change password" onPress={save} disabled={!oldPw || newPw.length < 8} />
    </Card>
  );
}
