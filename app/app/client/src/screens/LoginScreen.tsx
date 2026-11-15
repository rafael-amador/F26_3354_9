// Log In (Password + TOTP) — included by UC-1..UC-4 (D1 Fig 4.3); SD-4 "login (password + TOTP)".
// NFR Security: username + password + TOTP MFA. Sign-up for students: ASSUMPTIONS A-3.
// TD-5 (supersedes the TD-1 screen layout and A-39 in part):
//   (1) "Sign in" asks only for username and password; the code is never asked before Sign in is pressed.
//   (2) Remembered device (TD-1, 30 days, same user) -> signed in, no second step. Otherwise "Verify it's you" asks
//       for the 6-digit code from Duo Mobile with the "Remember this device for 30 days" checkbox.
//   (3) "Register" asks only for account details; then "Set up two-step verification" shows a QR code for Duo
//       Mobile on every screen size, plus TD-1 "Add to authenticator app" and "Copy setup key"; the first code
//       finishes setup and signs in.
// TD-4: styled like spec/05-ui-prototype/1-sign-in.png (wordmark on top, centered title + subtitle, one wide button,
// small grey footer). TD-4 §3: no "Sign in with Microsoft". Terms of Service / Privacy Policy links: A-46.
// TD-7 §2: the CometFit logo (spec/07-logo, assets/logo.png from app/tools/make_icons.py) sits left of the
// "CometFit" wordmark on all four steps (Sign in, Verify it's you, Register, Set up two-step verification),
// where prototype 1-sign-in shows its logo. Size: A-60.
import * as Clipboard from 'expo-clipboard';
import React, { useMemo, useState } from 'react';
import { Image, Linking, ScrollView, Text, TextInput, useWindowDimensions, View } from 'react-native';

// TD-7 §1, §3: transparent logo, aspect ratio kept (256 x 163 source -> 64 x 41 on screen, not stretched).
const LOGO = require('../../assets/logo.png');
const LOGO_W = 64;
const LOGO_H = Math.round((LOGO_W * 163) / 256);
import { api, ApiError } from '../api';
import { challengeExpired, isSixDigitCode, submitCode, submitPassword, type AuthDeps } from '../authFlow';
import { forgetRememberedDevice, getRememberedDevice, saveRememberedDevice } from '../deviceTrust';
import { qrCellSize } from '../logic';
import { qrMatrix } from '../qr';
import type { LoginResponse, Registration } from '../types';
import { Banner, Button, Card, Checkbox, colors, Field, Row, s } from '../ui';

const deps: AuthDeps = {
  login: api.login,
  verify: api.verify,
  getRememberedDevice,
  saveRememberedDevice,
  forgetRememberedDevice,
};

type Step =
  | { name: 'signin' }
  | { name: 'register' }
  | { name: 'verify'; challengeId: string; expiresAt: string | null }
  | { name: 'setup'; registration: Registration };

function message(e: unknown): string {
  return e instanceof ApiError || e instanceof Error ? e.message : 'Something went wrong';
}

export default function LoginScreen({ onLoggedIn }: { onLoggedIn: (r: LoginResponse) => void }) {
  const [step, setStep] = useState<Step>({ name: 'signin' });
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [code, setCode] = useState('');
  const [rememberDevice, setRememberDevice] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function go(next: Step) {
    setError(null);
    setCode('');
    setRememberDevice(false);
    setStep(next);
  }

  // TD-5 step 1
  async function signIn() {
    setError(null);
    setBusy(true);
    try {
      const out = await submitPassword(deps, username, password);
      if (out.kind === 'signedIn') onLoggedIn(out.session);
      else go({ name: 'verify', challengeId: out.challengeId, expiresAt: out.expiresAt });
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }

  // TD-5 step 2 ("Verify it's you") and TD-5 (3) setup: same server call
  async function verify(challengeId: string, expiresAt: string | null) {
    setError(null);
    if (challengeExpired(expiresAt)) {
      setError('This sign-in has expired. Go back and sign in again.');
      return;
    }
    setBusy(true);
    try {
      onLoggedIn(await submitCode(deps, username, challengeId, code, rememberDevice));
    } catch (e) {
      setError(message(e));
      setCode('');
    } finally {
      setBusy(false);
    }
  }

  async function register() {
    setError(null);
    setBusy(true);
    try {
      const r = await api.register({ username, password, email, displayName: displayName || undefined });
      setPassword('');
      go({ name: 'setup', registration: r });
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }

  const head = {
    signin: { title: 'Sign in', sub: 'Students, use your CometFit username and password.' },
    register: { title: 'Create a student account', sub: 'Enter your account details. You will set up two-step verification next.' },
    verify: { title: "Verify it's you", sub: `Enter the 6-digit code from Duo Mobile for ${username.trim()}.` },
    setup: { title: 'Set up two-step verification', sub: 'Add CometFit to Duo Mobile, then enter the first 6-digit code.' },
  }[step.name];

  return (
    <ScrollView contentContainerStyle={{ padding: 24, maxWidth: 440, width: '100%', alignSelf: 'center' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginTop: step.name === 'signin' ? 56 : 24, marginBottom: step.name === 'signin' ? 72 : 28 }}>
        <Image source={LOGO} style={{ width: LOGO_W, height: LOGO_H, marginRight: 10 }} resizeMode="contain"
          accessibilityLabel="CometFit logo" testID="auth-logo" />
        <Text style={{ fontSize: 34, fontWeight: '800', color: colors.heading }} accessibilityRole="header">
          CometFit
        </Text>
      </View>

      <View style={{ alignItems: 'center', marginBottom: 18 }}>
        <Text style={{ fontSize: 18, fontWeight: '700', color: colors.text }} testID="auth-title">{head.title}</Text>
        <Text style={[s.body, { textAlign: 'center', marginTop: 6, lineHeight: 21 }]}>{head.sub}</Text>
      </View>

      {error ? <Banner kind="error">{error}</Banner> : null}

      {step.name === 'signin' ? (
        <View>
          <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} testID="username" />
          <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry onSubmitEditing={signIn} testID="password" />
          <Button big label="Sign in" onPress={signIn} busy={busy} disabled={!username.trim() || !password} testID="signin" />
          <Button kind="secondary" label="New student? Create an account" onPress={() => go({ name: 'register' })} testID="to-register" />
        </View>
      ) : null}

      {step.name === 'register' ? (
        <View>
          <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} testID="reg-username" />
          <Field label="Password (at least 8 characters)" value={password} onChangeText={setPassword} secureTextEntry testID="reg-password" />
          <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" testID="reg-email" />
          <Field label="Display name (public)" value={displayName} onChangeText={setDisplayName} testID="reg-name" />
          <Button big label="Register" onPress={register} busy={busy} disabled={!username.trim() || !password || !email.trim()} testID="register" />
          <Button kind="secondary" label="Back to sign in" onPress={() => go({ name: 'signin' })} />
        </View>
      ) : null}

      {step.name === 'verify' ? (
        <View>
          <CodeInput value={code} onChange={setCode} onSubmit={() => verify(step.challengeId, step.expiresAt)} />
          <Checkbox label="Remember this device for 30 days" value={rememberDevice} onChange={setRememberDevice} testID="remember-device" />
          <Button big label="Verify" onPress={() => verify(step.challengeId, step.expiresAt)} busy={busy} disabled={!isSixDigitCode(code)} testID="verify" />
          <Button kind="secondary" label="Back to sign in" onPress={() => { setPassword(''); go({ name: 'signin' }); }} />
        </View>
      ) : null}

      {step.name === 'setup' ? (
        <View>
          <AuthenticatorSetup registration={step.registration} />
          <Card title="2. Enter the first code">
            <CodeInput value={code} onChange={setCode} onSubmit={() => verify(step.registration.setupChallengeId, step.registration.setupExpiresAt)} />
            <Checkbox label="Remember this device for 30 days" value={rememberDevice} onChange={setRememberDevice} testID="remember-device" />
            <Button
              big
              label="Finish setup"
              onPress={() => verify(step.registration.setupChallengeId, step.registration.setupExpiresAt)}
              busy={busy}
              disabled={!isSixDigitCode(code)}
              testID="finish-setup"
            />
          </Card>
        </View>
      ) : null}

      <Text style={[s.small, { textAlign: 'center', marginTop: 18, lineHeight: 18 }]}>
        CometFit is a general wellness app and does not diagnose or treat any medical condition.
      </Text>
    </ScrollView>
  );
}

/** Large centered 6-digit field (TD-5 (2)). */
function CodeInput({ value, onChange, onSubmit }: { value: string; onChange: (v: string) => void; onSubmit: () => void }) {
  return (
    <View style={{ marginBottom: 6 }}>
      <Text style={s.label}>6-digit code from Duo Mobile</Text>
      <TextInput
        value={value}
        onChangeText={(t) => onChange(t.replace(/\D/g, '').slice(0, 6))}
        onSubmitEditing={() => isSixDigitCode(value) && onSubmit()}
        keyboardType="number-pad"
        maxLength={6}
        autoFocus
        placeholder="000000"
        placeholderTextColor={colors.border}
        accessibilityLabel="6-digit code"
        testID="totp"
        style={[s.input, { fontSize: 28, letterSpacing: 8, textAlign: 'center', fontWeight: '700', color: colors.heading }]}
      />
    </View>
  );
}

// ---- TD-5 (3) + TD-1 (1): two-step setup after Register -----------------------------------------------------------
function AuthenticatorSetup({ registration }: { registration: Registration }) {
  const { width } = useWindowDimensions();
  const [note, setNote] = useState<{ kind: 'good' | 'warn'; text: string } | null>(null);
  const rows = useMemo(() => qrMatrix(registration.otpauthUri), [registration.otpauthUri]);
  // ScrollView max 440 wide, 24 padding each side, card padding 16 each side
  const cell = qrCellSize(Math.min(width, 440) - 48 - 32, rows.length);

  async function openAuthenticator() {
    try {
      await Linking.openURL(registration.otpauthUri);
    } catch {
      setNote({ kind: 'warn', text: 'No authenticator app opened. Scan the QR code or use "Copy setup key".' });
    }
  }
  async function copyKey() {
    try {
      await Clipboard.setStringAsync(registration.totpSecret);
      setNote({ kind: 'good', text: 'Setup key copied. Paste it into Duo Mobile or your authenticator app.' });
    } catch {
      setNote({ kind: 'warn', text: 'Could not copy. Select the key below and copy it by hand.' });
    }
  }

  return (
    <Card title="1. Scan with Duo Mobile">
      <Text style={s.body}>
        Account {registration.user.username} created. In Duo Mobile tap Add, then scan this QR code. Any authenticator
        app also works.
      </Text>
      <View style={{ alignItems: 'center', marginVertical: 10 }} testID="setup-qr">
        <SetupQrCode rows={rows} cell={cell} />
      </View>
      {note ? <Banner kind={note.kind}>{note.text}</Banner> : null}
      <Row>
        <View style={{ flex: 1, minWidth: 150 }}>
          <Button kind="secondary" label="Add to authenticator app" onPress={openAuthenticator} testID="add-authenticator" />
        </View>
        <View style={{ flex: 1, minWidth: 150 }}>
          <Button kind="secondary" label="Copy setup key" onPress={copyKey} testID="copy-setup-key" />
        </View>
      </Row>
      <Text selectable style={[s.muted, { fontFamily: 'monospace', marginTop: 6, textAlign: 'center' }]}>
        Setup key: {registration.totpSecret}
      </Text>
    </Card>
  );
}

/** Draws the QR matrix with Views (works in the browser and on phones). */
function SetupQrCode({ rows, cell }: { rows: boolean[][]; cell: number }) {
  const quiet = cell * 4;
  return (
    <View style={{ backgroundColor: '#fff', padding: quiet }} accessibilityLabel="QR code to scan with Duo Mobile">
      {rows.map((row, y) => (
        <View key={y} style={{ flexDirection: 'row' }}>
          {row.map((dark, x) => (
            <View key={x} style={{ width: cell, height: cell, backgroundColor: dark ? '#000' : '#fff' }} />
          ))}
        </View>
      ))}
    </View>
  );
}
