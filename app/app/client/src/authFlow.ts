// TD-5 two-step sign-in, as pure logic (no React Native imports) so it is unit-tested in __tests__/authFlow.test.ts.
// Step 1 "Sign in": username + password (+ the device token if this device is remembered, TD-1 (3)).
// Step 2 "Verify it's you": the 6-digit code from Duo Mobile + "Remember this device for 30 days", only when the
// server did not sign the user in at step 1. TD-5 (3): after Register, "Set up two-step verification" completes the
// setup challenge with the first code. The session token is only ever taken from a response that has one.
import type { LoginResponse, LoginStep } from './types';
import type { RememberedDevice } from './logic';

/** What the flow needs from the outside; the app passes the real API and device storage, tests pass fakes. */
export interface AuthDeps {
  login(b: { username: string; password: string; deviceToken?: string }): Promise<LoginStep>;
  verify(b: { challengeId: string; totpCode: string; rememberDevice: boolean }): Promise<LoginStep>;
  getRememberedDevice(username: string): Promise<RememberedDevice | null>;
  saveRememberedDevice(username: string, d: RememberedDevice): Promise<void>;
  forgetRememberedDevice(username: string): Promise<void>;
  now?: () => Date;
}

export type PasswordOutcome =
  | { kind: 'signedIn'; session: LoginResponse }
  | { kind: 'verify'; challengeId: string; expiresAt: string | null };

/** True only for exactly six digits (TD-5 (2) "6-digit code"). */
export function isSixDigitCode(code: string): boolean {
  return /^\d{6}$/.test(code.trim());
}

/** A response is a usable session only if it is not a challenge and carries a token (TD-5 (2)). */
export function toSession(r: LoginStep): LoginResponse | null {
  if (r.verificationRequired || !r.token || !r.user || !r.expiresAt) return null;
  return {
    token: r.token, expiresAt: r.expiresAt, user: r.user,
    deviceToken: r.deviceToken ?? null, trustedUntil: r.trustedUntil ?? null, rememberedDevice: !!r.rememberedDevice,
  };
}

/** Step 1. Sends the remembered device token (if any, still inside its 30 days); never sends a code. */
export async function submitPassword(deps: AuthDeps, username: string, password: string): Promise<PasswordOutcome> {
  const now = deps.now?.() ?? new Date();
  const stored = await deps.getRememberedDevice(username);
  const remembered = stored && stored.token && Date.parse(stored.trustedUntil) > now.getTime() ? stored : null;
  const r = await deps.login({ username, password, deviceToken: remembered?.token });
  const session = toSession(r);
  if (session) return { kind: 'signedIn', session };
  if (!r.verificationRequired || !r.challengeId) throw new Error('Unexpected sign-in response');
  // The server did not accept the stored device (expired, password changed, other user): drop it locally.
  if (remembered) await deps.forgetRememberedDevice(username);
  return { kind: 'verify', challengeId: r.challengeId, expiresAt: r.challengeExpiresAt ?? null };
}

/** Step 2 (and TD-5 (3) setup). Saves the new device token when "Remember this device" was ticked. */
export async function submitCode(deps: AuthDeps, username: string, challengeId: string, code: string,
                                 rememberDevice: boolean): Promise<LoginResponse> {
  if (!isSixDigitCode(code)) throw new Error('Enter the 6-digit code from Duo Mobile.');
  const r = await deps.verify({ challengeId, totpCode: code.trim(), rememberDevice });
  const session = toSession(r);
  if (!session) throw new Error('Unexpected verification response');
  if (session.deviceToken && session.trustedUntil) {
    await deps.saveRememberedDevice(username, { token: session.deviceToken, trustedUntil: session.trustedUntil });
  }
  return session;
}

/** True when the challenge's expiry has passed (client hint only; the server decides). */
export function challengeExpired(expiresAt: string | null, now: Date = new Date()): boolean {
  if (!expiresAt) return false;
  const t = Date.parse(expiresAt);
  return Number.isFinite(t) && t <= now.getTime();
}
