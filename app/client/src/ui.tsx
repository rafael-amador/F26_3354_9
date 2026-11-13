// Small shared UI kit for the CometFit mobile client (NFR Usability: simple, readable screens).
import React from 'react';
import {
  ActivityIndicator, Pressable, StyleSheet, Switch, Text, TextInput, TextInputProps, View, ViewStyle,
} from 'react-native';

export const colors = {
  bg: '#F4F6F8',
  card: '#FFFFFF',
  text: '#1B2430',
  muted: '#5F6B7A',
  primary: '#1F5FAD',
  primaryText: '#FFFFFF',
  border: '#D8DEE6',
  good: '#1E7B34',
  warn: '#9A5B00',
  warnBg: '#FFF4E0',
  bad: '#B3261E',
  badBg: '#FDECEA',
  infoBg: '#E8F0FB',
  track: '#E3E8EE',
};

export function Card({ title, children, style }: { title?: string; children: React.ReactNode; style?: ViewStyle }) {
  return (
    <View style={[s.card, style]}>
      {title ? <Text style={s.cardTitle}>{title}</Text> : null}
      {children}
    </View>
  );
}

export function Button({
  label, onPress, kind = 'primary', disabled, busy, testID,
}: { label: string; onPress: () => void; kind?: 'primary' | 'secondary' | 'danger'; disabled?: boolean; busy?: boolean; testID?: string }) {
  const bg = kind === 'primary' ? colors.primary : kind === 'danger' ? colors.bad : colors.card;
  const fg = kind === 'secondary' ? colors.primary : colors.primaryText;
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || busy}
      style={({ pressed }) => [
        s.btn,
        { backgroundColor: bg, borderColor: kind === 'secondary' ? colors.primary : bg, opacity: disabled ? 0.5 : pressed ? 0.8 : 1 },
      ]}
    >
      {busy ? <ActivityIndicator color={fg} /> : <Text style={[s.btnText, { color: fg }]}>{label}</Text>}
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
      <Text style={s.body}>{label}</Text>
      <Switch value={value} onValueChange={onChange} accessibilityLabel={label} />
    </View>
  );
}

export function ProgressBar({ value, color = colors.primary }: { value: number; color?: string }) {
  return (
    <View style={s.track} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: Math.round(value * 100) }}>
      <View style={[s.fill, { width: `${Math.round(value * 100)}%`, backgroundColor: color }]} />
    </View>
  );
}

export function Banner({ kind, children }: { kind: 'info' | 'warn' | 'error' | 'good'; children: React.ReactNode }) {
  const bg = kind === 'warn' ? colors.warnBg : kind === 'error' ? colors.badBg : kind === 'good' ? '#E6F4EA' : colors.infoBg;
  const fg = kind === 'warn' ? colors.warn : kind === 'error' ? colors.bad : kind === 'good' ? colors.good : colors.primary;
  return (
    <View style={[s.banner, { backgroundColor: bg }]}>
      <Text style={{ color: fg }}>{children}</Text>
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

export function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={s.stat}>
      <Text style={s.statValue}>{value}</Text>
      <Text style={s.muted}>{label}</Text>
    </View>
  );
}

export const s = StyleSheet.create({
  card: { backgroundColor: colors.card, borderRadius: 12, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: colors.border },
  cardTitle: { fontSize: 17, fontWeight: '700', color: colors.text, marginBottom: 8 },
  body: { fontSize: 15, color: colors.text },
  muted: { fontSize: 13, color: colors.muted },
  label: { fontSize: 13, color: colors.muted, marginBottom: 4 },
  input: {
    borderWidth: 1, borderColor: colors.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10,
    fontSize: 15, color: colors.text, backgroundColor: '#FFF',
  },
  btn: { borderRadius: 8, paddingVertical: 11, paddingHorizontal: 16, alignItems: 'center', borderWidth: 1, marginVertical: 4 },
  btnText: { fontSize: 15, fontWeight: '600' },
  track: { height: 10, backgroundColor: colors.track, borderRadius: 5, overflow: 'hidden', marginVertical: 6 },
  fill: { height: 10, borderRadius: 5 },
  banner: { padding: 10, borderRadius: 8, marginBottom: 8 },
  toggleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 6 },
  stat: { flex: 1, minWidth: 90, alignItems: 'center', padding: 8 },
  statValue: { fontSize: 20, fontWeight: '700', color: colors.text },
  listItem: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.border },
  h1: { fontSize: 22, fontWeight: '800', color: colors.text },
});
