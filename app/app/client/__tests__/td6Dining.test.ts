// TD-6 "Real UTD Dining locations and menu items": client display rules (FR-8, UC-1 "Browse Campus Dining Menu").
// Null macros are shown as "not published" (never a made-up number); the building is shown next to the location.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  diningLocationLabel, macroLine, macroText, mergeTodayLog, NOT_PUBLISHED, unpublishedMacrosNote,
} from '../src/logic.ts';

test('a null macro reads "not published"; a published 0 stays 0 g', () => {
  assert.equal(macroText(null), NOT_PUBLISHED);
  assert.equal(macroText(undefined), NOT_PUBLISHED);
  assert.equal(macroText(0), '0 g');
  assert.equal(macroText(29), '29 g');
});

test('calories-only item (Dining Hall West): "Macros not published"', () => {
  assert.equal(macroLine({ proteinG: null, carbsG: null, fatG: null }), 'Macros not published');
  assert.equal(macroLine({ proteinG: null, carbsG: null, fatG: null }, true), 'Macros not published');
});

test('partly published item (Chick-fil-A Fruit Cup: fat null) shows the numbers it has', () => {
  assert.equal(macroLine({ proteinG: 1, carbsG: 14, fatG: null }), 'Protein 1 g · Carbs 14 g · Fat not published');
  assert.equal(macroLine({ proteinG: 29, carbsG: 41, fatG: 18 }, true), 'P 29 g · C 41 g · F 18 g');
});

test('building is shown next to the location name', () => {
  assert.equal(diningLocationLabel({ name: 'Taco Bell Cantina', building: 'Parking Structure 3 (PS3)' }),
    'Taco Bell Cantina · Parking Structure 3 (PS3)');
  assert.equal(diningLocationLabel({ name: 'X', building: null }), 'X');
});

test("logged calories-only item appears in Today's log with macros not published", () => {
  const rows = mergeTodayLog(
    [{ entryId: 5, foodName: 'Italian Nachos', calories: 220, proteinG: null, carbsG: null, fatG: null,
       loggedAt: '2026-11-16T18:00:00Z', source: 'DINING_MENU' }],
    [],
  );
  assert.equal(rows[0].calories, 220);
  assert.equal(rows[0].detail, 'Macros not published');
});

test('macro totals say when unpublished macros were counted as 0 g', () => {
  assert.equal(unpublishedMacrosNote(0), null);
  assert.equal(unpublishedMacrosNote(undefined), null);
  assert.match(unpublishedMacrosNote(1)!, /^1 item has macros not published .* counted as 0 g/);
  assert.match(unpublishedMacrosNote(3)!, /^3 items have/);
});
