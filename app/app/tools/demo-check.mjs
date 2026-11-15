#!/usr/bin/env node
// Read-only check of the seeded demo data against a RUNNING server (default http://localhost:8080).
// Unlike smoke.mjs it logs nothing (no meals, water, visits), so the demo data stays clean after a reset.
// Signs in dev, alice and bob (TD-5: password step, then TOTP on /verify) and checks TD-6 dining data and demo meals,
// then the TD-8 demo friends (six-person boards, alice neither first nor last, pending request from cosmocomet).
//   node tools/demo-check.mjs [baseUrl]
import { totp } from './totp.mjs';

const BASE = process.argv[2] ?? process.env.API_URL ?? 'http://localhost:8080';
const PASSWORD = 'CometFit123!';
let failures = 0;

async function call(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}
function check(name, cond, extra = '') {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? ' — ' + extra : ''}`);
  if (!cond) failures++;
}
async function login(username, secret) {
  const step1 = await call('POST', '/api/auth/login', null, { username, password: PASSWORD });
  const r = await call('POST', '/api/auth/verify', null, { challengeId: step1.data?.challengeId, totpCode: totp(secret), rememberDevice: false });
  check(`sign in ${username} (password + TOTP)`, step1.status === 200 && r.status === 200 && !!r.data?.token, `HTTP ${step1.status}/${r.status}`);
  return r.data?.token;
}

const alice = await login('alice', 'COMETFITALICE234');
const bob = await login('bob', 'COMETFITBOBBY567');
await login('dev', 'COMETFITDEVADMIN');

// TD-6
const info = await call('GET', '/api/nutrition/menu/location-info', alice);
check('TD-6 7 locations with buildings', info.status === 200 && info.data.length === 7 && info.data.every((l) => l.building),
  info.data?.map?.((l) => `${l.name} (${l.building})`).join(', '));
let total = 0;
for (const l of info.data ?? []) {
  const m = await call('GET', `/api/nutrition/menu?location=${encodeURIComponent(l.name)}`, alice);
  total += m.data?.length ?? 0;
}
check('TD-6 54 items', total === 54, `${total}`);
const old = await call('GET', '/api/nutrition/menu?location=Dining%20Hall', alice);
check('A-16 placeholder "Dining Hall" gone', old.status === 404, `HTTP ${old.status}`);

// TD-6 §5: demo meal history from real items (yesterday = a full day)
const y = new Date(Date.now() - 86400000);
const day = `${y.getFullYear()}-${String(y.getMonth() + 1).padStart(2, '0')}-${String(y.getDate()).padStart(2, '0')}`;
for (const [name, token] of [['alice', alice], ['bob', bob]]) {
  const meals = await call('GET', `/api/nutrition/meals?date=${day}`, token);
  check(`TD-6 ${name}'s meals on ${day} are UTD Dining items`,
    meals.status === 200 && meals.data.length > 0 && meals.data.every((m) => m.source === 'DINING_MENU' && m.diningItemId),
    meals.data?.map?.((m) => `${m.foodName} ${m.calories}${m.proteinG === null ? ' (macros not published)' : ''}`).join('; '));
}

// TD-8: six demo friends, alice neither first nor last on any board, pending request from cosmocomet
const SIX = ['alice', 'bob', 'starryskies23', 'nebulanomad', 'lunarlifter', 'orbitrunner'];
const secrets = { starryskies23: 'COMETFITSTARRY23', nebulanomad: 'COMETFITNEBULA45', lunarlifter: 'COMETFITLUNAR567',
  orbitrunner: 'COMETFITORBIT234', cosmocomet: 'COMETFITCOSMO345' };
for (const [u, s] of Object.entries(secrets)) await login(u, s);

const friends = await call('GET', '/api/social/friends', alice);
check('TD-8 alice has 5 friends', friends.status === 200 && friends.data.length === 5
  && SIX.filter((u) => u !== 'alice').every((u) => friends.data.some((f) => f.username === u)),
  friends.data?.map?.((f) => f.username).join(', '));

const points = await call('GET', '/api/social/leaderboard', alice);
const pts = points.data ?? [];
check('TD-8 points leaderboard lists six people, alice neither first nor last',
  pts.length === 6 && !pts[0].me && !pts[pts.length - 1].me,
  pts.map((e) => `${e.rank}. ${e.username} ${e.points}`).join(', '));

// Per-metric boards, built as the Social screen does (A-48): friends' consented stats + alice's own (via bob).
const stats = {};
for (const f of friends.data ?? []) stats[f.username] = (await call('GET', `/api/social/friends/${f.userId}/stats`, alice)).data;
const aliceId = (await call('GET', '/api/auth/me', alice)).data?.userId;
stats.alice = (await call('GET', `/api/social/friends/${aliceId}/stats`, bob)).data;
for (const [metric, label] of [['waterTodayMl', 'Water today'], ['caloriesToday', 'Calories today'], ['visitsThisWeek', 'UREC visits 7 days']]) {
  const order = Object.keys(stats).sort((a, b) => (stats[b]?.[metric] ?? -1) - (stats[a]?.[metric] ?? -1));
  check(`TD-8 ${label}: six people, alice neither first nor last`,
    order.length === 6 && order[0] !== 'alice' && order[5] !== 'alice' && order.every((u) => stats[u]?.[metric] > 0),
    order.map((u) => `${u} ${stats[u]?.[metric]}`).join(', '));
}

const reqs = await call('GET', '/api/social/requests', alice);
check('TD-8 pending friend request cosmocomet -> alice',
  reqs.status === 200 && reqs.data.some((r) => r.fromUsername === 'cosmocomet' && r.toUsername === 'alice' && r.status === 'PENDING'),
  reqs.data?.map?.((r) => `${r.fromUsername} -> ${r.toUsername} ${r.status}`).join('; '));

console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll demo checks passed');
process.exit(failures ? 1 : 0);
