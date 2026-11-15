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

/**
 * TD-5 (3): "Show the QR code on every screen size" (replaces TD-1 (2) / A-39's large-screen-only rule).
 * Pixel size of one QR module so the code plus its 4-module quiet zone fits the available width; never below 2 px
 * (still scannable) and at most 6 px.
 */
export function qrCellSize(availableWidth: number, modules: number): number {
  const fit = Math.floor(availableWidth / (modules + 8));
  return Math.max(2, Math.min(6, fit));
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

// ---- TD-6 UTD Dining helpers ----------------------------------------------------------------------------------------

/** TD-6 §3: what is shown for a macro UTD Dining did not publish. Never a made-up number. */
export const NOT_PUBLISHED = 'not published';

/** "29 g", or "not published" for null / undefined (a published 0 stays "0 g"). */
export function macroText(v: number | null | undefined): string {
  return v === null || v === undefined ? NOT_PUBLISHED : `${v} g`;
}

/**
 * One line of macros for a dining item or a logged meal. All three missing -> "Macros not published";
 * otherwise each macro with its own value or "not published". short = log style "P 29 g · C 41 g · F 18 g".
 */
export function macroLine(m: { proteinG: number | null; carbsG: number | null; fatG: number | null }, short = false): string {
  if (m.proteinG === null && m.carbsG === null && m.fatG === null) return `Macros ${NOT_PUBLISHED}`;
  const [p, c, f] = short ? ['P', 'C', 'F'] : ['Protein', 'Carbs', 'Fat'];
  return `${p} ${macroText(m.proteinG)} · ${c} ${macroText(m.carbsG)} · ${f} ${macroText(m.fatG)}`;
}

/** TD-6 §2: the building next to the location name, e.g. "Taco Bell Cantina · Parking Structure 3 (PS3)". */
export function diningLocationLabel(l: { name: string; building: string | null }): string {
  return l.building ? `${l.name} · ${l.building}` : l.name;
}

/** TD-6 §3 / A-55: note under the macro totals when some of today's meals have unpublished macros. */
export function unpublishedMacrosNote(count: number | undefined): string | null {
  if (!count) return null;
  return `${count} ${count === 1 ? 'item has' : 'items have'} macros not published by UTD Dining; counted as 0 g in these totals.`;
}

// ---- TD-4 Food & Water helpers ------------------------------------------------------------------------------------

/** Local calendar dates (YYYY-MM-DD) of the last n days, oldest first, ending today (Week = 7, Month = 30; A-47). */
export function lastNDates(n: number, now: Date = new Date()): string[] {
  const out: string[] = [];
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
  }
  return out;
}

/** One row of the prototype's "Today's log": F = food, W = water, newest first. */
export interface LogRow {
  kind: 'food' | 'water'; key: string; title: string; detail: string | null; at: string; calories?: number; ml?: number;
  source?: string;
}

export function mergeTodayLog(
  meals: { entryId: number | null; foodName: string; calories: number; proteinG: number | null; carbsG: number | null; fatG: number | null; loggedAt: string; source?: string }[],
  water: { entryId: number; volumeMl: number; loggedAt: string }[],
): LogRow[] {
  const rows: LogRow[] = [
    ...meals.map((m, i) => ({
      kind: 'food' as const, key: `m${m.entryId ?? i}`, title: m.foodName, at: m.loggedAt, calories: m.calories,
      detail: macroLine(m, true), source: m.source,
    })),
    ...water.map((w) => ({ kind: 'water' as const, key: `w${w.entryId}`, title: 'Water', at: w.loggedAt, ml: w.volumeMl, detail: null })),
  ];
  return rows.sort((a, b) => Date.parse(b.at) - Date.parse(a.at));
}

/** "12:30 PM" as in the prototype. */
export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

/** Amount left to a goal, never negative ("750 cal left"). */
export function leftOf(value: number, goal: number): number {
  return Math.max(0, Math.round(goal - value));
}

// ---- TD-4 Social helpers (prototype 4-social.png) ----------------------------------------------------------------

export interface BoardInput { userId: number; name: string; value: number | null; me: boolean }
export interface BoardRow extends BoardInput { rank: number; value: number }

/**
 * One "Friends leaderboard" card: highest first, people who do not share this value (null) are left out
 * (NFR Legislative-Privacy, UC-3 consent), equal values share a rank (same rule as the points leaderboard, A-21).
 */
export function rankBoard(entries: BoardInput[]): BoardRow[] {
  const shared = entries.filter((e): e is BoardInput & { value: number } => e.value !== null)
    .sort((a, b) => b.value - a.value || Number(b.me) - Number(a.me));
  let rank = 0;
  return shared.map((e, i) => {
    if (i === 0 || e.value !== shared[i - 1].value) rank = i + 1;
    return { ...e, rank };
  });
}

// ---- TD-4 Dashboard helpers (prototype 2-dashboard.png): UREC check-in streak from the visit history (A-49) -------

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

export type WeekDayState = 'done' | 'today' | 'missed' | 'future';
export interface StreakStats {
  current: number;        // consecutive days with a check-in, ending today (or yesterday if not yet checked in today)
  best: number;           // longest run ever ("Personal best")
  total: number;          // all visits ("Total visits")
  checkedInToday: boolean;
  visitsThisWeek: number; // Monday..Sunday of this week, days with a visit
  nextMilestone: number;  // next multiple of 5 above the current streak
  week: { label: string; state: WeekDayState }[];   // M T W T F S S
}

/** One check-in per day counts toward the streak (prototype note; A-20 points once per day). */
export function streakStats(visitTimes: string[], now: Date = new Date()): StreakStats {
  const days = new Set(visitTimes.map((t) => dayKey(new Date(t))));
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const checkedInToday = days.has(dayKey(today));
  let current = 0;
  for (let d = checkedInToday ? today : addDays(today, -1); days.has(dayKey(d)); d = addDays(d, -1)) current++;
  const sorted = [...days].sort();
  let best = 0;
  let run = 0;
  let prev: string | null = null;
  for (const k of sorted) {
    const [y, m, dd] = k.split('-').map(Number);
    run = prev !== null && dayKey(addDays(new Date(y, m - 1, dd), -1)) === prev ? run + 1 : 1;
    best = Math.max(best, run);
    prev = k;
  }
  const monday = addDays(today, -((today.getDay() + 6) % 7));
  const labels = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  const week = labels.map((label, i) => {
    const d = addDays(monday, i);
    const k = dayKey(d);
    const state: WeekDayState = days.has(k) ? 'done' : k === dayKey(today) ? 'today' : d < today ? 'missed' : 'future';
    return { label, state };
  });
  return {
    current, best, total: visitTimes.length, checkedInToday,
    visitsThisWeek: week.filter((w) => w.state === 'done').length,
    nextMilestone: (Math.floor(current / 5) + 1) * 5,
    week,
  };
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
