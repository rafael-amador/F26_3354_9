// UC-3 View Friends' Progress — SD-3:
//   search username, send friend request -> sendRequest(targetId)          (UC-3 "Add / Accept Friend")
//   Friend: accept request -> acceptRequest(requestId)
//   Friend: choose fields to share -> setConsent(fields)                   (UC-3 "Set Data-Sharing Consent")
//   open Friends' Progress -> getFriendStats(friendId) -> consented stats only, leaderboard rank
//   plus "View Leaderboard", "Remove Friend"; FR-3 "search for a user and open their profile".
// TD-4: layout of spec/05-ui-prototype/4-social.png — "FRIENDS LEADERBOARD" with one ranked card per metric
// ("Water · today", "Calories logged · today", ...), the student's own row highlighted as "You", and the note
// "Calories reflect food logged, not a target to exceed." Per-metric boards are built from consented friend stats
// (A-48). Sharing settings are on the Account tab (TD-4 §2).
import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { rankBoard, type BoardInput } from '../logic';
import type { Friend, FriendStats, PublicProfile } from '../types';
import { Banner, Button, Card, colors, Field, fmt, MaintenanceNotice, RankRow, Row, s, SectionLabel, Stat } from '../ui';
import { useAsync } from '../useAsync';

export default function FriendsScreen({ me }: { me: number }) {
  const friendsQ = useAsync(() => api.friends(), []);
  const requestsQ = useAsync(() => api.requests(), []);
  const boardQ = useAsync(() => api.leaderboard(), []);
  // A-48: inputs of the per-metric boards — every friend's consented stats + my own numbers
  const statsQ = useAsync(async () => Promise.all((await api.friends()).map((f) => api.friendStats(f.userId).catch(() => null))), []);
  const mineQ = useAsync(async () => {
    const [sum, visits] = await Promise.all([api.summary().catch(() => null), api.visitHistory().catch(() => null)]);
    return { sum, visits };
  }, []);
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
    statsQ.reload();
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

  const incoming = (requestsQ.data ?? []).filter((r) => r.toUserId === me);
  const outgoing = (requestsQ.data ?? []).filter((r) => r.fromUserId === me);
  const friendStats = (statsQ.data ?? []).filter((x): x is FriendStats => x !== null);
  const board = (pick: (f: FriendStats) => number | null, mine: number | null | undefined): BoardInput[] => [
    ...friendStats.map((f) => ({ userId: f.userId, name: f.username, value: pick(f), me: false })),
    { userId: me, name: 'You', value: mine ?? null, me: true },
  ];
  const water = rankBoard(board((f) => f.waterTodayMl, mineQ.data?.sum?.waterMl));
  const calories = rankBoard(board((f) => f.caloriesToday, mineQ.data?.sum?.calories));
  const visits = rankBoard(board((f) => f.visitsThisWeek, mineQ.data?.visits?.visitsThisWeek));

  return (
    <ScrollView>
      {msg ? <Banner kind={msg.kind}>{msg.text}</Banner> : null}
      {stats ? <StatsCard stats={stats} onClose={() => setStats(null)} /> : null}

      <SectionLabel>Friends leaderboard</SectionLabel>
      <BoardCard title="Water · today" color={colors.water} rows={water} unit="ml" testID="board-water" />
      <BoardCard title="Calories logged · today" color={colors.food} rows={calories} unit="cal" testID="board-calories" />
      <BoardCard title="UREC visits · last 7 days" color={colors.heading} rows={visits} unit="visits" testID="board-visits" />
      {/* UC-3 "View Leaderboard" (server ranking by points, A-21) */}
      <Card title="Points" style={{ paddingHorizontal: 8 }}>
        {(boardQ.data ?? []).map((e) => (
          <RankRow key={e.userId} rank={e.rank} name={e.username} value={`${fmt(e.points)} pts`} me={e.me} />
        ))}
        <Text style={[s.small, { paddingHorizontal: 8, marginTop: 4 }]}>Points come from UREC visits and step goals. Friends appear only if they share visits or steps.</Text>
      </Card>
      <Text style={[s.small, { marginBottom: 14 }]}>Calories reflect food logged, not a target to exceed.</Text>

      <SectionLabel>Friends</SectionLabel>
      <Card>
        {(friendsQ.data ?? []).length === 0 ? <Text style={s.muted}>Add friends to compare progress.</Text> : null}
        {(friendsQ.data ?? []).map((f, i, all) => (
          <View key={f.userId} style={[st.row, i < all.length - 1 && st.line]}>
            <View style={{ flex: 1 }}>
              <Text style={st.name}>{f.displayName}</Text>
              <Text style={s.small}>@{f.username}</Text>
            </View>
            <View style={{ width: 84 }}>
              <Button kind="secondary" label="Stats" onPress={() => openStats(f)} />
            </View>
            <View style={{ width: 92, marginLeft: 6 }}>
              <Button kind="outlineDanger" label="Remove" onPress={() => remove(f)} />
            </View>
          </View>
        ))}
      </Card>

      <SectionLabel>Friend requests</SectionLabel>
      <Card>
        {incoming.length === 0 && outgoing.length === 0 ? <Text style={s.muted}>No pending requests.</Text> : null}
        {incoming.map((r) => (
          <View key={r.requestId} style={[st.row, st.line, { flexWrap: 'wrap' }]}>
            <Text style={[st.name, { flex: 1, minWidth: 160 }]}>{r.fromUsername} wants to be your friend</Text>
            <View style={{ width: 90 }}>
              <Button label="Accept" onPress={() => accept(r.requestId)} testID="accept" />
            </View>
            <View style={{ width: 90, marginLeft: 6 }}>
              <Button kind="secondary" label="Decline" onPress={() => decline(r.requestId)} />
            </View>
          </View>
        ))}
        {outgoing.map((r) => (
          <Text key={r.requestId} style={[s.muted, { paddingVertical: 6 }]}>
            Waiting for {r.toUsername} to accept
          </Text>
        ))}
      </Card>

      <SectionLabel>Find people</SectionLabel>
      <Card>
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
          <View key={p.userId} style={[st.row, st.line]}>
            <View style={{ flex: 1 }}>
              <Text style={st.name}>
                {p.username}
                {p.displayName ? ` · ${p.displayName}` : ''}
              </Text>
              <Text style={s.small}>{relationLabel(p)}</Text>
            </View>
            <View style={{ width: 96 }}>
              <Button kind="secondary" label="Profile" onPress={() => openProfile(p.userId)} />
            </View>
          </View>
        ))}
        {profile ? (
          <View style={{ backgroundColor: colors.soft, borderRadius: 12, padding: 12, marginTop: 8 }}>
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
    </ScrollView>
  );
}

function BoardCard({ title, color, rows, unit, testID }: {
  title: string; color: string; rows: ReturnType<typeof rankBoard>; unit: string; testID?: string;
}) {
  return (
    <View style={[s.card, { paddingHorizontal: 8 }]} testID={testID}>
      <Text style={[s.cardTitle, { color, paddingHorizontal: 8, marginBottom: 6, fontSize: 17 }]}>{title}</Text>
      {rows.length === 0 ? <Text style={[s.muted, { paddingHorizontal: 8 }]}>Nothing shared yet.</Text> : null}
      {rows.map((r) => (
        <RankRow key={r.userId} rank={r.rank} name={r.name} value={`${fmt(r.value)} ${unit}`} color={color} me={r.me} />
      ))}
    </View>
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
    v === null ? 'Not shared' : goal ? `${fmt(v)} / ${fmt(goal)} ${unit}` : `${fmt(v)} ${unit}`;
  return (
    <Card title={`${stats.displayName}'s progress today`}>
      <Row>
        <Stat label="Calories" value={show(stats.caloriesToday, stats.calorieGoal, 'cal')} color={colors.food} />
        <Stat label="Water" value={show(stats.waterTodayMl, stats.waterGoalMl, 'ml')} color={colors.water} />
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

const st = {
  row: { flexDirection: 'row' as const, alignItems: 'center' as const, paddingVertical: 8 },
  line: { borderBottomWidth: 1, borderBottomColor: colors.border },
  name: { fontSize: 15, color: colors.heading, fontWeight: '500' as const },
};
