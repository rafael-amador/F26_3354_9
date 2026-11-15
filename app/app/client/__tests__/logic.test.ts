// Client unit tests (run with `npm test`, Node's built-in runner with TypeScript type stripping).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  formatDuration, goalWarnings, isEnabled, parseMacro, parsePositiveAmount, parsePositiveInt, progress, refillVolume, toLiters,
} from '../src/logic.ts';

// Team repo code LogWater.java / D2 §4.3 TC-1: 0.3 gal -> 1.13 L (truncated, not rounded)
test('water preview converts gallons to liters and truncates to two decimals like LogWater', () => {
  assert.equal(toLiters(0.3, false), 1.13);
  assert.equal(toLiters(0.5, true), 0.5);
  assert.equal(toLiters(0.759, true), 0.75);
  assert.equal(toLiters(0.29, true), 0.29);
  assert.equal(toLiters(1, false), 3.78);
});

// D2 §4.1: negative or missing values are rejected
test('water amount parsing accepts positive decimals only', () => {
  assert.equal(parsePositiveAmount('0.5'), 0.5);
  assert.equal(parsePositiveAmount('1,25'), 1.25);
  assert.equal(parsePositiveAmount('.3'), 0.3);
  assert.equal(parsePositiveAmount('2'), 2);
  assert.equal(parsePositiveAmount(''), null);
  assert.equal(parsePositiveAmount('-0.3'), null);
  assert.equal(parsePositiveAmount('0'), null);
  assert.equal(parsePositiveAmount('abc'), null);
});
import { FakeSmartDevice, seeded } from '../src/smartDevice.ts';
import { deviceKey, isStillRemembered, qrCellSize } from '../src/logic.ts';
import { qrMatrix } from '../src/qr.ts';

// TD-5 (3) "Show the QR code on every screen size" (replaces TD-1 (2) large-screen-only test): the QR plus its
// quiet zone fits a small phone and a laptop, and modules never get smaller than 2 px.
test('QR code fits every screen size', () => {
  const n = qrMatrix('otpauth://totp/CometFit:alice?secret=COMETFITALICE234&issuer=CometFit').length;
  for (const width of [320 - 80, 390 - 80, 440 - 80]) {   // phone widths minus screen + card padding
    const cell = qrCellSize(width, n);
    assert.ok(cell >= 2 && cell <= 6);
    assert.ok(cell * (n + 8) <= width, `QR ${cell * (n + 8)} px fits ${width} px`);
  }
  assert.equal(qrCellSize(10_000, n), 6);
  assert.equal(qrCellSize(10, n), 2);
});

// TD-1 (2): the QR encodes the otpauth setup link
test('QR matrix for the authenticator link is a square of modules with finder patterns', () => {
  const m = qrMatrix('otpauth://totp/CometFit:alice?secret=COMETFITALICE234&issuer=CometFit');
  assert.ok(m.length >= 21 && (m.length - 17) % 4 === 0);    // QR version size 17 + 4v
  assert.ok(m.every((row) => row.length === m.length));
  // top-left finder pattern: dark 7x7 border
  for (let i = 0; i < 7; i++) {
    assert.equal(m[0][i], true);
    assert.equal(m[i][0], true);
    assert.equal(m[6][i], true);
  }
  assert.equal(m[1][1], false);
});

// TD-1 (3): remembered device for 30 days
test('remembered device is used only until trustedUntil, per username', () => {
  const now = new Date('2026-11-14T12:00:00Z');
  assert.equal(isStillRemembered({ token: 'abc', trustedUntil: '2026-12-14T12:00:00Z' }, now), true);
  assert.equal(isStillRemembered({ token: 'abc', trustedUntil: '2026-11-14T11:59:59Z' }, now), false);
  assert.equal(isStillRemembered({ token: '', trustedUntil: '2026-12-14T12:00:00Z' }, now), false);
  assert.equal(isStillRemembered(null, now), false);
  assert.equal(deviceKey(' Alice '), deviceKey('alice'));
  assert.notEqual(deviceKey('alice'), deviceKey('bob'));
});

// TD-4 Food & Water (prototype 3-food-water.png): Week / Month dates, merged "Today's log", "cal left"
import { clockTime, lastNDates, leftOf, mergeTodayLog } from '../src/logic.ts';
test('Week and Month cover the last 7 / 30 local days ending today, oldest first', () => {
  const now = new Date(2026, 10, 15, 9, 0);   // Nov 15 2026 local
  assert.deepEqual(lastNDates(7, now), ['2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12', '2026-11-13', '2026-11-14', '2026-11-15']);
  const month = lastNDates(30, now);
  assert.equal(month.length, 30);
  assert.equal(month[0], '2026-10-17');
  assert.equal(month[29], '2026-11-15');
});
test("Today's log merges meals (F) and water (W), newest first", () => {
  const rows = mergeTodayLog(
    [{ entryId: 1, foodName: 'Lunch', calories: 650, proteinG: 1, carbsG: 2, fatG: 3, loggedAt: '2026-11-15T18:30:00Z', source: 'MANUAL' },
     { entryId: 2, foodName: 'Breakfast', calories: 400, proteinG: 0, carbsG: 0, fatG: 0, loggedAt: '2026-11-15T14:00:00Z' }],
    [{ entryId: 9, volumeMl: 750, loggedAt: '2026-11-15T17:45:00Z' }],
  );
  assert.deepEqual(rows.map((r) => r.title), ['Lunch', 'Water', 'Breakfast']);
  assert.deepEqual(rows.map((r) => r.kind), ['food', 'water', 'food']);
  assert.equal(rows[1].ml, 750);
  assert.equal(rows[0].calories, 650);
  assert.match(clockTime('2026-11-15T18:30:00Z'), /^\d{1,2}:30\s?(AM|PM)$/);
  assert.equal(leftOf(1250, 2000), 750);
  assert.equal(leftOf(2500, 2000), 0);
});

// TD-4 Social (prototype 4-social.png) + UC-3 consent: per-metric friends leaderboards
import { rankBoard } from '../src/logic.ts';
test('friends leaderboard: highest first, not-shared left out, ties share a rank', () => {
  const rows = rankBoard([
    { userId: 1, name: 'me', value: 1500, me: true },
    { userId: 2, name: 'starry', value: 2500, me: false },
    { userId: 3, name: 'nebula', value: 2100, me: false },
    { userId: 4, name: 'private', value: null, me: false },
  ]);
  assert.deepEqual(rows.map((r) => [r.rank, r.name]), [[1, 'starry'], [2, 'nebula'], [3, 'me']]);
  const tie = rankBoard([
    { userId: 1, name: 'a', value: 5, me: false },
    { userId: 2, name: 'me', value: 5, me: true },
    { userId: 3, name: 'c', value: 1, me: false },
  ]);
  assert.deepEqual(tie.map((r) => [r.rank, r.name]), [[1, 'me'], [1, 'a'], [3, 'c']]);
  assert.deepEqual(rankBoard([]), []);
});

// TD-4 Dashboard (prototype 2-dashboard.png) + FR-7 visit history: streak, week row, personal best (A-49)
import { streakStats } from '../src/logic.ts';
test('UREC streak: consecutive check-in days, one per day counts, week row Monday..Sunday', () => {
  const at = (y: number, m: number, d: number, h = 10) => new Date(y, m - 1, d, h).toISOString();
  const now = new Date(2026, 10, 13, 9);   // Friday Nov 13 2026, not yet checked in
  const visits = [at(2026, 11, 9), at(2026, 11, 10), at(2026, 11, 11), at(2026, 11, 12), at(2026, 11, 12, 18),
    at(2026, 11, 1), at(2026, 11, 2), at(2026, 11, 3), at(2026, 11, 4), at(2026, 11, 5), at(2026, 11, 6)];
  const st = streakStats(visits, now);
  assert.equal(st.current, 4);              // Mon..Thu, today not yet -> still alive
  assert.equal(st.checkedInToday, false);
  assert.equal(st.best, 6);                 // Nov 1..6
  assert.equal(st.total, 11);               // every visit counts in "Total visits"
  assert.equal(st.visitsThisWeek, 4);
  assert.equal(st.nextMilestone, 5);
  assert.deepEqual(st.week.map((w) => w.state), ['done', 'done', 'done', 'done', 'today', 'future', 'future']);
  const after = streakStats([...visits, at(2026, 11, 13, 8)], now);
  assert.equal(after.current, 5);
  assert.equal(after.nextMilestone, 10);
  assert.equal(after.week[4].state, 'done');
  // a missed day breaks the streak
  assert.equal(streakStats([at(2026, 11, 10), at(2026, 11, 11)], now).current, 0);
  assert.equal(streakStats([at(2026, 11, 10), at(2026, 11, 11)], now).week[0].state, 'missed');
  assert.equal(streakStats([], now).best, 0);
});

// UC-1 "View Daily Summary & Goals"
test('progress is clamped to 0..1 and safe for a zero goal', () => {
  assert.equal(progress(500, 2000), 0.25);
  assert.equal(progress(3000, 2000), 1);
  assert.equal(progress(-1, 2000), 0);
  assert.equal(progress(10, 0), 0);
});

// FR-6 / Proposal §4: water from bottle size x refills
test('refill volume uses the bottle size', () => {
  assert.equal(refillVolume(500, 1), 500);
  assert.equal(refillVolume(750, 0.5), 375);
  assert.equal(refillVolume(500, -2), 0);
  assert.equal(refillVolume(Number.NaN, 1), 0);
});

// FR-5 / SD-4 isEnabled(moduleId)
test('module flags: disabled module is reported, unknown or not-yet-loaded count as enabled', () => {
  const mods = [
    { moduleId: 'SOCIAL', name: 'Friends', enabled: false },
    { moduleId: 'NUTRITION', name: 'Nutrition', enabled: true },
  ];
  assert.equal(isEnabled(mods, 'SOCIAL'), false);
  assert.equal(isEnabled(mods, 'NUTRITION'), true);
  assert.equal(isEnabled(mods, 'ACTIVITY'), true);
  assert.equal(isEnabled(null, 'SOCIAL'), true);
});

// NFR Safety — client mirror of the server thresholds
test('unsafe goals produce warnings, safe goals none', () => {
  assert.deepEqual(goalWarnings(2000, 2000, 10000), []);
  assert.equal(goalWarnings(1000, 2000, 10000).length, 1);
  assert.match(goalWarnings(2000, 5000, 10000)[0], /excessive water/);
  assert.equal(goalWarnings(5000, 500, 40000).length, 3);
});

test('number parsing for form fields', () => {
  assert.equal(parsePositiveInt('250'), 250);
  assert.equal(parsePositiveInt(' 12 '), 12);
  assert.equal(parsePositiveInt('0'), null);
  assert.equal(parsePositiveInt('12.5'), null);
  assert.equal(parsePositiveInt('6000', 5000), null);
  assert.equal(parseMacro(''), 0);
  assert.equal(parseMacro('12.5'), 12.5);
  assert.equal(parseMacro('-1'), null);
  assert.equal(parseMacro('abc'), null);
});

test('durations are human readable', () => {
  assert.equal(formatDuration(42), '42s');
  assert.equal(formatDuration(125), '2m 5s');
  assert.equal(formatDuration(3720), '1h 2m');
});

// FR-4 / SD-2: the stub smart device produces plausible, repeatable readings
test('FakeSmartDevice readings are plausible and deterministic per seed', () => {
  const a = new FakeSmartDevice('SIM-1', 'simulated', 7);
  const b = new FakeSmartDevice('SIM-1', 'simulated', 7);
  for (let i = 0; i < 50; i++) {
    const ra = a.readSinceLastSync();
    const rb = b.readSinceLastSync();
    assert.equal(ra.steps, rb.steps);
    assert.ok(ra.steps >= 300 && ra.steps < 2000);
    assert.ok(ra.heartRate >= 62 && ra.heartRate < 122);
    assert.ok(!Number.isNaN(Date.parse(ra.timestamp)));
  }
  const r = seeded(1);
  const x = r();
  assert.ok(x >= 0 && x < 1);
});
