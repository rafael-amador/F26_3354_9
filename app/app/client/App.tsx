// CometFit mobile client (Expo / React Native; also runs in a browser for the demo — ASSUMPTIONS A-26).
// Deliverable 1 §5.3: client-server — this is the "Client (Mobile Application)"; every SD-1..SD-4 "Mobile App (View)"
// lifeline is one of the screens below. Developers get the "AdminDashboard (View)" of SD-4 instead.
// Look and navigation: spec/03-team-decisions/TD-4.md + spec/05-ui-prototype/ — five bottom tabs named
// Dashboard, Food & Water, Activities, Social, Account (TD-4 §1), features mapped onto them per TD-4 §2.
import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { api, setToken, setUnauthorizedHandler } from './src/api';
import { isEnabled } from './src/logic';
import ActivityScreen from './src/screens/ActivityScreen';
import AdminDashboard from './src/screens/AdminDashboard';
import DashboardScreen from './src/screens/DashboardScreen';
import FriendsScreen from './src/screens/FriendsScreen';
import LoginScreen from './src/screens/LoginScreen';
import LogScreen from './src/screens/LogScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import type { LoginResponse, ModuleState } from './src/types';
import { colors, MaintenanceNotice, ScreenHeader } from './src/ui';

type Tab = 'dashboard' | 'food' | 'activities' | 'social' | 'account';

// Titles and subtitles are the prototype's (spec/05-ui-prototype/2..6-*.png). The Dashboard mixes two modules
// (daily summary = NUTRITION, UREC check-in = ACTIVITY), so it is not guarded as a whole: each part shows its own
// maintenance notice (FR-5 "the rest of the app keeps working"; A-45).
const TABS: { id: Tab; label: string; title: string; subtitle: string; module?: string; moduleName?: string }[] = [
  { id: 'dashboard', label: 'Dashboard', title: 'Your effort adds up.', subtitle: 'One visit. One stronger day.' },
  { id: 'food', label: 'Food & Water', title: 'Food & Water', subtitle: 'Fuel your day. Stay hydrated.', module: 'NUTRITION', moduleName: 'Nutrition & Hydration' },
  { id: 'activities', label: 'Activities', title: 'Activities', subtitle: 'Make time for your next move.', module: 'ACTIVITY', moduleName: 'Activity & UREC Visits' },
  { id: 'social', label: 'Social', title: 'Social', subtitle: 'Small habits. Shared momentum.', module: 'SOCIAL', moduleName: 'Friends & Leaderboard' },
  { id: 'account', label: 'Account', title: 'Account', subtitle: 'Your profile, your preferences.' },
];

const MODULE_POLL_MS = 15_000;

export default function App() {
  const [session, setSession] = useState<LoginResponse | null>(null);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* session may already be gone */
    }
    setToken(null);
    setSession(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken(null);
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  function loggedIn(r: LoginResponse) {
    setToken(r.token);
    setSession(r);
  }

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      {!session ? (
        <LoginScreen onLoggedIn={loggedIn} />
      ) : session.user.role === 'DEVELOPER' ? (
        <AdminDashboard onLogout={logout} name={session.user.displayName} />
      ) : (
        <StudentApp session={session} onLogout={logout} />
      )}
    </SafeAreaView>
  );
}

function StudentApp({ session, onLogout }: { session: LoginResponse; onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('dashboard');
  const [modules, setModules] = useState<ModuleState[] | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [name, setName] = useState(session.user.displayName);

  // SD-4: Mobile App (Student) -> FeatureFlagService isEnabled(moduleId)
  useEffect(() => {
    let alive = true;
    const load = () => api.modules().then((m) => alive && setModules(m)).catch(() => undefined);
    load();
    const t = setInterval(load, MODULE_POLL_MS);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [tab]);

  const current = TABS.find((t) => t.id === tab)!;
  const bump = () => setRefreshKey((k) => k + 1);
  const disabled = current.module && !isEnabled(modules, current.module);

  return (
    <View style={styles.phone}>
      <ScreenHeader
        brand={tab === 'dashboard'}
        title={current.title}
        subtitle={current.subtitle}
        name={tab === 'account' ? undefined : name}
      />
      <View style={styles.content}>
        {disabled ? (
          <MaintenanceNotice name={current.moduleName!} />
        ) : tab === 'dashboard' ? (
          <DashboardScreen refreshKey={refreshKey} onChanged={bump} />
        ) : tab === 'food' ? (
          <LogScreen onChanged={bump} />
        ) : tab === 'activities' ? (
          <ActivityScreen onChanged={bump} />
        ) : tab === 'social' ? (
          <FriendsScreen me={session.user.userId} />
        ) : (
          <ProfileScreen username={session.user.username} onLogout={onLogout} onChanged={bump} onNameChanged={setName} />
        )}
      </View>
      <View style={styles.tabs} accessibilityRole="tablist">
        {TABS.map((t) => {
          const off = t.module && !isEnabled(modules, t.module);
          const on = tab === t.id;
          return (
            <Pressable
              key={t.id}
              onPress={() => setTab(t.id)}
              style={[styles.tab, on && styles.tabOn]}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              testID={`tab-${t.id}`}
            >
              <Text style={[styles.tabText, on && styles.tabTextOn, off ? styles.tabOff : null]} numberOfLines={1}>
                {t.label}
                {off ? ' ⚠' : ''}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  phone: { flex: 1, width: '100%', maxWidth: 480, alignSelf: 'center', backgroundColor: colors.bg },
  content: { flex: 1, paddingHorizontal: 20 },
  // TD-4 tab bar: white bar, active tab = light-green pill with bold dark-green label
  tabs: {
    flexDirection: 'row', borderTopWidth: 1, borderTopColor: colors.border, backgroundColor: colors.card,
    paddingHorizontal: 4, paddingVertical: 6, gap: 2,
  },
  tab: { flex: 1, paddingVertical: 12, alignItems: 'center', borderRadius: 20 },
  tabOn: { backgroundColor: colors.soft },
  tabText: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  tabTextOn: { color: colors.heading, fontWeight: '700' },
  tabOff: { color: colors.warn },
});
