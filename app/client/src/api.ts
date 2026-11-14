// REST client for the CometFit server.
// Deliverable 1 §5.3.2: "Client (Mobile Application) <-> API Gateway using REST APIs".
// Every call after login carries the session token (SD-1 "authenticatedRequest(session)").
import { Platform } from 'react-native';
import type * as T from './types';

export const API_URL: string =
  process.env.EXPO_PUBLIC_API_URL ?? (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');

/** Error from the server. code MODULE_DISABLED = maintenance notice (FR-5, SD-4). */
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public moduleId?: string) {
    super(message);
  }
  get isMaintenance() {
    return this.code === 'MODULE_DISABLED';
  }
}

let token: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setToken(t: string | null) {
  token = t;
}
export function setUnauthorizedHandler(h: (() => void) | null) {
  onUnauthorized = h;
}

async function request<R>(method: string, path: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<R> {
  const headers: Record<string, string> = { ...(extraHeaders ?? {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload: BodyInit | undefined;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res: Response;
  try {
    res = await fetch(API_URL + path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, 'NETWORK', `Cannot reach the CometFit server at ${API_URL}`);
  }
  if (res.status === 204) return undefined as R;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    if (res.status === 401 && token && onUnauthorized) onUnauthorized();
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? `Request failed (${res.status})`, data?.moduleId);
  }
  return data as R;
}

const get = <R>(p: string) => request<R>('GET', p);
const post = <R>(p: string, b?: unknown) => request<R>('POST', p, b);
const put = <R>(p: string, b?: unknown) => request<R>('PUT', p, b);
const del = <R>(p: string) => request<R>('DELETE', p);

export const api = {
  // Log In (Password + TOTP) — Fig 4.3, NFR Security
  register: (b: { username: string; password: string; email: string; displayName?: string }) =>
    post<T.Registration>('/api/auth/register', b),
  login: (b: { username: string; password: string; totpCode: string }) => post<T.LoginResponse>('/api/auth/login', b),
  logout: () => post<void>('/api/auth/logout'),

  // FR-3 own profile, goals (NFR Safety warnings)
  profile: () => get<T.Profile>('/api/profile'),
  updateProfile: (b: { displayName?: string; isPublic?: boolean }) => put<T.Profile>('/api/profile', b),
  changePassword: (b: { oldPassword: string; newPassword: string }) => put<void>('/api/profile/password', b),
  setGoals: (b: { calorieGoal: number; waterGoalMl: number; stepGoal: number; bottleSizeMl?: number }) =>
    put<{ profile: T.Profile; warnings: string[] }>('/api/profile/goals', b),

  // FR-5: isEnabled(moduleId)
  modules: () => get<T.ModuleState[]>('/api/modules'),

  // UC-1 / SD-1 — NutritionController
  summary: () => get<T.DailySummary>('/api/nutrition/summary'),
  submitMealPhoto: (form: FormData) => post<T.PhotoEstimate>('/api/nutrition/meals/photo', form),
  menuLocations: () => get<string[]>('/api/nutrition/menu/locations'),
  getMenu: (location: string) => get<T.DiningItem[]>(`/api/nutrition/menu?location=${encodeURIComponent(location)}`),
  saveMealEntry: (b: T.MealEntryRequest) => post<T.SaveResult<T.MealEntry>>('/api/nutrition/meals', b),
  meals: () => get<T.MealEntry[]>('/api/nutrition/meals'),
  // team repo code LogWater.enter(userId, amount, isLiter): liters or gallons
  saveWaterEntry: (amount: number, isLiter: boolean) =>
    post<T.WaterSaveResult>('/api/nutrition/water', { amount, isLiter }),
  waterEntries: () => get<T.WaterEntry[]>('/api/nutrition/water'),

  // UC-2 / SD-2 — ActivityController
  linkDevice: (b: { deviceId: string; deviceType: string; platform: string; healthPermissionGranted: boolean }) =>
    post<T.DeviceLink>('/api/activity/devices', b),
  devices: () => get<T.DeviceLink[]>('/api/activity/devices'),
  unlinkDevice: (deviceId: string) => del<void>(`/api/activity/devices/${encodeURIComponent(deviceId)}`),
  pushSteps: (b: { deviceId: string; steps: number; heartRate?: number; timestamp?: string }) =>
    post<T.PushStepsResult>('/api/activity/steps', b),
  activityRecords: () => get<T.ActivityRecord[]>('/api/activity/records'),
  visitHistory: () => get<T.VisitHistory>('/api/activity/visits'),
  simulateSwipe: () => post<T.VisitResult>('/api/activity/stub-reader/swipe', {}),

  // UC-3 / SD-3 — SocialController
  searchUsers: (q: string) => get<T.PublicProfile[]>(`/api/social/users/search?q=${encodeURIComponent(q)}`),
  userProfile: (id: number) => get<T.PublicProfile>(`/api/social/users/${id}`),
  sendRequest: (targetId: number) => post<T.FriendRequest>('/api/social/requests', { targetId }),
  requests: () => get<T.FriendRequest[]>('/api/social/requests'),
  acceptRequest: (id: number) => post<T.Friend>(`/api/social/requests/${id}/accept`),
  declineRequest: (id: number) => post<void>(`/api/social/requests/${id}/decline`),
  friends: () => get<T.Friend[]>('/api/social/friends'),
  removeFriend: (id: number) => del<void>(`/api/social/friends/${id}`),
  consent: () => get<T.Consent>('/api/social/consent'),
  setConsent: (c: T.Consent) => put<T.Consent>('/api/social/consent', c),
  friendStats: (id: number) => get<T.FriendStats>(`/api/social/friends/${id}/stats`),
  leaderboard: () => get<T.LeaderboardEntry[]>('/api/social/leaderboard'),

  // UC-4 / SD-4 — AdminController
  adminAccess: () => get<T.AdminAccess>('/api/admin/access'),
  uptime: (hours = 24) => get<T.UptimeReport>(`/api/admin/uptime?hours=${hours}`),
  alerts: () => get<T.DowntimeAlert[]>('/api/admin/alerts'),
  checkNow: () => post<unknown>('/api/admin/uptime/check-now'),
  adminModules: () => get<T.ModuleState[]>('/api/admin/modules'),
  disableModule: (id: string) => post<T.ModuleState>(`/api/admin/modules/${id}/disable`),
  enableModule: (id: string) => post<T.ModuleState>(`/api/admin/modules/${id}/enable`),
  simulateOutage: (serviceName: string, down: boolean) =>
    post<{ simulatedDown: string[] }>('/api/admin/stub/outage', { serviceName, down }),
};
