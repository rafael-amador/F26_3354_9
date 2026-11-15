// Shapes returned by the CometFit server (see app/server controllers; classes from Deliverable 1 §5.2).

export type Role = 'STUDENT' | 'DEVELOPER';

export interface UserView {
  userId: number;
  username: string;
  displayName: string;
  role: Role;
  isPublic: boolean;
  permissions: string[];
}
export interface LoginResponse {
  token: string; expiresAt: string; user: UserView;
  // TD-1 (3): set when this device was just remembered
  deviceToken: string | null; trustedUntil: string | null; rememberedDevice: boolean;
}
/**
 * TD-5: what POST /api/auth/login and /api/auth/verify return. verificationRequired = step 2 is needed and there is
 * NO session yet (challengeId only); otherwise token/user are set.
 */
export interface LoginStep {
  verificationRequired: boolean; challengeId: string | null; challengeExpiresAt: string | null;
  token: string | null; expiresAt: string | null; user: UserView | null;
  deviceToken: string | null; trustedUntil: string | null; rememberedDevice: boolean;
}
/** TD-5 (3): setupChallengeId is completed by the first 6-digit code on "Set up two-step verification". */
export interface Registration {
  user: UserView; totpSecret: string; otpauthUri: string; setupChallengeId: string; setupExpiresAt: string;
}

export interface Profile {
  userId: number; username: string; displayName: string; email: string; role: Role; isPublic: boolean;
  calorieGoal: number | null; waterGoalMl: number | null; stepGoal: number | null; bottleSizeMl: number | null;
}

export interface ModuleState { moduleId: 'NUTRITION' | 'ACTIVITY' | 'SOCIAL' | string; name: string; enabled: boolean }

// UC-1
export type MealSource = 'MANUAL' | 'PHOTO' | 'DINING_MENU';
// TD-6 §3: a macro is null when UTD Dining did not publish it ("not published"); manual / photo entries always have numbers.
export interface MealEntry {
  entryId: number | null; foodName: string; calories: number; proteinG: number | null; carbsG: number | null; fatG: number | null;
  source: MealSource; diningItemId: number | null; loggedAt: string;
}
export interface MealEntryRequest {
  foodName?: string; calories?: number; proteinG?: number; carbsG?: number; fatG?: number;
  source: MealSource; diningItemId?: number;
}
export interface FoodEstimate { foodName: string; calories: number; proteinG: number; carbsG: number; fatG: number; confidence: number }
export interface PhotoEstimate { draft: MealEntry; foodItems: FoodEstimate[]; notice: string }
// TD-6: building, serving size; null macros = "not published".
export interface DiningItem {
  itemId: number; name: string; location: string; building: string | null; serving: string | null; calories: number;
  proteinG: number | null; carbsG: number | null; fatG: number | null;
}
// TD-6 §2: GET /api/nutrition/menu/location-info
export interface DiningLocation { name: string; building: string | null; menu: string | null; source: string | null; macrosPublished: boolean }
// Water is stored in liters (team repo code LogWater.java); volumeMl is derived (class diagram WaterEntry.volumeMl).
export interface WaterEntry { entryId: number; amount: number; volumeMl: number; loggedAt: string }
export interface WaterSaveResult { saved: WaterEntry; summary: DailySummary; message: string }
export interface DailySummary {
  date: string; calories: number; calorieGoal: number; proteinG: number; carbsG: number; fatG: number;
  waterMl: number; waterLiters: number; waterGoalMl: number; bottleSizeMl: number; steps: number; stepGoal: number;
  latestHeartRate: number | null; mealCount: number; waterEntryCount: number; messages: string[];
  /** TD-6 §3: meals whose macros were not published (counted as 0 g in the macro totals, A-55). */
  mealsWithUnpublishedMacros?: number;
}
export interface SaveResult<T> { saved: T; summary: DailySummary }

// UC-2
export interface DeviceLink { linkId: number; deviceId: string; deviceType: string; platform: string; status: string; linkedAt: string }
export interface ActivityRecord { id: number; steps: number; heartRate: number | null; timestamp: string }
export interface PointTransaction { id: number; amount: number; reason: string; timestamp: string }
export interface PushStepsResult { record: ActivityRecord; stepsToday: number; stepGoal: number; pointsAwarded: PointTransaction | null; balance: number }
export interface RecVisit { visitId: number; readerId: string; timestamp: string }
export interface VisitResult { visit: RecVisit; pointsAwarded: PointTransaction | null; balance: number; message: string }
export interface VisitHistory { visits: RecVisit[]; visitsThisWeek: number; balance: number; transactions: PointTransaction[] }

// UC-3
export type Relation = 'SELF' | 'FRIEND' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'NONE';
export interface PublicProfile {
  userId: number; username: string; displayName: string | null; isPublic: boolean; relation: Relation;
  pendingRequestId: number | null; memberSince: string | null;
}
export interface FriendRequest { requestId: number; fromUserId: number; fromUsername: string; toUserId: number; toUsername: string; status: string; sentAt: string }
export interface Friend { userId: number; username: string; displayName: string; since: string }
export interface Consent { shareCalories: boolean; shareWater: boolean; shareSteps: boolean; shareVisits: boolean }
export interface FriendStats {
  userId: number; username: string; displayName: string;
  caloriesToday: number | null; calorieGoal: number | null; waterTodayMl: number | null; waterGoalMl: number | null;
  stepsToday: number | null; stepGoal: number | null; visitsThisWeek: number | null; points: number | null;
  leaderboardRank: number | null;
}
export interface LeaderboardEntry { rank: number; userId: number; username: string; displayName: string; points: number; me: boolean }

// UC-4
export interface AdminAccess { userId: number; username: string; adminRole: string; permissions: string[] }
export interface ServiceStatus { serviceName: string; up: boolean; lastCheckedAt: string | null; lastResponseMs: number; uptimePercent: number; checks: number }
export interface Incident { serviceName: string; startedAt: string; endedAt: string | null; durationSec: number; ongoing: boolean }
export interface UptimeReport {
  generatedAt: string; rangeHours: number; overallUptimePercent: number; allUp: boolean;
  services: ServiceStatus[]; incidents: Incident[]; checkIntervalSec: number;
}
export interface DowntimeAlert { raisedAt: string; serviceName: string; recovered: boolean; message: string }
