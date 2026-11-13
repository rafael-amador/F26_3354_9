// Log In (Password + TOTP) — included by UC-1..UC-4 (Deliverable 1 Fig 4.3); SD-4 "login (password + TOTP)".
// NFR Security: username + password + TOTP MFA. Sign-up for students: ASSUMPTIONS A-3.
import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { LoginResponse, Registration } from '../types';
import { Banner, Button, Card, Field, s } from '../ui';

export default function LoginScreen({ onLoggedIn }: { onLoggedIn: (r: LoginResponse) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [registered, setRegistered] = useState<Registration | null>(null);

  async function login() {
    setError(null);
    setBusy(true);
    try {
      onLoggedIn(await api.login({ username, password, totpCode: code.trim() }));
    } catch (e) {
      setError((e as ApiError).message);
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

      {registered ? (
        <Card title="Set up two-factor sign-in">
          <Text style={s.body}>
            Account created for {registered.user.username}. Add this secret to an authenticator app (TOTP, 6 digits, 30 s).
            You need the 6-digit code every time you sign in.
          </Text>
          <Text selectable style={[s.body, { fontFamily: 'monospace', fontWeight: '700', marginVertical: 8 }]}>
            {registered.totpSecret}
          </Text>
          <Text selectable style={s.muted}>{registered.otpauthUri}</Text>
        </Card>
      ) : null}

      <Card title={mode === 'login' ? 'Sign in' : 'Create a student account'}>
        {error ? <Banner kind="error">{error}</Banner> : null}
        <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} testID="username" />
        <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry testID="password" />
        {mode === 'login' ? (
          <Field
            label="6-digit code from your authenticator app"
            value={code}
            onChangeText={setCode}
            keyboardType="number-pad"
            maxLength={6}
            testID="totp"
          />
        ) : (
          <>
            <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
            <Field label="Display name (public)" value={displayName} onChangeText={setDisplayName} />
            <Text style={s.muted}>Password: at least 8 characters.</Text>
          </>
        )}
        {mode === 'login' ? (
          <Button label="Sign in" onPress={login} busy={busy} disabled={!username || !password || code.trim().length !== 6} testID="signin" />
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
