// TD-5 (5) client tests: "password-only login without a remembered device does not give a session; remembered device
// skips step 2; wrong code fails; challenge expires and is single-use." Runs the real client flow (src/authFlow.ts)
// against a fake server that follows the same rules as the CometFit server (AuthService, A-42).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isSixDigitCode, submitCode, submitPassword, toSession, type AuthDeps } from '../src/authFlow.ts';
import type { RememberedDevice } from '../src/logic.ts';
import type { LoginStep } from '../src/types.ts';

const GENERIC = 'Invalid username, password or code';
const USER = { userId: 7, username: 'alice', displayName: 'Alice', role: 'STUDENT' as const, isPublic: true, permissions: [] };

function fakeServer() {
  let clock = Date.parse('2026-11-15T12:00:00Z');
  let seq = 0;
  const challenges = new Map<string, { expires: number; attempts: number }>();
  const devices = new Map<string, number>();          // token -> trusted until
  const sessions: string[] = [];
  const empty: LoginStep = {
    verificationRequired: false, challengeId: null, challengeExpiresAt: null, token: null, expiresAt: null, user: null,
    deviceToken: null, trustedUntil: null, rememberedDevice: false,
  };
  const fail = () => Object.assign(new Error(GENERIC), { status: 401 });
  const session = (extra: Partial<LoginStep>): LoginStep => {
    const token = `session-${++seq}`;
    sessions.push(token);
    return { ...empty, token, expiresAt: new Date(clock + 12 * 3600e3).toISOString(), user: USER, ...extra };
  };
  const store = new Map<string, RememberedDevice>();

  const deps: AuthDeps = {
    async login({ username, password, deviceToken }) {
      if (username !== 'alice' || password !== 'pw') throw fail();
      if (deviceToken && (devices.get(deviceToken) ?? 0) > clock) return session({ rememberedDevice: true });
      const id = `challenge-${++seq}`;
      challenges.set(id, { expires: clock + 5 * 60e3, attempts: 0 });
      return { ...empty, verificationRequired: true, challengeId: id, challengeExpiresAt: new Date(clock + 5 * 60e3).toISOString() };
    },
    async verify({ challengeId, totpCode, rememberDevice }) {
      const c = challenges.get(challengeId);
      if (!c) throw fail();
      if (c.expires <= clock) { challenges.delete(challengeId); throw fail(); }
      if (totpCode !== '123456') { if (++c.attempts >= 5) challenges.delete(challengeId); throw fail(); }
      challenges.delete(challengeId);
      if (!rememberDevice) return session({});
      const dt = `device-${++seq}`;
      devices.set(dt, clock + 30 * 86400e3);
      return session({ deviceToken: dt, trustedUntil: new Date(clock + 30 * 86400e3).toISOString() });
    },
    getRememberedDevice: async (u) => store.get(u) ?? null,
    saveRememberedDevice: async (u, d) => { store.set(u, d); },
    forgetRememberedDevice: async (u) => { store.delete(u); },
    now: () => new Date(clock),
  };
  return { deps, sessions, store, devices, advance: (ms: number) => { clock += ms; } };
}

test('password only, no remembered device: a challenge and no session', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  assert.equal(out.kind, 'verify');
  assert.equal(f.sessions.length, 0);
  // a challenge response is never treated as a session
  assert.equal(toSession({ verificationRequired: true, challengeId: 'x', challengeExpiresAt: null, token: null, expiresAt: null,
    user: null, deviceToken: null, trustedUntil: null, rememberedDevice: false }), null);
});

test('wrong password fails with the generic message', async () => {
  const f = fakeServer();
  await assert.rejects(submitPassword(f.deps, 'alice', 'nope'), { message: GENERIC });
});

test('code then session; "Remember this device" makes the next sign-in skip step 2', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  assert.equal(out.kind, 'verify');
  const s1 = await submitCode(f.deps, 'alice', out.kind === 'verify' ? out.challengeId : '', '123456', true);
  assert.equal(s1.user.username, 'alice');
  assert.ok(f.store.get('alice')?.token, 'device token saved on this device');
  const again = await submitPassword(f.deps, 'alice', 'pw');
  assert.equal(again.kind, 'signedIn');
  assert.equal(again.kind === 'signedIn' && again.session.rememberedDevice, true);
});

test('not ticking the checkbox stores nothing', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  await submitCode(f.deps, 'alice', out.kind === 'verify' ? out.challengeId : '', '123456', false);
  assert.equal(f.store.size, 0);
  assert.equal((await submitPassword(f.deps, 'alice', 'pw')).kind, 'verify');
});

test('wrong code fails, the right code then still works', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  const id = out.kind === 'verify' ? out.challengeId : '';
  await assert.rejects(submitCode(f.deps, 'alice', id, '000000', false), { message: GENERIC });
  await assert.rejects(submitCode(f.deps, 'alice', id, '12345', false), /6-digit code/);   // not even sent
  assert.equal(f.sessions.length, 0);
  await submitCode(f.deps, 'alice', id, '123456', false);
  assert.equal(f.sessions.length, 1);
});

test('the challenge is single-use', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  const id = out.kind === 'verify' ? out.challengeId : '';
  await submitCode(f.deps, 'alice', id, '123456', false);
  await assert.rejects(submitCode(f.deps, 'alice', id, '123456', false), { message: GENERIC });
});

test('the challenge expires', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  f.advance(5 * 60e3 + 1);
  await assert.rejects(submitCode(f.deps, 'alice', out.kind === 'verify' ? out.challengeId : '', '123456', false), { message: GENERIC });
  assert.equal(f.sessions.length, 0);
});

test('an expired remembered device is not sent and is replaced by step 2', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  await submitCode(f.deps, 'alice', out.kind === 'verify' ? out.challengeId : '', '123456', true);
  f.advance(31 * 86400e3);
  assert.equal((await submitPassword(f.deps, 'alice', 'pw')).kind, 'verify');
});

test('a device the server no longer accepts is forgotten locally', async () => {
  const f = fakeServer();
  const out = await submitPassword(f.deps, 'alice', 'pw');
  await submitCode(f.deps, 'alice', out.kind === 'verify' ? out.challengeId : '', '123456', true);
  f.devices.clear();                                   // e.g. password changed (A-39)
  assert.equal((await submitPassword(f.deps, 'alice', 'pw')).kind, 'verify');
  assert.equal(f.store.has('alice'), false);
});

test('six-digit code check', () => {
  assert.equal(isSixDigitCode('123456'), true);
  assert.equal(isSixDigitCode(' 123456 '), true);
  assert.equal(isSixDigitCode('12345'), false);
  assert.equal(isSixDigitCode('12a456'), false);
});
