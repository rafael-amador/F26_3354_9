// UC-1 Track Nutrition & Hydration — SD-1, all three alt branches plus water:
//   [Photo entry]       take photo -> submitMealPhoto(image) -> show estimate for review -> confirm / edit
//   [Dining menu item]  browse dining menu -> getMenu(location) -> menu list -> select item   (FR-8)
//   [Manual entry]      enter food name and calories                                           (FR-2)
//   then saveMealEntry(entry); "log water (bottle size / refill)" -> saveWaterEntry(volume)    (FR-6)
import * as ImagePicker from 'expo-image-picker';
import React, { useState } from 'react';
import { Platform, ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { parseMacro, parsePositiveInt, progress, refillVolume, shortTime } from '../logic';
import type { DailySummary, DiningItem, MealSource, PhotoEstimate } from '../types';
import { Banner, Button, Card, colors, Field, MaintenanceNotice, ProgressBar, Row, s } from '../ui';
import { useAsync } from '../useAsync';

type Mode = 'MANUAL' | 'PHOTO' | 'DINING_MENU';

export default function LogScreen({ onChanged }: { onChanged: () => void }) {
  const summaryQ = useAsync(() => api.summary(), []);
  const mealsQ = useAsync(() => api.meals(), []);
  const [mode, setMode] = useState<Mode>('MANUAL');
  const [msg, setMsg] = useState<{ kind: 'good' | 'error'; text: string } | null>(null);

  if (summaryQ.error?.isMaintenance) return <MaintenanceNotice name="Nutrition & Hydration" />;

  function saved(summary: DailySummary, text: string) {
    summaryQ.setData(summary);
    mealsQ.reload();
    setMsg({ kind: 'good', text });
    onChanged();
  }
  function failed(e: unknown) {
    setMsg({ kind: 'error', text: (e as ApiError).message });
  }

  const sum = summaryQ.data;
  return (
    <ScrollView>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}
      <WaterCard summary={sum} onSaved={saved} onError={failed} />

      <Card title="Log a meal">
        <Row>
          {(['MANUAL', 'PHOTO', 'DINING_MENU'] as Mode[]).map((m) => (
            <View key={m} style={{ flex: 1, minWidth: 100 }}>
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

      <Card title="Today's meals">
        {sum ? (
          <Text style={s.muted}>
            {sum.calories} / {sum.calorieGoal} kcal · P {sum.proteinG} g · C {sum.carbsG} g · F {sum.fatG} g
          </Text>
        ) : null}
        {(mealsQ.data ?? []).length === 0 ? <Text style={s.muted}>No meals logged yet today.</Text> : null}
        {(mealsQ.data ?? []).map((m) => (
          <View key={m.entryId ?? m.loggedAt} style={s.listItem}>
            <Text style={s.body}>
              {m.foodName} — {m.calories} kcal
            </Text>
            <Text style={s.muted}>
              {sourceLabel(m.source)} · P {m.proteinG} g · C {m.carbsG} g · F {m.fatG} g · {shortTime(m.loggedAt)}
            </Text>
          </View>
        ))}
      </Card>
    </ScrollView>
  );
}

function sourceLabel(src: MealSource) {
  return src === 'PHOTO' ? 'Photo' : src === 'DINING_MENU' ? 'UTD Dining' : 'Manual';
}

type SavedFn = (s: DailySummary, text: string) => void;
type ErrFn = (e: unknown) => void;

// ---- FR-6 water -----------------------------------------------------------------------------------------------
function WaterCard({ summary, onSaved, onError }: { summary: DailySummary | null; onSaved: SavedFn; onError: ErrFn }) {
  const [custom, setCustom] = useState('');
  const [busy, setBusy] = useState(false);
  const bottle = summary?.bottleSizeMl ?? 500;

  async function log(volumeMl: number) {
    setBusy(true);
    try {
      const r = await api.saveWaterEntry(volumeMl);
      onSaved(r.summary, `Logged ${volumeMl} ml of water.`);
      setCustom('');
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  const customMl = parsePositiveInt(custom, 5000);
  return (
    <Card title="Water">
      {summary ? (
        <>
          <Text style={s.body}>
            {summary.waterMl} / {summary.waterGoalMl} ml today
          </Text>
          <ProgressBar value={progress(summary.waterMl, summary.waterGoalMl)} color="#2A8BD8" />
          {summary.waterMl < summary.waterGoalMl ? (
            <Banner kind="warn">Your water intake is low today. Refill your bottle!</Banner>
          ) : null}
        </>
      ) : null}
      <Row>
        <View style={{ flex: 1 }}>
          <Button label={`+1 refill (${bottle} ml)`} onPress={() => log(refillVolume(bottle, 1))} busy={busy} testID="refill" />
        </View>
        <View style={{ flex: 1 }}>
          <Button kind="secondary" label={`+½ bottle (${refillVolume(bottle, 0.5)} ml)`} onPress={() => log(refillVolume(bottle, 0.5))} disabled={busy} />
        </View>
      </Row>
      <Row>
        <View style={{ flex: 2 }}>
          <Field label="Other amount (ml)" value={custom} onChangeText={setCustom} keyboardType="number-pad" />
        </View>
        <View style={{ flex: 1 }}>
          <Button label="Add" onPress={() => customMl && log(customMl)} disabled={!customMl || busy} />
        </View>
      </Row>
      <Text style={s.muted}>Bottle size can be changed in Profile → Goals.</Text>
    </Card>
  );
}

// ---- [Manual entry] (also used to confirm / edit a photo estimate) ---------------------------------------------
function ManualMeal({
  source, initial, onSaved, onError, onDone,
}: {
  source: MealSource;
  initial?: { foodName: string; calories: number; proteinG: number; carbsG: number; fatG: number };
  onSaved: SavedFn;
  onError: ErrFn;
  onDone?: () => void;
}) {
  const [name, setName] = useState(initial?.foodName ?? '');
  const [cal, setCal] = useState(initial ? String(initial.calories) : '');
  const [p, setP] = useState(initial ? String(round1(initial.proteinG)) : '');
  const [c, setC] = useState(initial ? String(round1(initial.carbsG)) : '');
  const [f, setF] = useState(initial ? String(round1(initial.fatG)) : '');
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
function DiningMenu({ onSaved, onError }: { onSaved: SavedFn; onError: ErrFn }) {
  const locations = useAsync(() => api.menuLocations(), []);
  const [location, setLocation] = useState<string | null>(null);
  const [items, setItems] = useState<DiningItem[] | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function open(loc: string) {
    setLocation(loc);
    setItems(null);
    try {
      setItems(await api.getMenu(loc));
    } catch (e) {
      onError(e);
    }
  }
  async function add(item: DiningItem) {
    setBusyId(item.itemId);
    try {
      const r = await api.saveMealEntry({ source: 'DINING_MENU', diningItemId: item.itemId });
      onSaved(r.summary, `Added "${item.name}" from ${item.location}.`);
    } catch (e) {
      onError(e);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <View style={{ marginTop: 8 }}>
      <Text style={s.muted}>Nutrition facts for UTD Dining locations.</Text>
      <Row>
        {(locations.data ?? []).map((l) => (
          <View key={l} style={{ flex: 1, minWidth: 120 }}>
            <Button kind={location === l ? 'primary' : 'secondary'} label={l} onPress={() => open(l)} />
          </View>
        ))}
      </Row>
      {location && !items ? <Text style={s.muted}>Loading menu…</Text> : null}
      {(items ?? []).map((it) => (
        <View key={it.itemId} style={[s.listItem, { flexDirection: 'row', alignItems: 'center' }]}>
          <View style={{ flex: 1 }}>
            <Text style={s.body}>{it.name}</Text>
            <Text style={s.muted}>
              {it.calories} kcal · Protein {it.proteinG} g · Carbs {it.carbsG} g · Fat {it.fatG} g
            </Text>
          </View>
          <View style={{ width: 90 }}>
            <Button kind="secondary" label="Add" onPress={() => add(it)} busy={busyId === it.itemId} />
          </View>
        </View>
      ))}
      <Text style={[s.muted, { marginTop: 6, color: colors.muted }]}>Menu data comes from UTD Dining (stubbed in this build).</Text>
    </View>
  );
}
