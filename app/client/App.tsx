// CometFit mobile client (Expo / React Native; also runs in a browser for the demo — ASSUMPTIONS A-26).
// Deliverable 1 §5.3: client-server — this is the "Client (Mobile Application)"; every SD-1..SD-4 "Mobile App (View)"
// lifeline is one of the screens below. Developers get the "AdminDashboard (View)" of SD-4 instead.
import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { api, setToken, setUnauthorizedHandler } from './src/api';
import { isEnabled } from './src/logic';
import ActivityScreen from './src/screens/ActivityScreen';
import AdminDashboard from './src/screens/AdminDashboard';
import FriendsScreen from './src/screens/FriendsScreen';
import LoginScreen from './src/screens/LoginScreen';
import LogScreen from './src/screens/LogScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import TodayScreen from './src/screens/TodayScreen';
import type { LoginResponse, ModuleState } from './src/types';
import { colors, MaintenanceNotice } from './src/ui';

type Tab = 'today' | 'log' | 'activity' | 'friends' | 'profile';

const TABS: { id: Tab; label: string; module?: string; moduleName?: string }[] = [
  { id: 'today', label: 'Today', module: 'NUTRITION', moduleName: 'Nutrition & Hydration' },
  { id: 'log', label: 'Log', module: 'NUTRITION', moduleName: 'Nutrition & Hydration' },
  { id: 'activity', label: 'Activity', module: 'ACTIVITY', moduleName: 'Activity & UREC Visits' },
  { id: 'friends', label: 'Friends', module: 'SOCIAL', moduleName: 'Friends & Leaderboard' },
  { id: 'profile', label: 'Profile' },
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
        <AdminDashboard onLogout={logout} />
      ) : (
        <StudentApp session={session} onLogout={logout} />
      )}
    </SafeAreaView>
  );
}

function StudentApp({ session, onLogout }: { session: LoginResponse; onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('today');
  const [modules, setModules] = useState<ModuleState[] | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

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
      <View style={styles.header}>
        <Text style={styles.brand}>CometFit</Text>
        <Text style={styles.hello}>Hi, {session.user.displayName}</Text>
      </View>
      <View style={styles.content}>
        {disabled ? (
          <MaintenanceNotice name={current.moduleName!} />
        ) : tab === 'today' ? (
          <TodayScreen refreshKey={refreshKey} />
        ) : tab === 'log' ? (
          <LogScreen onChanged={bump} />
        ) : tab === 'activity' ? (
          <ActivityScreen onChanged={bump} />
        ) : tab === 'friends' ? (
          <FriendsScreen me={session.user.userId} />
        ) : (
          <ProfileScreen onLogout={onLogout} onChanged={bump} />
        )}
      </View>
      <View style={styles.tabs}>
        {TABS.map((t) => {
          const off = t.module && !isEnabled(modules, t.module);
          return (
            <Pressable key={t.id} onPress={() => setTab(t.id)} style={styles.tab} accessibilityRole="tab" testID={`tab-${t.id}`}>
              <Text style={[styles.tabText, tab === t.id && styles.tabActive, off ? styles.tabOff : null]}>
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
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', padding: 16, paddingBottom: 8 },
  brand: { fontSize: 22, fontWeight: '800', color: colors.primary },
  hello: { color: colors.muted },
  content: { flex: 1, paddingHorizontal: 12 },
  tabs: { flexDirection: 'row', borderTopWidth: 1, borderTopColor: colors.border, backgroundColor: colors.card },
  tab: { flex: 1, paddingVertical: 14, alignItems: 'center' },
  tabText: { color: colors.muted, fontWeight: '600' },
  tabActive: { color: colors.primary },
  tabOff: { color: colors.warn },
});
