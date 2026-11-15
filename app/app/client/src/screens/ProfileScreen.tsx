// FR-3 "view their own profile" + UC-3 "Edit Public Profile" (User.updateProfile, changePassword — §5.2);
// Student.setGoals(cal, water, steps) with NFR Safety "warning whenever an unsafe goal is set".
// TD-4: layout of spec/05-ui-prototype/6-account.png — "Your account" card with the initials avatar, grouped
// "ACCOUNT & PREFERENCES" / "PRIVACY & SECURITY" lists with chevrons (each row opens its panel inline), outlined red
// "Log out", "CometFit · Version 1.0". TD-4 §2: profile, goals and units, sharing settings, 2FA and log out are here.
// Prototype rows with no FR/UC (Notifications, Terms of service, Help & support, Delete account) are left out: A-51.
import React, { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { forgetRememberedDevice, getRememberedDevice } from '../deviceTrust';
import { goalWarnings, isStillRemembered, parsePositiveInt, type RememberedDevice } from '../logic';
import type { Profile } from '../types';
import { Avatar, Banner, Button, colors, Field, ListRow, s, SectionLabel, Toggle } from '../ui';
import { useAsync } from '../useAsync';
import SharingSettings from './SharingSettings';

type Panel = 'personal' | 'password' | 'goals' | 'sharing' | 'twofactor' | null;

export default function ProfileScreen({ username, onLogout, onChanged, onNameChanged }: {
  username?: string; onLogout: () => void; onChanged: () => void; onNameChanged?: (n: string) => void;
}) {
  const q = useAsync(() => api.profile(), []);
  const [open, setOpen] = useState<Panel>(null);
  const p = q.data;
  const toggle = (x: Panel) => setOpen(open === x ? null : x);
  const student = p?.role === 'STUDENT';
  return (
    <ScrollView>
      {q.error ? <Banner kind="error">{q.error.message}</Banner> : null}
      {p ? (
        <>
          <View style={[s.card, { flexDirection: 'row', alignItems: 'center', gap: 14 }]}>
            <Avatar name={p.displayName} size={48} />
            <View style={{ flex: 1 }}>
              <Text style={[s.cardTitle, { fontSize: 18 }]}>{p.displayName}</Text>
              <Text style={s.small}>@{p.username} · Manage your personal information</Text>
            </View>
          </View>

          <SectionLabel>Account & preferences</SectionLabel>
          <View style={[s.card, { paddingVertical: 4 }]}>
            <ListRow title="Personal information" subtitle="Display name and profile visibility" open={open === 'personal'} onPress={() => toggle('personal')} testID="row-personal" />
            {open === 'personal' ? (
              <PersonalInfo profile={p} onSaved={(x) => { q.setData(x); onNameChanged?.(x.displayName); }} />
            ) : null}
            <ListRow title="Email & password" open={open === 'password'} onPress={() => toggle('password')} last={!student} testID="row-password" />
            {open === 'password' ? <EmailPassword email={p.email} /> : null}
            {student ? (
              <>
                <ListRow title="Daily goals & units" open={open === 'goals'} onPress={() => toggle('goals')} last testID="row-goals" />
                {open === 'goals' ? <Goals profile={p} onSaved={(x) => { q.setData(x); onChanged(); }} /> : null}
              </>
            ) : null}
          </View>

          <SectionLabel>Privacy & security</SectionLabel>
          <View style={[s.card, { paddingVertical: 4 }]}>
            {student ? (
              <>
                <ListRow title="Privacy & data" subtitle="What your friends can see" open={open === 'sharing'} onPress={() => toggle('sharing')} testID="row-sharing" />
                {open === 'sharing' ? <View style={st.panel}><SharingSettings /></View> : null}
              </>
            ) : null}
            <ListRow title="Two-step verification" subtitle="On · Duo Mobile / authenticator app" open={open === 'twofactor'} onPress={() => toggle('twofactor')} last testID="row-2fa" />
            {open === 'twofactor' ? <TwoStep username={username ?? p.username} /> : null}
          </View>
        </>
      ) : null}
      <View style={{ marginTop: 8 }}>
        <Button big kind="outlineDanger" label="Log out" onPress={onLogout} testID="logout" />
      </View>
      <Text style={[s.small, { textAlign: 'center', marginTop: 10 }]}>CometFit · Version 1.0</Text>
      <Text style={[s.small, { textAlign: 'center', marginTop: 4, marginBottom: 16 }]}>
        CometFit is a general wellness app and does not diagnose or treat any medical condition.
      </Text>
    </ScrollView>
  );
}

const st = {
  panel: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: colors.border },
};

function PersonalInfo({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
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
    <View style={st.panel}>
      <Text style={s.muted}>Username @{profile.username}</Text>
      <Field label="Display name (public)" value={name} onChangeText={setName} />
      <Toggle label="Public profile (visible to non-friends)" value={isPublic} onChange={setPublic} />
      {msg ? <Text style={s.muted}>{msg}</Text> : null}
      <Button label="Save profile" onPress={save} disabled={!name.trim()} />
    </View>
  );
}

/** TD-1 (3) / TD-5 (2): two-step sign-in is always on (NFR Security); this device may be remembered for 30 days. */
function TwoStep({ username }: { username: string }) {
  const [device, setDevice] = useState<RememberedDevice | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => {
    getRememberedDevice(username).then((d) => setDevice(isStillRemembered(d) ? d : null));
  }, [username]);
  async function forget() {
    await forgetRememberedDevice(username);
    setDevice(null);
    setMsg('This device is forgotten. You will be asked for a code at your next sign-in.');
  }
  return (
    <View style={[st.panel, { borderBottomWidth: 0 }]}>
      <Text style={s.body}>
        Two-step verification is always on: after your password, CometFit asks for the 6-digit code from Duo Mobile
        (or any authenticator app) unless this device is remembered.
      </Text>
      <Text style={[s.muted, { marginTop: 6 }]} testID="remembered-status">
        {device ? `This device is remembered until ${new Date(device.trustedUntil).toLocaleDateString()}.` : 'This device is not remembered.'}
      </Text>
      {msg ? <Banner kind="good">{msg}</Banner> : null}
      {device ? <Button kind="secondary" label="Forget this device" onPress={forget} testID="forget-device" /> : null}
      <Text style={s.small}>Changing your password also forgets every remembered device.</Text>
    </View>
  );
}

function EmailPassword({ email }: { email: string }) {
  return (
    <View style={st.panel}>
      <Text style={s.muted}>Email (private): {email}</Text>
      <PasswordCard />
    </View>
  );
}

function Goals({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
  const [cal, setCal] = useState(String(profile.calorieGoal ?? ''));
  const [water, setWater] = useState(String(profile.waterGoalMl ?? ''));
  const [steps, setSteps] = useState(String(profile.stepGoal ?? ''));
  const [bottle, setBottle] = useState(String(profile.bottleSizeMl ?? ''));
  const [serverWarnings, setServerWarnings] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const c = parsePositiveInt(cal, 10000), w = parsePositiveInt(water, 10000), stp = parsePositiveInt(steps, 100000);
  const b = parsePositiveInt(bottle, 5000);
  const live = c && w && stp ? goalWarnings(c, w, stp) : [];
  useEffect(() => setServerWarnings(null), [cal, water, steps]);

  async function save() {
    setError(null);
    try {
      const r = await api.setGoals({ calorieGoal: c!, waterGoalMl: w!, stepGoal: stp!, bottleSizeMl: b ?? undefined });
      setServerWarnings(r.warnings);
      onSaved(r.profile);
    } catch (e) {
      setError((e as ApiError).message);
    }
  }
  return (
    <View style={st.panel}>
      <Text style={[s.small, { marginBottom: 6 }]}>Water is logged in liters or gallons on Food & Water; goals and bottle size are in ml.</Text>
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
      <Button label="Save goals" onPress={save} disabled={!c || !w || !stp || (bottle !== '' && !b)} testID="save-goals" />
    </View>
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
    <View style={{ marginTop: 8 }}>
      <Field label="Current password" value={oldPw} onChangeText={setOld} secureTextEntry />
      <Field label="New password (8+ characters)" value={newPw} onChangeText={setNew} secureTextEntry />
      {msg ? <Text style={s.muted}>{msg}</Text> : null}
      <Button label="Change password" onPress={save} disabled={!oldPw || newPw.length < 8} />
    </View>
  );
}
