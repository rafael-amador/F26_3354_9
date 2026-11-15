// Shared UI kit for the CometFit mobile client (NFR Usability: simple, readable screens).
// Visual design: spec/03-team-decisions/TD-4.md — follows the team's Figma prototype in spec/05-ui-prototype/
// (dark green, orange accent, blue for water, warm off-white background, white rounded cards, headers with a
// subtitle and an initials avatar, bottom tab bar). Supersedes A-32. Exact hex values are read off the PNGs (A-44).
import React from 'react';
import {
  ActivityIndicator, Pressable, StyleSheet, Switch, Text, TextInput, TextInputProps, View, ViewStyle,
} from 'react-native';

export const colors = {
  // TD-4 palette (prototype PNGs)
  bg: '#F6F6F1',          // warm off-white background
  card: '#FFFFFF',
  text: '#1C1C1A',
  heading: '#1D4B35',     // dark green titles / brand
  muted: '#6B6E68',
  primary: '#1D4B35',     // dark green: streak card, "+ Log exercise", active tab text
  primaryText: '#FFFFFF',
  accent: '#EF8E3A',      // orange: "Check in at UREC", streak bar
  accentText: '#1D4B35',
  food: '#B5561D',        // food calories (orange-brown)
  foodBg: '#FDF1E6',
  water: '#2F6FED',       // water ml (blue)
  waterBg: '#EAF1FD',
  soft: '#E4ECE2',        // light green: avatar, active tab pill, "You" row
  chip: '#EEEEEA',        // neutral grey chips / inputs
  chipActive: '#1C1C1A',  // black "Today" segment
  border: '#E6E6E0',
  good: '#1D7A3E',
  warn: '#9A5B00',
  warnBg: '#FFF4E0',
  bad: '#B3362B',
  badBg: '#FCECEA',
  infoBg: '#EAF1FD',
  track: '#ECECE6',
};

export function Card({ title, right, children, style }: { title?: string; right?: string; children: React.ReactNode; style?: ViewStyle }) {
  return (
    <View style={[s.card, style]}>
      {title ? (
        <View style={s.cardHead}>
          <Text style={s.cardTitle}>{title}</Text>
          {right ? <Text style={s.muted}>{right}</Text> : null}
        </View>
      ) : null}
      {children}
    </View>
  );
}

export type ButtonKind = 'primary' | 'secondary' | 'danger' | 'accent' | 'food' | 'water' | 'outlineDanger';

export function Button({
  label, onPress, kind = 'primary', disabled, busy, testID, big,
}: { label: string; onPress: () => void; kind?: ButtonKind; disabled?: boolean; busy?: boolean; testID?: string; big?: boolean }) {
  const look: Record<ButtonKind, { bg: string; fg: string; border: string }> = {
    primary: { bg: colors.primary, fg: colors.primaryText, border: colors.primary },
    secondary: { bg: colors.chip, fg: colors.heading, border: colors.chip },
    danger: { bg: colors.bad, fg: '#FFF', border: colors.bad },
    accent: { bg: colors.accent, fg: colors.accentText, border: colors.accent },
    food: { bg: colors.foodBg, fg: colors.food, border: colors.foodBg },
    water: { bg: colors.waterBg, fg: colors.water, border: colors.waterBg },
    outlineDanger: { bg: colors.card, fg: colors.bad, border: colors.border },
  };
  const l = look[kind];
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || busy}
      style={({ pressed }) => [
        s.btn,
        big && s.btnBig,
        { backgroundColor: l.bg, borderColor: l.border, opacity: disabled ? 0.45 : pressed ? 0.8 : 1 },
      ]}
    >
      {busy ? <ActivityIndicator color={l.fg} /> : <Text style={[s.btnText, big && s.btnTextBig, { color: l.fg }]}>{label}</Text>}
    </Pressable>
  );
}

export function Field({ label, ...props }: { label: string } & TextInputProps) {
  return (
    <View style={{ marginBottom: 10 }}>
      <Text style={s.label}>{label}</Text>
      <TextInput placeholderTextColor={colors.muted} style={s.input} {...props} />
    </View>
  );
}

export function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <View style={s.toggleRow}>
      <Text style={[s.body, { flex: 1 }]}>{label}</Text>
      <Switch
        value={value}
        onValueChange={onChange}
        accessibilityLabel={label}
        trackColor={{ true: colors.primary, false: colors.track }}
        thumbColor="#FFF"
      />
    </View>
  );
}

/** Checkbox row (TD-5 (2) "Remember this device for 30 days" checkbox). */
export function Checkbox({ label, value, onChange, testID }: { label: string; value: boolean; onChange: (v: boolean) => void; testID?: string }) {
  return (
    <Pressable
      onPress={() => onChange(!value)}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: value }}
      accessibilityLabel={label}
      testID={testID}
      style={s.checkRow}
    >
      <View style={[s.checkBox, value && s.checkBoxOn]}>{value ? <Text style={s.checkMark}>✓</Text> : null}</View>
      <Text style={[s.body, { flex: 1 }]}>{label}</Text>
    </Pressable>
  );
}

export function ProgressBar({ value, color = colors.primary, track = colors.track }: { value: number; color?: string; track?: string }) {
  return (
    <View style={[s.track, { backgroundColor: track }]} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: Math.round(value * 100) }}>
      <View style={[s.fill, { width: `${Math.round(value * 100)}%`, backgroundColor: color }]} />
    </View>
  );
}

export function Banner({ kind, children }: { kind: 'info' | 'warn' | 'error' | 'good'; children: React.ReactNode }) {
  const bg = kind === 'warn' ? colors.warnBg : kind === 'error' ? colors.badBg : kind === 'good' ? colors.soft : colors.infoBg;
  const fg = kind === 'warn' ? colors.warn : kind === 'error' ? colors.bad : kind === 'good' ? colors.heading : colors.water;
  return (
    <View style={[s.banner, { backgroundColor: bg }]}>
      <Text style={{ color: fg, fontSize: 14 }}>{children}</Text>
    </View>
  );
}

/** UC-4 "Show Maintenance Notice" — shown instead of a disabled module; the rest of the app keeps working (FR-5). */
export function MaintenanceNotice({ name }: { name: string }) {
  return (
    <Card>
      <Text style={s.cardTitle}>{name} is under maintenance</Text>
      <Text style={s.body}>
        This part of CometFit has been temporarily turned off by the developers. Everything else in the app still works.
        Please check back later.
      </Text>
    </Card>
  );
}

export function Row({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }, style]}>{children}</View>;
}

/** Prototype stat: big number over a small grey label, left aligned (Activities "3 Workouts", Dashboard "18 Total visits"). */
export function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <View style={s.stat}>
      <Text style={[s.statValue, color ? { color } : null]}>{value}</Text>
      <Text style={s.muted}>{label}</Text>
    </View>
  );
}

// ---- TD-4 prototype pieces ---------------------------------------------------------------------------------------

/** Initials for the header avatar ("SC" in the prototype). */
export function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  return (
    <View
      style={[s.avatar, { width: size, height: size, borderRadius: size / 2 }]}
      accessibilityLabel={`Signed in as ${name}`}
    >
      <Text style={[s.avatarText, { fontSize: size * 0.36 }]}>{initials(name)}</Text>
    </View>
  );
}

/** Screen header: big green title, grey subtitle, initials avatar on the right (TD-4; every prototype screen). */
export function ScreenHeader({ title, subtitle, name, brand }: { title: string; subtitle?: string; name?: string; brand?: boolean }) {
  return (
    <View style={s.header}>
      {brand ? (
        <View style={s.headerTop}>
          <Text style={s.brand}>CometFit</Text>
          {name ? <Avatar name={name} /> : null}
        </View>
      ) : null}
      <View style={s.headerTop}>
        <View style={{ flex: 1 }}>
          <Text style={brand ? s.headline : s.title}>{title}</Text>
          {subtitle ? <Text style={s.subtitle}>{subtitle}</Text> : null}
        </View>
        {!brand && name ? <Avatar name={name} /> : null}
      </View>
    </View>
  );
}

/** Small uppercase grey section label ("FRIENDS LEADERBOARD", "ACCOUNT & PREFERENCES"). */
export function SectionLabel({ children }: { children: React.ReactNode }) {
  return <Text style={s.sectionLabel}>{children}</Text>;
}

/** Today / Week / Month pill switch (Food & Water prototype). */
export function Segmented<T extends string>({ options, value, onChange }: { options: { id: T; label: string }[]; value: T; onChange: (v: T) => void }) {
  return (
    <View style={s.segmented} accessibilityRole="tablist">
      {options.map((o) => {
        const on = o.id === value;
        return (
          <Pressable
            key={o.id}
            onPress={() => onChange(o.id)}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            style={[s.segment, on && s.segmentOn]}
            testID={`seg-${o.id}`}
          >
            <Text style={[s.segmentText, on && s.segmentTextOn]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Settings-style row with a chevron (Account prototype). Tapping toggles an inline panel. */
export function ListRow({
  title, subtitle, open, onPress, danger, last, testID,
}: { title: string; subtitle?: string; open?: boolean; onPress: () => void; danger?: boolean; last?: boolean; testID?: string }) {
  return (
    <Pressable onPress={onPress} style={[s.listRow, last && !open ? null : s.listRowLine]} accessibilityRole="button" testID={testID}>
      <View style={{ flex: 1 }}>
        <Text style={[s.listRowTitle, danger ? { color: colors.bad } : null]}>{title}</Text>
        {subtitle ? <Text style={s.small}>{subtitle}</Text> : null}
      </View>
      <Text style={s.chevron}>{open ? '⌄' : '›'}</Text>
    </Pressable>
  );
}

/** Ranked row of a leaderboard ("01  starryskies23  2,500 ml"); the student's own row is highlighted green. */
export function RankRow({ rank, name, value, color, me }: { rank: number; name: string; value: string; color?: string; me?: boolean }) {
  return (
    <View style={[s.rankRow, me && s.rankRowMe]}>
      <Text style={s.rankNo}>{String(rank).padStart(2, '0')}</Text>
      <Text style={[s.rankName, me && { fontWeight: '700' }]} numberOfLines={1}>{me ? 'You' : name}</Text>
      <Text style={[s.rankValue, { color: color ?? colors.heading }]}>{value}</Text>
    </View>
  );
}

/** Square letter badge of a log entry ("F" food in orange, "W" water in blue). */
export function LetterBadge({ letter, kind }: { letter: string; kind: 'food' | 'water' }) {
  return (
    <View style={[s.letter, { backgroundColor: kind === 'food' ? colors.foodBg : colors.waterBg }]}>
      <Text style={{ color: kind === 'food' ? colors.food : colors.water, fontWeight: '700' }}>{letter}</Text>
    </View>
  );
}

/** Thousands separator as in the prototype ("1,250"). */
export function fmt(n: number): string {
  return Math.round(n).toLocaleString('en-US');
}

export const s = StyleSheet.create({
  card: { backgroundColor: colors.card, borderRadius: 16, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: colors.border },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  cardTitle: { fontSize: 18, fontWeight: '700', color: colors.heading },
  body: { fontSize: 15, color: colors.text },
  muted: { fontSize: 13, color: colors.muted },
  small: { fontSize: 12, color: colors.muted },
  label: { fontSize: 13, color: colors.muted, marginBottom: 4 },
  input: {
    borderWidth: 1, borderColor: colors.border, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 11,
    fontSize: 15, color: colors.text, backgroundColor: '#FFF',
  },
  btn: { borderRadius: 12, paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center', borderWidth: 1, marginVertical: 4 },
  btnBig: { borderRadius: 14, paddingVertical: 16 },
  btnText: { fontSize: 15, fontWeight: '600' },
  btnTextBig: { fontSize: 17, fontWeight: '700' },
  track: { height: 6, borderRadius: 3, overflow: 'hidden', marginVertical: 8 },
  fill: { height: 6, borderRadius: 3 },
  banner: { padding: 12, borderRadius: 12, marginBottom: 10 },
  toggleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 6, gap: 8 },
  stat: { flex: 1, minWidth: 80, paddingVertical: 4 },
  statValue: { fontSize: 22, fontWeight: '700', color: colors.heading },
  listItem: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: colors.border },
  h1: { fontSize: 26, fontWeight: '800', color: colors.heading },
  // header
  header: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 10 },
  headerTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 22, fontWeight: '800', color: colors.heading, marginBottom: 14 },
  title: { fontSize: 28, fontWeight: '800', color: colors.heading },
  headline: { fontSize: 26, fontWeight: '800', color: colors.heading },
  subtitle: { fontSize: 14, color: colors.muted, marginTop: 4 },
  avatar: { backgroundColor: colors.soft, alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: colors.heading, fontWeight: '700' },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: colors.muted, letterSpacing: 0.6, marginTop: 6, marginBottom: 8, textTransform: 'uppercase' },
  // segmented
  segmented: { flexDirection: 'row', gap: 8, marginBottom: 14 },
  segment: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 18, backgroundColor: colors.chip },
  segmentOn: { backgroundColor: colors.chipActive },
  segmentText: { fontSize: 15, color: colors.text, fontWeight: '500' },
  segmentTextOn: { color: '#FFF', fontWeight: '600' },
  // list rows
  listRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12 },
  listRowLine: { borderBottomWidth: 1, borderBottomColor: colors.border },
  listRowTitle: { fontSize: 15, color: colors.heading, fontWeight: '500' },
  chevron: { fontSize: 20, color: colors.muted, marginLeft: 8 },
  // leaderboard
  rankRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, paddingHorizontal: 8, borderRadius: 10 },
  rankRowMe: { backgroundColor: colors.soft },
  rankNo: { width: 32, fontSize: 13, color: colors.muted },
  rankName: { flex: 1, fontSize: 15, color: colors.heading },
  rankValue: { fontSize: 14, fontWeight: '600' },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 8 },
  checkBox: { width: 22, height: 22, borderRadius: 6, borderWidth: 2, borderColor: colors.muted, alignItems: 'center', justifyContent: 'center', backgroundColor: '#FFF' },
  checkBoxOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  checkMark: { color: '#FFF', fontSize: 14, fontWeight: '800' },
  letter: { width: 28, height: 28, borderRadius: 6, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
});
