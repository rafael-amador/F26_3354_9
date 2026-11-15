// Log In (Password + TOTP) — included by UC-1..UC-4 (D1 Fig 4.3); SD-4 "login (password + TOTP)".
// NFR Security: username + password + TOTP MFA. Sign-up for students: ASSUMPTIONS A-3.
// TD-1 (stage 2):
//   (1) after sign-up: "Add to authenticator app" (opens the otpauth link with the account filled in) and
//       "Copy setup key" as a backup;
//   (2) QR code of the setup key only on a larger screen (laptop browser), where a phone can scan it;
//   (3) "Remember this device for 30 days": on a remembered device, username + password only.
import * as Clipboard from 'expo-clipboard';
import React, { useEffect, useMemo, useState } from 'react';
import { Linking, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { api, ApiError } from '../api';
import { forgetRememberedDevice, getRememberedDevice, saveRememberedDevice } from '../deviceTrust';
import { isLargeScreen, isStillRemembered, type RememberedDevice } from '../logic';
import { qrMatrix } from '../qr';
import type { LoginResponse, Registration } from '../types';
import { Banner, Button, Card, Field, Row, s, Toggle } from '../ui';

export default function LoginScreen({ onLoggedIn }: { onLoggedIn: (r: LoginResponse) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [rememberDevice, setRememberDevice] = useState(false);
  const [remembered, setRemembered] = useState<RememberedDevice | null>(null);
  const [useCodeAnyway, setUseCodeAnyway] = useState(false);
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [registered, setRegistered] = useState<Registration | null>(null);

  // TD-1 (3): is this device remembered for the username being typed?
  useEffect(() => {
    let alive = true;
    getRememberedDevice(username).then((d) => alive && setRemembered(isStillRemembered(d) ? d : null));
    return () => {
      alive = false;
    };
  }, [username]);

  const skipCode = remembered !== null && !useCodeAnyway;

  async function login() {
    setError(null);
    setBusy(true);
    try {
      const r = await api.login({
        username,
        password,
        totpCode: skipCode ? undefined : code.trim(),
        rememberDevice: skipCode ? undefined : rememberDevice,
        deviceToken: remembered?.token,
      });
      if (r.deviceToken && r.trustedUntil) {
        await saveRememberedDevice(username, { token: r.deviceToken, trustedUntil: r.trustedUntil });
      }
      onLoggedIn(r);
    } catch (e) {
      if (skipCode && (e as ApiError).status === 401) {
        // Remembered device no longer accepted (expired, password changed, ...): ask for the code again.
        await forgetRememberedDevice(username);
        setRemembered(null);
        setError('Please enter the 6-digit code from your authenticator app.');
      } else {
        setError((e as ApiError).message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function register() {
    setError(null);
    setBusy(true);
    try {
      const r = await api.register({ username, password, email, displayName: displayName || undefined });
      setRegistered(r);
      setMode('login');
      setCode('');
    } catch (e) {
      setError((e as ApiError).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={{ padding: 20, maxWidth: 480, width: '100%', alignSelf: 'center' }}>
      <View style={{ alignItems: 'center', marginVertical: 24 }}>
        <Text style={[s.h1, { fontSize: 30 }]}>CometFit</Text>
        <Text style={s.muted}>Healthy habits for student life</Text>
      </View>

      {registered ? <AuthenticatorSetup registration={registered} /> : null}

      <Card title={mode === 'login' ? 'Sign in' : 'Create a student account'}>
        {error ? <Banner kind="error">{error}</Banner> : null}
        <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} testID="username" />
        <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry testID="password" />
        {mode === 'login' ? (
          skipCode ? (
            <View style={{ marginBottom: 8 }}>
              <Text style={s.muted} testID="remembered-note">
                This device is remembered until {new Date(remembered!.trustedUntil).toLocaleDateString()}. No code needed.
              </Text>
              <Button kind="secondary" label="Use a code instead" onPress={() => setUseCodeAnyway(true)} />
            </View>
          ) : (
            <>
              <Field
                label="6-digit code from your authenticator app"
                value={code}
                onChangeText={setCode}
                keyboardType="number-pad"
                maxLength={6}
                testID="totp"
              />
              <Toggle label="Remember this device for 30 days" value={rememberDevice} onChange={setRememberDevice} />
            </>
          )
        ) : (
          <>
            <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
            <Field label="Display name (public)" value={displayName} onChangeText={setDisplayName} />
            <Text style={s.muted}>Password: at least 8 characters.</Text>
          </>
        )}
        {mode === 'login' ? (
          <Button
            label="Sign in"
            onPress={login}
            busy={busy}
            disabled={!username || !password || (!skipCode && code.trim().length !== 6)}
            testID="signin"
          />
        ) : (
          <Button label="Create account" onPress={register} busy={busy} disabled={!username || !password || !email} />
        )}
        <Button
          kind="secondary"
          label={mode === 'login' ? 'New student? Create an account' : 'Back to sign in'}
          onPress={() => {
            setError(null);
            setMode(mode === 'login' ? 'register' : 'login');
          }}
        />
      </Card>
      <Text style={[s.muted, { textAlign: 'center' }]}>
        CometFit is a general wellness app and does not diagnose or treat any medical condition.
      </Text>
    </ScrollView>
  );
}

// ---- TD-1 (1) + (2): two-factor setup after sign-up ----------------------------------------------------------------
function AuthenticatorSetup({ registration }: { registration: Registration }) {
  const { width } = useWindowDimensions();
  const [note, setNote] = useState<{ kind: 'good' | 'warn'; text: string } | null>(null);

  async function openAuthenticator() {
    try {
      await Linking.openURL(registration.otpauthUri);
    } catch {
      setNote({ kind: 'warn', text: 'No authenticator app opened. Use "Copy setup key" and paste it into your authenticator app.' });
    }
  }
  async function copyKey() {
    try {
      await Clipboard.setStringAsync(registration.totpSecret);
      setNote({ kind: 'good', text: 'Setup key copied. Paste it into your authenticator app.' });
    } catch {
      setNote({ kind: 'warn', text: 'Could not copy. Select the key below and copy it by hand.' });
    }
  }

  return (
    <Card title="Set up two-factor sign-in">
      <Text style={s.body}>
        Account created for {registration.user.username}. Add CometFit to your authenticator app. You will be asked
        for its 6-digit code when you sign in on a new device.
      </Text>
      {note ? <Banner kind={note.kind}>{note.text}</Banner> : null}
      <Row>
        <View style={{ flex: 1 }}>
          <Button label="Add to authenticator app" onPress={openAuthenticator} testID="add-authenticator" />
        </View>
        <View style={{ flex: 1 }}>
          <Button kind="secondary" label="Copy setup key" onPress={copyKey} testID="copy-setup-key" />
        </View>
      </Row>
      {isLargeScreen(width) ? (
        <View style={{ alignItems: 'center', marginVertical: 8 }} testID="setup-qr">
          <Text style={s.muted}>Or scan this QR code with your phone's authenticator app:</Text>
          <SetupQrCode text={registration.otpauthUri} />
        </View>
      ) : null}
      <Text selectable style={[s.muted, { fontFamily: 'monospace', marginTop: 4 }]}>
        Setup key: {registration.totpSecret}
      </Text>
    </Card>
  );
}

/** Draws the QR matrix with Views (works in the browser and on phones). */
function SetupQrCode({ text, cell = 5 }: { text: string; cell?: number }) {
  const rows = useMemo(() => qrMatrix(text), [text]);
  const quiet = cell * 4;
  return (
    <View style={{ backgroundColor: '#fff', padding: quiet, marginTop: 8 }} accessibilityLabel="Authenticator setup QR code">
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
