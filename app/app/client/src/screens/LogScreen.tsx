// UC-1 Track Nutrition & Hydration — SD-1, all three alt branches plus water:
//   [Photo entry]       take photo -> submitMealPhoto(image) -> show estimate for review -> confirm / edit
//   [Dining menu item]  browse dining menu -> getMenu(location) -> menu list -> select item   (FR-8)
//   [Manual entry]      enter food name and calories                                           (FR-2)
//   then saveMealEntry(entry); "log water (bottle size / refill)" -> saveWaterEntry(volume)    (FR-6)
//   Water input in liters or gallons: team repo code LogWater.java (stage 2).
// TD-4: layout of spec/05-ui-prototype/3-food-water.png — Today / Week / Month switch, Food and Water cards side by
// side (big number, "of goal", bar, "left", "+ Log food" / "+ <bottle> ml"), "Today's log" with F / W badges.
// TD-4 §3: water input stays liters/gallons (LogWater); totals are shown in ml as in the prototype. Week/Month: A-47.
import * as ImagePicker from 'expo-image-picker';
import React, { useState } from 'react';
import { Platform, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { api, ApiError } from '../api';
import {
  clockTime, diningLocationLabel, lastNDates, leftOf, macroLine, mergeTodayLog, parseMacro, parsePositiveAmount,
  parsePositiveInt, progress, refillVolume, toLiters, unpublishedMacrosNote,
} from '../logic';
import type { DailySummary, DiningItem, DiningLocation, MealSource, PhotoEstimate } from '../types';
import {
  Banner, Button, Card, colors, Field, fmt, LetterBadge, ListRow, MaintenanceNotice, ProgressBar, Row, s, Segmented,
} from '../ui';
import { useAsync } from '../useAsync';

type Mode = 'MANUAL' | 'PHOTO' | 'DINING_MENU';
type Range = 'today' | 'week' | 'month';

export default function LogScreen({ onChanged }: { onChanged: () => void }) {
  const [range, setRange] = useState<Range>('today');
  return (
    <ScrollView>
      <Segmented
        options={[{ id: 'today', label: 'Today' }, { id: 'week', label: 'Week' }, { id: 'month', label: 'Month' }]}
        value={range}
        onChange={setRange}
      />
      {range === 'today' ? <Today onChanged={onChanged} /> : <History days={range === 'week' ? 7 : 30} />}
    </ScrollView>
  );
}

function Today({ onChanged }: { onChanged: () => void }) {
  const summaryQ = useAsync(() => api.summary(), []);
  const mealsQ = useAsync(() => api.meals(), []);
  const waterQ = useAsync(() => api.waterEntries(), []);
  const [panel, setPanel] = useState<'none' | 'food' | 'water'>('none');
  const [mode, setMode] = useState<Mode>('MANUAL');
  const [msg, setMsg] = useState<{ kind: 'good' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const { width } = useWindowDimensions();

  if (summaryQ.error?.isMaintenance) return <MaintenanceNotice name="Nutrition & Hydration" />;

  function saved(summary: DailySummary, text: string) {
    summaryQ.setData(summary);
    mealsQ.reload();
    waterQ.reload();
    setMsg({ kind: 'good', text });
    onChanged();
  }
  function failed(e: unknown) {
    setMsg({ kind: 'error', text: (e as ApiError).message });
  }
  // FR-6 / Proposal §4 / A-8: one bottle refill, sent in liters (LogWater)
  async function refill(ml: number) {
    setBusy(true);
    try {
      const r = await api.saveWaterEntry(ml / 1000, true);
      saved(r.summary, r.message);
    } catch (e) {
      failed(e);
    } finally {
      setBusy(false);
    }
  }

  const sum = summaryQ.data;
  const bottle = sum?.bottleSizeMl ?? 500;
  const log = mergeTodayLog(mealsQ.data ?? [], waterQ.data ?? []);
  const narrow = width < 360;
  return (
    <View>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}
      {summaryQ.error && !summaryQ.error.isMaintenance ? <Banner kind="error">{summaryQ.error.message}</Banner> : null}
      {sum ? (
        <View style={{ flexDirection: narrow ? 'column' : 'row', gap: 12 }}>
          <View style={[s.card, { flex: 1 }]} testID="food-card">
            <Text style={st.cardLabel}>Food</Text>
            <Text style={st.big}>{fmt(sum.calories)}</Text>
            <Text style={s.muted}>cal</Text>
            <Text style={[s.muted, { marginTop: 10 }]}>of {fmt(sum.calorieGoal)} cal</Text>
            <ProgressBar value={progress(sum.calories, sum.calorieGoal)} color={colors.food} track={colors.foodBg} />
            <Text style={s.muted}>{fmt(leftOf(sum.calories, sum.calorieGoal))} cal left</Text>
            <View style={{ marginTop: 10 }}>
              <Button kind="food" label="+ Log food" onPress={() => setPanel(panel === 'food' ? 'none' : 'food')} testID="log-food" />
            </View>
          </View>
          <View style={[s.card, { flex: 1 }]} testID="water-card">
            <Text style={st.cardLabel}>Water</Text>
            <Text style={st.big}>{fmt(sum.waterMl)}</Text>
            <Text style={s.muted}>ml</Text>
            <Text style={[s.muted, { marginTop: 10 }]}>of {fmt(sum.waterGoalMl)} ml</Text>
            <ProgressBar value={progress(sum.waterMl, sum.waterGoalMl)} color={colors.water} track={colors.waterBg} />
            <Text style={s.muted}>{fmt(leftOf(sum.waterMl, sum.waterGoalMl))} ml left</Text>
            <View style={{ marginTop: 10 }}>
              <Button kind="water" label={`+ ${fmt(bottle)} ml`} onPress={() => refill(refillVolume(bottle, 1))} busy={busy} testID="refill" />
            </View>
            <Text style={[st.link, { color: colors.water }]} onPress={() => setPanel(panel === 'water' ? 'none' : 'water')} testID="more-water">
              {panel === 'water' ? 'Hide water options' : 'Other amount (L / gal)'}
            </Text>
          </View>
        </View>
      ) : null}

      {/* FR-6 verification: low-intake message from the daily summary */}
      {(sum?.messages ?? []).map((m) => (
        <Banner key={m} kind={m.startsWith('Water goal reached') ? 'good' : 'warn'}>{m}</Banner>
      ))}

      {panel === 'water' ? <WaterOptions bottle={bottle} onSaved={saved} onError={failed} /> : null}

      {panel === 'food' ? (
        <Card title="Log food">
          <Row>
            {(['MANUAL', 'PHOTO', 'DINING_MENU'] as Mode[]).map((m) => (
              <View key={m} style={{ flex: 1, minWidth: 96 }}>
                <Button
                  kind={mode === m ? 'primary' : 'secondary'}
                  label={m === 'MANUAL' ? 'Manual' : m === 'PHOTO' ? 'Photo (AI)' : 'Dining menu'}
                  onPress={() => setMode(m)}
                />
              </View>
            ))}
          </Row>
          {mode === 'MANUAL' ? <ManualMeal source="MANUAL" onSaved={saved} onError={failed} /> : null}
          {mode === 'PHOTO' ? <PhotoMeal onSaved={saved} onError={failed} /> : null}
          {mode === 'DINING_MENU' ? <DiningMenu onSaved={saved} onError={failed} /> : null}
        </Card>
      ) : null}

      <Card title="Today's log" right={`${log.length} ${log.length === 1 ? 'entry' : 'entries'}`}>
        {sum ? (
          <Text style={[s.small, { marginBottom: 4 }]}>
            Macros today: protein {sum.proteinG} g · carbs {sum.carbsG} g · fat {sum.fatG} g
          </Text>
        ) : null}
        {sum && unpublishedMacrosNote(sum.mealsWithUnpublishedMacros) ? (
          <Text style={[s.small, { marginBottom: 4, fontStyle: 'italic' }]}>{unpublishedMacrosNote(sum.mealsWithUnpublishedMacros)}</Text>
        ) : null}
        {log.length === 0 ? <Text style={s.muted}>Nothing logged yet today.</Text> : null}
        {log.map((r, i) => (
          <View key={r.key} style={[st.logRow, i < log.length - 1 && st.logLine]}>
            <LetterBadge letter={r.kind === 'food' ? 'F' : 'W'} kind={r.kind} />
            <View style={{ flex: 1 }}>
              <Text style={st.logTitle}>{r.title}</Text>
              <Text style={s.small}>
                {clockTime(r.at)}
                {r.source ? ` · ${sourceLabel(r.source as MealSource)}` : ''}
                {r.detail ? ` · ${r.detail}` : ''}
              </Text>
            </View>
            {r.kind === 'food' ? (
              <Text style={st.logValue}>{fmt(r.calories ?? 0)} cal</Text>
            ) : (
              <Text style={[st.logValue, { color: colors.water }]}>{fmt(r.ml ?? 0)} ml</Text>
            )}
          </View>
        ))}
      </Card>
    </View>
  );
}

// ---- Week / Month (A-47): one daily summary per day from the existing GET /api/nutrition/summary?date= ----------
function History({ days }: { days: number }) {
  const q = useAsync(() => Promise.all(lastNDates(days).map((d) => api.summary(d))), [days]);
  if (q.error?.isMaintenance) return <MaintenanceNotice name="Nutrition & Hydration" />;
  if (q.error) return <Banner kind="error">{q.error.message}</Banner>;
  if (!q.data) return <Text style={s.muted}>Loading…</Text>;
  const list = q.data;
  const cal = list.reduce((a, d) => a + d.calories, 0);
  const ml = list.reduce((a, d) => a + d.waterMl, 0);
  const waterDays = list.filter((d) => d.waterMl >= d.waterGoalMl).length;
  return (
    <View>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={[s.card, { flex: 1 }]}>
          <Text style={st.cardLabel}>Food</Text>
          <Text style={st.big}>{fmt(cal / days)}</Text>
          <Text style={s.muted}>cal / day on average</Text>
          <Text style={[s.muted, { marginTop: 8 }]}>{fmt(cal)} cal in {days} days</Text>
        </View>
        <View style={[s.card, { flex: 1 }]}>
          <Text style={st.cardLabel}>Water</Text>
          <Text style={st.big}>{fmt(ml / days)}</Text>
          <Text style={s.muted}>ml / day on average</Text>
          <Text style={[s.muted, { marginTop: 8 }]}>Goal reached on {waterDays} of {days} days</Text>
        </View>
      </View>
      <Card title={days === 7 ? 'Last 7 days' : 'Last 30 days'}>
        {[...list].reverse().map((d, i) => (
          <View key={d.date} style={[st.logRow, i < list.length - 1 && st.logLine]}>
            <Text style={[st.logTitle, { flex: 1 }]}>
              {new Date(`${d.date}T12:00:00`).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
            </Text>
            <Text style={[st.logValue, { width: 90, textAlign: 'right' }]}>{fmt(d.calories)} cal</Text>
            <Text style={[st.logValue, { width: 90, textAlign: 'right', color: colors.water }]}>{fmt(d.waterMl)} ml</Text>
          </View>
        ))}
      </Card>
    </View>
  );
}

const st = {
  cardLabel: { fontSize: 15, fontWeight: '600' as const, color: colors.text, marginBottom: 6 },
  big: { fontSize: 30, fontWeight: '700' as const, color: colors.text },
  link: { fontSize: 13, marginTop: 8, textAlign: 'center' as const, textDecorationLine: 'underline' as const },
  logRow: { flexDirection: 'row' as const, alignItems: 'center' as const, paddingVertical: 10 },
  logLine: { borderBottomWidth: 1, borderBottomColor: colors.border },
  logTitle: { fontSize: 15, color: colors.heading, fontWeight: '500' as const },
  logValue: { fontSize: 15, fontWeight: '700' as const, color: colors.text },
  menuPanel: { paddingVertical: 8, paddingHorizontal: 4, borderBottomWidth: 1, borderBottomColor: colors.border },
};

function sourceLabel(src: MealSource) {
  return src === 'PHOTO' ? 'Photo' : src === 'DINING_MENU' ? 'UTD Dining' : 'Manual';
}

type SavedFn = (s: DailySummary, text: string) => void;
type ErrFn = (e: unknown) => void;

// ---- FR-6 water -----------------------------------------------------------------------------------------------
// Team repo code LogWater.enter(userId, amount, isLiter): the student can enter liters or gallons; the server stores
// liters truncated to two decimals and answers "Added 1.13L to Jane Doe". Refill buttons = Proposal §4 bottle refills.
function WaterOptions({ bottle, onSaved, onError }: { bottle: number; onSaved: SavedFn; onError: ErrFn }) {
  const [custom, setCustom] = useState('');
  const [isLiter, setIsLiter] = useState(true);
  const [busy, setBusy] = useState(false);

  async function log(amount: number, liter: boolean) {
    setBusy(true);
    try {
      const r = await api.saveWaterEntry(amount, liter);
      onSaved(r.summary, r.message);
      setCustom('');
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  const customAmount = parsePositiveAmount(custom);
  const preview = customAmount !== null ? toLiters(customAmount, isLiter) : null;
  return (
    <Card title="Log water">
      <Button kind="water" label={`+ ½ bottle (${refillVolume(bottle, 0.5)} ml)`} onPress={() => log(refillVolume(bottle, 0.5) / 1000, true)} disabled={busy} />
      <Row>
        <View style={{ flex: 2 }}>
          <Field label={`Other amount (${isLiter ? 'liters' : 'gallons'})`} value={custom} onChangeText={setCustom} keyboardType="decimal-pad" testID="water-amount" />
        </View>
        <View style={{ flex: 1 }}>
          <Button kind={isLiter ? 'primary' : 'secondary'} label="L" onPress={() => setIsLiter(true)} testID="unit-liter" />
        </View>
        <View style={{ flex: 1 }}>
          <Button kind={!isLiter ? 'primary' : 'secondary'} label="gal" onPress={() => setIsLiter(false)} testID="unit-gallon" />
        </View>
      </Row>
      <Button
        kind="water"
        label={preview !== null ? `Add ${preview} L` : 'Add'}
        onPress={() => customAmount !== null && log(customAmount, isLiter)}
        disabled={customAmount === null || busy}
        testID="water-add"
      />
      <Text style={s.muted}>Water is saved in liters (gallons are converted). Bottle size can be changed in Account → Daily goals & units.</Text>
    </Card>
  );
}

// ---- [Manual entry] (also used to confirm / edit a photo estimate) ---------------------------------------------
function ManualMeal({
  source, initial, onSaved, onError, onDone,
}: {
  source: MealSource;
  initial?: { foodName: string; calories: number; proteinG: number | null; carbsG: number | null; fatG: number | null };
  onSaved: SavedFn;
  onError: ErrFn;
  onDone?: () => void;
}) {
  const [name, setName] = useState(initial?.foodName ?? '');
  const [cal, setCal] = useState(initial ? String(initial.calories) : '');
  // A photo estimate always has numbers; null (TD-6 type) would start as an empty field.
  const [p, setP] = useState(initial?.proteinG != null ? String(round1(initial.proteinG)) : '');
  const [c, setC] = useState(initial?.carbsG != null ? String(round1(initial.carbsG)) : '');
  const [f, setF] = useState(initial?.fatG != null ? String(round1(initial.fatG)) : '');
  const [busy, setBusy] = useState(false);

  const calories = cal.trim() === '0' ? 0 : parsePositiveInt(cal, 5000);
  const macros = [parseMacro(p), parseMacro(c), parseMacro(f)];
  const valid = name.trim().length > 0 && calories !== null && macros.every((x) => x !== null);

  async function save() {
    setBusy(true);
    try {
      const r = await api.saveMealEntry({
        source, foodName: name.trim(), calories: calories!, proteinG: macros[0]!, carbsG: macros[1]!, fatG: macros[2]!,
      });
      onSaved(r.summary, `Saved "${r.saved.foodName}" (${r.saved.calories} kcal).`);
      setName(''); setCal(''); setP(''); setC(''); setF('');
      onDone?.();
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={{ marginTop: 8 }}>
      <Field label="Food name" value={name} onChangeText={setName} testID="food-name" />
      <Field label="Calories (kcal)" value={cal} onChangeText={setCal} keyboardType="number-pad" testID="food-cal" />
      <Row>
        <View style={{ flex: 1 }}><Field label="Protein g" value={p} onChangeText={setP} keyboardType="decimal-pad" /></View>
        <View style={{ flex: 1 }}><Field label="Carbs g" value={c} onChangeText={setC} keyboardType="decimal-pad" /></View>
        <View style={{ flex: 1 }}><Field label="Fat g" value={f} onChangeText={setF} keyboardType="decimal-pad" /></View>
      </Row>
      <Button label={source === 'PHOTO' ? 'Confirm & save' : 'Save meal'} onPress={save} disabled={!valid} busy={busy} testID="save-meal" />
    </View>
  );
}

function round1(n: number) {
  return Math.round(n * 10) / 10;
}

// ---- [Photo entry] ---------------------------------------------------------------------------------------------
function PhotoMeal({ onSaved, onError }: { onSaved: SavedFn; onError: ErrFn }) {
  const [estimate, setEstimate] = useState<PhotoEstimate | null>(null);
  const [busy, setBusy] = useState(false);

  async function pick(camera: boolean) {
    try {
      if (camera && Platform.OS !== 'web') {
        const perm = await ImagePicker.requestCameraPermissionsAsync();
        if (!perm.granted) throw new ApiError(0, 'PERMISSION', 'Camera permission is needed to photograph a meal');
      }
      const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 0.6 };
      const res = camera && Platform.OS !== 'web' ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
      if (res.canceled || !res.assets?.length) return;
      const asset = res.assets[0];
      setBusy(true);
      const form = new FormData();
      const type = asset.mimeType ?? 'image/jpeg';
      if (Platform.OS === 'web') {
        const blob = await (await fetch(asset.uri)).blob();
        form.append('image', blob, asset.fileName ?? 'meal.jpg');
      } else {
        form.append('image', { uri: asset.uri, name: asset.fileName ?? 'meal.jpg', type } as unknown as Blob);
      }
      setEstimate(await api.submitMealPhoto(form));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={{ marginTop: 8 }}>
      <Text style={s.muted}>
        Take a photo of your meal and CometFit will estimate the calories and macros. You can edit the estimate before saving.
      </Text>
      <Row>
        <View style={{ flex: 1 }}>
          <Button label={Platform.OS === 'web' ? 'Choose meal photo' : 'Take photo'} onPress={() => pick(true)} busy={busy} />
        </View>
        {Platform.OS !== 'web' ? (
          <View style={{ flex: 1 }}>
            <Button kind="secondary" label="From gallery" onPress={() => pick(false)} disabled={busy} />
          </View>
        ) : null}
      </Row>
      {estimate ? (
        <View style={{ marginTop: 8 }}>
          <Banner kind="info">{estimate.notice}</Banner>
          {estimate.foodItems.map((f) => (
            <Text key={f.foodName} style={s.muted}>
              • {f.foodName}: {f.calories} kcal (confidence {Math.round(f.confidence * 100)}%)
            </Text>
          ))}
          <ManualMeal
            key={estimate.draft.foodName + estimate.draft.calories}
            source="PHOTO"
            initial={estimate.draft}
            onSaved={onSaved}
            onError={onError}
            onDone={() => setEstimate(null)}
          />
        </View>
      ) : null}
    </View>
  );
}

// ---- [Dining menu item] — FR-8 ---------------------------------------------------------------------------------
// TD-6: the 7 UTD Dining locations of spec/06-utd-dining, each with its building (settings-style rows as in the TD-4
// prototype); items show serving size, calories and the macros, or "not published" where UTD Dining has none.
function DiningMenu({ onSaved, onError }: { onSaved: SavedFn; onError: ErrFn }) {
  const locations = useAsync(() => api.menuLocationInfo(), []);
  const [location, setLocation] = useState<DiningLocation | null>(null);
  const [items, setItems] = useState<DiningItem[] | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function open(loc: DiningLocation) {
    if (location?.name === loc.name) {   // tap again to close, like the Account rows
      setLocation(null);
      setItems(null);
      return;
    }
    setLocation(loc);
    setItems(null);
    try {
      setItems(await api.getMenu(loc.name));
    } catch (e) {
      onError(e);
    }
  }
  async function add(item: DiningItem) {
    setBusyId(item.itemId);
    try {
      const r = await api.saveMealEntry({ source: 'DINING_MENU', diningItemId: item.itemId });
      onSaved(r.summary, `Added "${item.name}" from ${item.location} (${fmt(item.calories)} cal).`);
    } catch (e) {
      onError(e);
    } finally {
      setBusyId(null);
    }
  }

  const list = locations.data ?? [];
  return (
    <View style={{ marginTop: 8 }} testID="dining-menu">
      <Text style={s.muted}>Nutrition facts for UTD Dining locations. Pick a place to see its menu.</Text>
      {locations.error ? <Banner kind="error">{locations.error.message}</Banner> : null}
      {!locations.data && !locations.error ? <Text style={s.muted}>Loading locations…</Text> : null}
      {list.map((l, i) => {
        const isOpen = location?.name === l.name;
        return (
          <View key={l.name}>
            <ListRow
              title={l.name}
              subtitle={l.building ?? undefined}
              open={isOpen}
              last={i === list.length - 1}
              onPress={() => open(l)}
              testID={`dining-loc-${i}`}
            />
            {isOpen ? (
              <View style={st.menuPanel}>
                <Text style={s.small}>
                  {diningLocationLabel(l)}{l.menu ? ` · ${l.menu}` : ''}
                </Text>
                {!items ? <Text style={s.muted}>Loading menu…</Text> : null}
                {(items ?? []).map((it, k) => (
                  <View key={it.itemId} style={[st.logRow, k < (items ?? []).length - 1 && st.logLine]}>
                    <LetterBadge letter="F" kind="food" />
                    <View style={{ flex: 1 }}>
                      <Text style={st.logTitle}>{it.name}</Text>
                      <Text style={s.small}>
                        {it.serving ? `${it.serving} · ` : ''}{fmt(it.calories)} cal
                      </Text>
                      <Text style={[s.small, it.proteinG === null && it.carbsG === null && it.fatG === null ? { fontStyle: 'italic' } : null]}>
                        {macroLine(it)}
                      </Text>
                    </View>
                    <View style={{ width: 80 }}>
                      <Button kind="food" label="Add" onPress={() => add(it)} busy={busyId === it.itemId} />
                    </View>
                  </View>
                ))}
                {l.source ? <Text style={[s.small, { marginTop: 6 }]}>Source: {l.source}</Text> : null}
              </View>
            ) : null}
          </View>
        );
      })}
      <Text style={[s.muted, { marginTop: 6, color: colors.muted }]}>
        Menu data from UTD Dining (Dine On Campus), Monday 2026-11-16; served by a stub in this build, no live connection.
      </Text>
    </View>
  );
}
