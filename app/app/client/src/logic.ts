// Pure helpers used by the screens (unit-tested in __tests__/logic.test.ts).
import type { ModuleState } from './types';

/** Progress 0..1 toward a goal (UC-1 "View Daily Summary & Goals"). */
export function progress(value: number, goal: number): number {
  if (!goal || goal <= 0) return 0;
  return Math.max(0, Math.min(1, value / goal));
}

/** Water volume for N bottle refills (Proposal §4 "calculating water intake based on the number of refills"; SD-1). */
export function refillVolume(bottleSizeMl: number, refills: number): number {
  if (!Number.isFinite(bottleSizeMl) || !Number.isFinite(refills)) return 0;
  return Math.max(0, Math.round(bottleSizeMl * refills));
}

/** Liters per US gallon, as in the team's LogWater.java. */
export const LITERS_PER_GALLON = 3.785412;

/** Client preview of LogWater.enter's conversion: gallons -> liters, truncated to two decimals (server is authoritative). */
export function toLiters(amount: number, isLiter: boolean): number {
  const a = isLiter ? amount : amount * LITERS_PER_GALLON;
  return Math.floor(a * 100 + 1e-9) / 100;
}

/** Parses a positive decimal amount (water in L or gal); null when empty, not a number, or <= 0. */
export function parsePositiveAmount(text: string): number | null {
  const t = text.trim().replace(',', '.');
  if (!/^\d*(\.\d+)?$/.test(t) || t === '' || t === '.') return null;
  const n = parseFloat(t);
  return n > 0 ? n : null;
}

// ---- TD-1 sign-in helpers ----------------------------------------------------------------------------------------

/** TD-1 (2): "larger screen (for example a laptop browser)". Width in dp/px at which the QR code is shown (A-39). */
export const LARGE_SCREEN_MIN_WIDTH = 768;
export function isLargeScreen(width: number): boolean {
  return width >= LARGE_SCREEN_MIN_WIDTH;
}

/** A device remembered for one username (TD-1 (3)). */
export interface RememberedDevice { token: string; trustedUntil: string }

/** Storage key of the remembered device for a username (usernames are case-insensitive, A-4). */
export function deviceKey(username: string): string {
  return `cometfit.device.${username.trim().toLowerCase()}`;
}

/** True while the remembered device is inside its 30 days (the server re-checks; this only hides the code field). */
export function isStillRemembered(d: RememberedDevice | null, now: Date = new Date()): boolean {
  if (!d || !d.token) return false;
  const until = Date.parse(d.trustedUntil);
  return Number.isFinite(until) && until > now.getTime();
}

/** SD-4 "isEnabled(moduleId)" evaluated against the list fetched from the server. Unknown modules count as enabled. */
export function isEnabled(modules: ModuleState[] | null, moduleId: string): boolean {
  if (!modules) return true;
  const m = modules.find((x) => x.moduleId === moduleId);
  return m ? m.enabled : true;
}

/** Client-side mirror of the server's NFR Safety thresholds, so warnings show while typing (server is authoritative). */
export function goalWarnings(cal: number, water: number, steps: number): string[] {
  const w: string[] = [];
  if (cal < 1200) w.push('A daily calorie target below 1200 kcal may be unsafe.');
  if (cal > 4000) w.push('A daily calorie target above 4000 kcal may be unsafe.');
  if (water < 1000) w.push('A daily water target below 1000 ml may leave you dehydrated.');
  if (water > 4000) w.push('A daily water target above 4000 ml is excessive water intake.');
  if (steps > 30000) w.push('A daily step target above 30000 steps risks over-exertion.');
  return w;
}

/** Parses a positive integer from a text field; returns null when invalid. */
export function parsePositiveInt(text: string, max = Number.MAX_SAFE_INTEGER): number | null {
  if (!/^\d+$/.test(text.trim())) return null;
  const n = parseInt(text.trim(), 10);
  return n > 0 && n <= max ? n : null;
}

/** Parses a non-negative number (macros); empty string -> 0. */
export function parseMacro(text: string): number | null {
  const t = text.trim();
  if (t === '') return 0;
  if (!/^\d+(\.\d+)?$/.test(t)) return null;
  return parseFloat(t);
}

export function formatDuration(sec: number): string {
  if (sec < 60) return `${sec}s`;
  if (sec < 3600) return `${Math.floor(sec / 60)}m ${sec % 60}s`;
  return `${Math.floor(sec / 3600)}h ${Math.floor((sec % 3600) / 60)}m`;
}

export function shortTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
