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
