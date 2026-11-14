#!/usr/bin/env node
// End-to-end smoke test against a RUNNING server (default http://localhost:8080) using the seeded demo accounts.
// Walks one happy path of each use case over real HTTP:  node tools/smoke.mjs [baseUrl]
// UC-1 (FR-2, FR-6, FR-8) · UC-2 (FR-4, FR-7) · UC-3 (FR-3) · UC-4 (FR-1, FR-5)
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
  const r = await call('POST', '/api/auth/login', null, { username, password: PASSWORD, totpCode: totp(secret) });
  check(`login ${username} (password + TOTP)`, r.status === 200, `HTTP ${r.status}`);
  return r.data?.token;
}

const health = await fetch(BASE + '/actuator/health').then((r) => r.json()).catch(() => null);
check('server is up', health?.status === 'UP', JSON.stringify(health));
if (!health) process.exit(1);

const alice = await login('alice', 'COMETFITALICE234');
const bob = await login('bob', 'COMETFITBOBBY567');
const dev = await login('dev', 'COMETFITDEVADMIN');

// TD-1 (3): "Remember this device for 30 days" -> next sign-in with username + password only
{
  const first = await call('POST', '/api/auth/login', null, { username: 'bob', password: PASSWORD, totpCode: totp('COMETFITBOBBY567'), rememberDevice: true });
  check('TD-1 remember this device', first.status === 200 && !!first.data?.deviceToken, `trusted until ${first.data?.trustedUntil}`);
  const again = await call('POST', '/api/auth/login', null, { username: 'bob', password: PASSWORD, deviceToken: first.data?.deviceToken });
  check('TD-1 remembered device signs in without a code', again.status === 200 && again.data?.rememberedDevice === true, `HTTP ${again.status}`);
}

// UC-1
let r = await call('POST', '/api/nutrition/meals', alice, { source: 'MANUAL', foodName: 'Smoke salad', calories: 320, proteinG: 8, carbsG: 20, fatG: 12 });
check('UC-1 FR-2 manual meal saved', r.status === 201, `today ${r.data?.summary?.calories} kcal`);
r = await call('POST', '/api/nutrition/water', alice, { volumeMl: 500 });
check('UC-1 FR-6 water logged', r.status === 201, r.data?.summary?.messages?.[0]);
// team repo code LogWater.java / D2 §4.3 TC-1: 0.3 gallons -> 1.13 L
r = await call('POST', '/api/nutrition/water', alice, { amount: 0.3, isLiter: false });
check('UC-1 FR-6 water in gallons stored as liters', r.status === 201 && r.data?.saved?.amount === 1.13, r.data?.message);
r = await call('POST', '/api/nutrition/water', alice, { amount: -0.3, isLiter: true });
check('UC-1 FR-6 negative water rejected', r.status === 400 && r.data?.message === 'Negative amount.', r.data?.message);
r = await call('GET', '/api/nutrition/menu?location=Dining%20Hall', alice);
check('UC-1 FR-8 dining menu with macros', r.status === 200 && r.data.length > 0, `${r.data?.length} items`);
if (r.status === 200) {
  r = await call('POST', '/api/nutrition/meals', alice, { source: 'DINING_MENU', diningItemId: r.data[0].itemId });
  check('UC-1 dining item added to meal log', r.status === 201, r.data?.saved?.foodName);
}

// UC-2
const deviceId = `SMOKE-WATCH-${Date.now()}`;
r = await call('POST', '/api/activity/devices', alice, { deviceId, deviceType: 'watch', platform: 'simulated', healthPermissionGranted: true });
check('UC-2 FR-4 device linked', r.status === 201, r.data?.status);
r = await call('POST', '/api/activity/steps', alice, { deviceId, steps: 1500, heartRate: 90 });
check('UC-2 FR-4 steps synced', r.status === 201, `${r.data?.stepsToday} steps today`);
r = await call('POST', '/api/activity/stub-reader/swipe', alice, {});
check('UC-2 FR-7 UREC swipe recorded', r.status === 201, r.data?.message);
r = await call('GET', '/api/activity/visits', alice);
check('UC-2 FR-7 visit history', r.status === 200 && r.data.visits.length > 0, `${r.data?.visits?.length} visits, ${r.data?.balance} points`);

// UC-3
const bobId = (await call('GET', '/api/auth/me', bob)).data.userId;
const aliceId = (await call('GET', '/api/auth/me', alice)).data.userId;
r = await call('GET', '/api/social/users/search?q=bob', alice);
check('UC-3 FR-3 search user', r.status === 200 && r.data.some((u) => u.username === 'bob'));
r = await call('GET', `/api/social/users/${bobId}`, alice);
check('UC-3 FR-3 open profile', r.status === 200, `relation ${r.data?.relation}`);
if (r.data?.relation === 'NONE') {
  const req = await call('POST', '/api/social/requests', alice, { targetId: bobId });
  check('UC-3 friend request sent', req.status === 201);
  const acc = await call('POST', `/api/social/requests/${req.data.requestId}/accept`, bob);
  check('UC-3 friend request accepted', acc.status === 200);
}
await call('PUT', '/api/social/consent', alice, { shareCalories: true, shareWater: true, shareSteps: true, shareVisits: true });
r = await call('GET', `/api/social/friends/${aliceId}/stats`, bob);
check('UC-3 friend stats (consented)', r.status === 200 && r.data.caloriesToday !== null, `alice ${r.data?.caloriesToday} kcal, rank #${r.data?.leaderboardRank}`);
r = await call('GET', '/api/social/leaderboard', bob);
check('UC-3 leaderboard', r.status === 200 && r.data.length >= 1, r.data?.map((e) => `${e.rank}.${e.username}:${e.points}`).join(' '));

// UC-4
r = await call('POST', '/api/admin/uptime/check-now', dev);
r = await call('GET', '/api/admin/uptime', dev);
check('UC-4 FR-1 uptime report', r.status === 200, `${r.data?.overallUptimePercent}% overall, allUp=${r.data?.allUp}`);
r = await call('POST', '/api/admin/modules/SOCIAL/disable', dev);
check('UC-4 FR-5 disable Friends module', r.status === 200 && r.data.enabled === false);
r = await call('GET', '/api/social/friends', bob);
check('UC-4 FR-5 student sees maintenance', r.status === 503 && r.data.code === 'MODULE_DISABLED');
r = await call('GET', '/api/nutrition/summary', bob);
check('UC-4 FR-5 other features still work', r.status === 200);
r = await call('POST', '/api/admin/modules/SOCIAL/enable', dev);
check('UC-4 FR-5 re-enable module', r.status === 200 && r.data.enabled === true);

console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll smoke checks passed');
process.exit(failures ? 1 : 0);
