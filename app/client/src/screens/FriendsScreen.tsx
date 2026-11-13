// UC-3 View Friends' Progress — SD-3:
//   search username, send friend request -> sendRequest(targetId)          (UC-3 "Add / Accept Friend")
//   Friend: accept request -> acceptRequest(requestId)
//   Friend: choose fields to share -> setConsent(fields)                   (UC-3 "Set Data-Sharing Consent")
//   open Friends' Progress -> getFriendStats(friendId) -> consented stats only, leaderboard rank
//   plus "View Leaderboard", "Remove Friend"; FR-3 "search for a user and open their profile".
import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { Consent, Friend, FriendStats, PublicProfile } from '../types';
import { Banner, Button, Card, colors, Field, MaintenanceNotice, Row, s, Stat, Toggle } from '../ui';
import { useAsync } from '../useAsync';

export default function FriendsScreen({ me }: { me: number }) {
  const friendsQ = useAsync(() => api.friends(), []);
  const requestsQ = useAsync(() => api.requests(), []);
  const boardQ = useAsync(() => api.leaderboard(), []);
  const consentQ = useAsync(() => api.consent(), []);
  const [q, setQ] = useState('');
  const [results, setResults] = useState<PublicProfile[] | null>(null);
  const [profile, setProfile] = useState<PublicProfile | null>(null);
  const [stats, setStats] = useState<FriendStats | null>(null);
  const [msg, setMsg] = useState<{ kind: 'good' | 'error'; text: string } | null>(null);

  if (friendsQ.error?.isMaintenance) return <MaintenanceNotice name="Friends & Leaderboard" />;

  const err = (e: unknown) => setMsg({ kind: 'error', text: (e as ApiError).message });
  const reloadAll = () => {
    friendsQ.reload();
    requestsQ.reload();
    boardQ.reload();
  };

  async function search() {
    try {
      setResults(await api.searchUsers(q));
      setProfile(null);
    } catch (e) {
      err(e);
    }
  }
  async function openProfile(id: number) {
    try {
      setProfile(await api.userProfile(id));
    } catch (e) {
      err(e);
    }
  }
  async function add(p: PublicProfile) {
    try {
      await api.sendRequest(p.userId);
      setMsg({ kind: 'good', text: `Friend request sent to ${p.username}.` });
      requestsQ.reload();
      if (results) setResults(await api.searchUsers(q));
      if (profile?.userId === p.userId) await openProfile(p.userId);
    } catch (e) {
      err(e);
    }
  }
  async function accept(requestId: number) {
    try {
      const f = await api.acceptRequest(requestId);
      setMsg({ kind: 'good', text: `You and ${f.username} are now friends.` });
      reloadAll();
    } catch (e) {
      err(e);
    }
  }
  async function decline(requestId: number) {
    try {
      await api.declineRequest(requestId);
      requestsQ.reload();
    } catch (e) {
      err(e);
    }
  }
  async function openStats(f: Friend | { userId: number }) {
    try {
      setStats(await api.friendStats(f.userId));
    } catch (e) {
      err(e);
    }
  }
  async function remove(f: Friend) {
    try {
      await api.removeFriend(f.userId);
      setStats(null);
      setMsg({ kind: 'good', text: `Removed ${f.username} from your friends.` });
      reloadAll();
    } catch (e) {
      err(e);
    }
  }
  async function saveConsent(c: Consent) {
    try {
      consentQ.setData(await api.setConsent(c));
      setMsg({ kind: 'good', text: 'Sharing settings saved.' });
    } catch (e) {
      err(e);
    }
  }

  const incoming = (requestsQ.data ?? []).filter((r) => r.toUserId === me);
  const outgoing = (requestsQ.data ?? []).filter((r) => r.fromUserId === me);

  return (
    <ScrollView>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}

      {stats ? <StatsCard stats={stats} onClose={() => setStats(null)} /> : null}

      <Card title="Find people">
        <Row>
          <View style={{ flex: 3 }}>
            <Field label="Search by username" value={q} onChangeText={setQ} autoCapitalize="none" onSubmitEditing={search} testID="search" />
          </View>
          <View style={{ flex: 1 }}>
            <Button label="Search" onPress={search} disabled={q.trim().length < 2} />
          </View>
        </Row>
        {results && results.length === 0 ? <Text style={s.muted}>No students found.</Text> : null}
        {(results ?? []).map((p) => (
          <View key={p.userId} style={[s.listItem, { flexDirection: 'row', alignItems: 'center' }]}>
            <View style={{ flex: 1 }}>
              <Text style={s.body}>
                {p.username}
                {p.displayName ? ` · ${p.displayName}` : ''}
              </Text>
              <Text style={s.muted}>{relationLabel(p)}</Text>
            </View>
            <View style={{ width: 110 }}>
              <Button kind="secondary" label="Profile" onPress={() => openProfile(p.userId)} />
            </View>
          </View>
        ))}
        {profile ? (
          <View style={{ backgroundColor: colors.infoBg, borderRadius: 8, padding: 12, marginTop: 8 }}>
            <Text style={s.cardTitle}>{profile.displayName ?? profile.username}</Text>
            <Text style={s.body}>@{profile.username}</Text>
            {profile.memberSince ? <Text style={s.muted}>Member since {new Date(profile.memberSince).toLocaleDateString()}</Text> : null}
            {!profile.isPublic && profile.relation !== 'FRIEND' ? <Text style={s.muted}>This profile is private.</Text> : null}
            <Text style={s.muted}>{relationLabel(profile)}</Text>
            {profile.relation === 'NONE' ? <Button label="Add friend" onPress={() => add(profile)} testID="add-friend" /> : null}
            {profile.relation === 'REQUEST_RECEIVED' && profile.pendingRequestId ? (
              <Button label="Accept friend request" onPress={() => accept(profile.pendingRequestId!)} />
            ) : null}
            {profile.relation === 'FRIEND' ? <Button label="View progress" onPress={() => openStats(profile)} /> : null}
          </View>
        ) : null}
      </Card>

      <Card title="Friend requests">
        {incoming.length === 0 && outgoing.length === 0 ? <Text style={s.muted}>No pending requests.</Text> : null}
        {incoming.map((r) => (
          <View key={r.requestId} style={s.listItem}>
            <Text style={s.body}>{r.fromUsername} wants to be your friend</Text>
            <Row>
              <View style={{ flex: 1 }}>
                <Button label="Accept" onPress={() => accept(r.requestId)} testID="accept" />
              </View>
              <View style={{ flex: 1 }}>
                <Button kind="secondary" label="Decline" onPress={() => decline(r.requestId)} />
              </View>
            </Row>
          </View>
        ))}
        {outgoing.map((r) => (
          <Text key={r.requestId} style={s.muted}>
            Waiting for {r.toUsername} to accept
          </Text>
        ))}
      </Card>

      <Card title="Friends' progress">
        {(friendsQ.data ?? []).length === 0 ? <Text style={s.muted}>Add friends to compare progress.</Text> : null}
        {(friendsQ.data ?? []).map((f) => (
          <View key={f.userId} style={[s.listItem, { flexDirection: 'row', alignItems: 'center' }]}>
            <View style={{ flex: 1 }}>
              <Text style={s.body}>{f.displayName}</Text>
              <Text style={s.muted}>@{f.username}</Text>
            </View>
            <View style={{ width: 100 }}>
              <Button label="Stats" onPress={() => openStats(f)} />
            </View>
            <View style={{ width: 100 }}>
              <Button kind="secondary" label="Remove" onPress={() => remove(f)} />
            </View>
          </View>
        ))}
      </Card>

      <Card title="Leaderboard (points)">
        {(boardQ.data ?? []).map((e) => (
          <View key={e.userId} style={[s.listItem, { flexDirection: 'row' }]}>
            <Text style={[s.body, { width: 36, fontWeight: '700' }]}>#{e.rank}</Text>
            <Text style={[s.body, { flex: 1, fontWeight: e.me ? '700' : '400' }]}>
              {e.displayName}
              {e.me ? ' (you)' : ''}
            </Text>
            <Text style={s.body}>{e.points} pts</Text>
          </View>
        ))}
        <Text style={s.muted}>Friends appear here only if they share their visits or steps.</Text>
      </Card>

      {consentQ.data ? <ConsentCard consent={consentQ.data} onSave={saveConsent} /> : null}
    </ScrollView>
  );
}

function relationLabel(p: PublicProfile) {
  switch (p.relation) {
    case 'FRIEND': return 'Friend';
    case 'REQUEST_SENT': return 'Request sent';
    case 'REQUEST_RECEIVED': return 'Sent you a request';
    case 'SELF': return 'You';
    default: return 'Not a friend yet';
  }
}

function StatsCard({ stats, onClose }: { stats: FriendStats; onClose: () => void }) {
  const show = (v: number | null, goal: number | null, unit: string) =>
    v === null ? 'Not shared' : goal ? `${v} / ${goal} ${unit}` : `${v} ${unit}`;
  return (
    <Card title={`${stats.displayName}'s progress today`}>
      <Row>
        <Stat label="Calories" value={show(stats.caloriesToday, stats.calorieGoal, 'kcal')} />
        <Stat label="Water" value={show(stats.waterTodayMl, stats.waterGoalMl, 'ml')} />
      </Row>
      <Row>
        <Stat label="Steps" value={show(stats.stepsToday, stats.stepGoal, '')} />
        <Stat label="UREC visits (7 days)" value={stats.visitsThisWeek === null ? 'Not shared' : String(stats.visitsThisWeek)} />
      </Row>
      <Row>
        <Stat label="Points" value={stats.points === null ? 'Not shared' : String(stats.points)} />
        <Stat label="Leaderboard rank" value={stats.leaderboardRank ? `#${stats.leaderboardRank}` : '-'} />
      </Row>
      <Text style={s.muted}>Only the data your friend chose to share is shown.</Text>
      <Button kind="secondary" label="Close" onPress={onClose} />
    </Card>
  );
}

function ConsentCard({ consent, onSave }: { consent: Consent; onSave: (c: Consent) => void }) {
  const [c, setC] = useState<Consent>(consent);
  const set = (k: keyof Consent) => (v: boolean) => setC({ ...c, [k]: v });
  return (
    <Card title="What my friends can see">
      <Text style={s.muted}>Nothing is shared unless you turn it on here.</Text>
      <Toggle label="Calories" value={c.shareCalories} onChange={set('shareCalories')} />
      <Toggle label="Water intake" value={c.shareWater} onChange={set('shareWater')} />
      <Toggle label="Steps" value={c.shareSteps} onChange={set('shareSteps')} />
      <Toggle label="UREC visits" value={c.shareVisits} onChange={set('shareVisits')} />
      <Button label="Save sharing settings" onPress={() => onSave(c)} testID="save-consent" />
    </Card>
  );
}
