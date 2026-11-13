# Traceability

Paths: `S/` = `app/server/src/main/java/cometfit/`, `T/` = `app/server/src/test/java/cometfit/`,
`C/` = `app/client/`. Spec: D1 = Deliverable 1, Prop = Project Proposal.

## Functional requirements

| Req / UC | Spec source | Code | Test |
|---|---|---|---|
| FR-1 Track server uptime and current status | D1 §4.1 FR-1, §4.4 RTM (UC-4, "Server Component"), SD-4 | `S/services/MonitoringService` (checkServers, getUptimePercent, report, incidents), `S/services/ServerHealthProbes`, `S/controllers/AdminController` (healthCheck, getUptimeReport, alerts, check-now), `S/stubs/MonitoringAgentStub`, `S/domain/UptimeRecord`; client `C/src/screens/AdminDashboard.tsx` | `T/Uc4AdminTest` fr1_uptimePercentageAndServerStatus, externalMonitorMustPresentIntegrationKey, downtimeRaisesAlertAndIncident; `T/services/MonitoringServiceTest`; `app/tools/smoke.mjs` "UC-4 FR-1" |
| FR-2 Track calorie intake | D1 §4.1 FR-2, §4.4 RTM (UC-1, "Calorie intake, user"), SD-1 | `S/controllers/NutritionController` (saveMealEntry, meals, submitMealPhoto), `S/services/DailySummaryService`, `S/domain/MealEntry`, `MealSource`; client `C/src/screens/LogScreen.tsx` (Manual / Photo), `TodayScreen.tsx` | `T/Uc1NutritionTest` fr2_manualMealIsSavedAndStillThereWhenReopened, fr2_mealValidation, photoEntryReturnsAnEstimateThenSavesAfterConfirmation, dailySummaryTotalsEverythingAgainstGoals; smoke "UC-1 FR-2" |
| FR-3 View own profile and others' profiles / friends' statistics | D1 §4.1 FR-3, §4.4 RTM (UC-3, "Profile Component, users"), SD-3 | `S/controllers/ProfileController` (own profile, updateProfile, changePassword), `S/controllers/SocialController` (search, profile, requests, friends, consent, getFriendStats, leaderboard), `S/services/PrivacyService`; client `C/src/screens/ProfileScreen.tsx`, `FriendsScreen.tsx` | `T/Uc3SocialTest` (all 5 tests); `T/SecurityTest` changePasswordChecksTheOldPassword; smoke "UC-3" |
| FR-4 Connect smart devices | D1 §4.1 FR-4, §4.4 RTM (UC-2, "Smart device, Server"), SD-2, NFR Interoperability | `S/controllers/ActivityController` (linkDevice, unlinkDevice, pushSteps, records), `S/services/SmartDeviceGateway` + `S/stubs/FakeSmartDeviceGateway`, `S/domain/SmartDevice`, `DeviceLink`, `ActivityRecord`; client `C/src/smartDevice.ts` (FakeSmartDevice stub), `C/src/screens/ActivityScreen.tsx` | `T/Uc2ActivityTest` fr4_linkDeviceThenStepsSynchronise, pairingFailureAndUnlinkedDevicesAreRejected, reachingTheStepGoalEarnsPointsOncePerDay; `C/__tests__/logic.test.ts` FakeSmartDevice; smoke "UC-2 FR-4" |
| FR-5 Shut down portions of the app | D1 §4.1 FR-5, §4.4 RTM (UC-4, "Server, App"), SD-4 | `S/services/FeatureFlagService`, `S/domain/AppModule`, `S/gateway/ApiGatewayInterceptor` (503 MODULE_DISABLED), `S/controllers/AdminController` (disableModule, enableModule), `S/controllers/ModulesController` (isEnabled); client `C/App.tsx` (module guard), `C/src/ui.tsx` MaintenanceNotice, `AdminDashboard.tsx` | `T/Uc4AdminTest` fr5_disabledModuleShowsMaintenanceWhileOthersKeepWorking; `C/__tests__/logic.test.ts` module flags; smoke "UC-4 FR-5" |
| FR-6 Track water intake | D1 §4.1 FR-6, §4.4 RTM (UC-1, "Water intake, user"; low-intake message), SD-1, Prop §4 (bottle refills) | `S/controllers/NutritionController` (saveWaterEntry, waterEntries, summary), `S/services/DailySummaryService` (low-water message), `S/domain/WaterEntry`, `S/security/Student.bottleSizeMl`; client `LogScreen.tsx` WaterCard, `TodayScreen.tsx` | `T/Uc1NutritionTest` fr6_waterIsTrackedAndALowIntakeMessageIsShown; `C/__tests__/logic.test.ts` refill volume; smoke "UC-1 FR-6" |
| FR-7 Track visits to the rec center | D1 §4.1 FR-7, §4.4 RTM (UC-2, "Rec center swipes, server"), SD-2 | `S/controllers/ActivityController` (readerSwipe, recordVisit, visitHistory), `S/stubs/FakeUrecCardReader`, `S/services/PointsService`, `S/domain/RecVisit`, `UrecCardReader`, `PointAccount`, `PointTransaction`, `S/security/IntegrationKeyVerifier`; client `ActivityScreen.tsx` | `T/Uc2ActivityTest` fr7_cardSwipeRecordsVisitAndAwardsPoints, unknownReaderOrCardIsRejected; smoke "UC-2 FR-7" |
| FR-8 View UTD Dining nutritional info | D1 §4.1 FR-8, §4.4 RTM (UC-1, "Food Information"), SD-1 alt [Dining menu item] | `S/services/DiningMenuService` (fetchItems), `S/services/UtdDiningFeed` + `S/stubs/StubUtdDiningFeed`, `S/domain/DiningItem`, `S/controllers/NutritionController` (menuLocations, getMenu, DINING_MENU entries); client `LogScreen.tsx` DiningMenu | `T/Uc1NutritionTest` fr8_diningMenusListItemsWithMacrosPerLocation, diningItemBecomesAMealEntryWithTheItemsMacros; smoke "UC-1 FR-8" |

## Use cases (D1 §4.3 Fig 4.3 diagrams + use case descriptions) and sequence diagrams (D1 §5.1)

| Req / UC | Spec source | Code | Test |
|---|---|---|---|
| UC-1 Track Nutrition & Hydration (FR-2, 6, 8) | Fig 4.3 UC-1; SD-1 | Server: `NutritionController`, `DailySummaryService`, `DiningMenuService`, `AIFoodService`/`FakeAIFoodService`. Client: `TodayScreen`, `LogScreen` | `T/Uc1NutritionTest` (9 tests) |
| · Log Meal Calories (manual) | Fig 4.3 UC-1; SD-1 alt [Manual entry] | `NutritionController.saveMealEntry` (MANUAL); `LogScreen` ManualMeal | fr2_manualMeal… |
| · Log Meal by Photo (actor AI Food Recognition Service) | Fig 4.3 UC-1; SD-1 alt [Photo entry] submitMealPhoto → estimateMacros → review → confirm | `NutritionController.submitMealPhoto`, `S/stubs/FakeAIFoodService`; `LogScreen` PhotoMeal | photoEntryReturns…, photoMustBeAnImage |
| · Browse Campus Dining Menu / Add Dining Item to Meal Log / Maintain Dining Menu & Macros (actor UTD Dining) | Fig 4.3 UC-1; SD-1 alt [Dining menu item] getMenu → fetchItems | `NutritionController.getMenu`, `DiningMenuService.fetchItems`, `StubUtdDiningFeed`; `LogScreen` DiningMenu | fr8_…, diningItemBecomes… |
| · Log Water Intake | Fig 4.3 UC-1; SD-1 saveWaterEntry(volume) | `NutritionController.saveWaterEntry` | fr6_… |
| · View Daily Summary & Goals | Fig 4.3 UC-1; SD-1 "query daily totals vs goals" | `DailySummaryService.getDailySummary` (= Student.getDailySummary), `NutritionController.summary`; `TodayScreen` | dailySummaryTotals… |
| UC-2 Track Activity and Rec Center Visits (FR-4, 7) | Fig 4.3 UC-2; SD-2 | Server: `ActivityController`, `PointsService`, `SmartDeviceGateway`/`FakeSmartDeviceGateway`, `FakeUrecCardReader`. Client: `ActivityScreen`, `smartDevice.ts` | `T/Uc2ActivityTest` (5 tests) |
| · Connect Smart Device + Grant Health Permissions | Fig 4.3 UC-2; SD-2 "request health permissions → linkDevice → pair → save DeviceLink" | `ActivityController.linkDevice` (healthPermissionGranted required), `FakeSmartDeviceGateway.pair`, `DeviceLink.pair()`; `ActivityScreen` permission prompt | fr4_…, pairingFailure… |
| · Sync Steps & Movement Data (actor Smart Device) | Fig 4.3 UC-2; SD-2 loop [periodic sync] pushSteps | `ActivityController.pushSteps`; client auto-sync loop in `ActivityScreen` + `FakeSmartDevice` | fr4_… |
| · Check In at UREC (actor UREC Card Reader) | Fig 4.3 UC-2; SD-2 recordVisit → insert RecVisit | `ActivityController.readerSwipe` / `recordVisit`, `FakeUrecCardReader.sendSwipe` | fr7_… |
| · Earn Points | Fig 4.3 UC-2 («include» from Check In, «extend» from Sync); SD-2 awardPoints → update balance | `PointsService.awardPoints/getBalance`, `PointAccount.addPoints` | fr7_…, reachingTheStepGoal… |
| · View Visit History & Point Balance | Fig 4.3 UC-2; SD-2 "show visit history and points" | `ActivityController.visitHistory`; `ActivityScreen` | fr7_… |
| UC-3 View Friends' Progress (FR-3) | Fig 4.3 UC-3; SD-3 | Server: `SocialController`, `PrivacyService`, `ProfileController`. Client: `FriendsScreen`, `ProfileScreen` | `T/Uc3SocialTest` (5 tests) |
| · Add / Accept Friend (actors User, Friend) | Fig 4.3 UC-3; SD-3 sendRequest → FriendRequest (pending) → notify → acceptRequest → Friendship | `SocialController.sendRequest/pendingRequests/acceptRequest/declineRequest`, `FriendRequest.accept()/decline()` | friendRequestLifecycle, declinedRequest… |
| · Set Data-Sharing Consent | Fig 4.3 UC-3; SD-3 setConsent(fields) → save ConsentSettings | `SocialController.getConsent/setConsent`, `ConsentSettings`; `FriendsScreen` ConsentCard | friendStatsShowOnlyConsentedFields |
| · View Friend's Statistics | Fig 4.3 UC-3; SD-3 getFriendStats → filterByConsent → consented stats only | `SocialController.getFriendStats`, `PrivacyService.filterByConsent` | friendStatsShowOnlyConsentedFields |
| · View Leaderboard | Fig 4.3 UC-3 («extend» View Friend's Statistics); SD-3 "leaderboard rank" | `SocialController.leaderboard` | leaderboardRanksFriendsWhoShare |
| · Edit Public Profile / Remove Friend | Fig 4.3 UC-3 | `ProfileController.update` (User.updateProfile), `SocialController.removeFriend` | fr3_…, friendRequestLifecycle |
| UC-4 Monitor and Control System (FR-1, 5) | Fig 4.3 UC-4; SD-4 | Server: `AdminController`, `MonitoringService`, `FeatureFlagService`, `ModulesController`, `MonitoringAgentStub`, `LoggingAlertNotifier`, `OutageSimulator`. Client: `AdminDashboard`, module guard in `App.tsx` | `T/Uc4AdminTest` (5 tests) |
| · Authorize Admin (Permissions + TOTP) | Fig 4.3 UC-4; SD-4 login → authorize(admin, permissions) | `AuthService.authenticate/authorize`, `Developer.hasPermission`, `AdminController.authorize` (`/api/admin/access`) | developerIsAuthorizedByPermissions; `T/SecurityTest` permissionsList… |
| · View Uptime Dashboard / Track Server Uptime (actor Monitoring Service) | Fig 4.3 UC-4; SD-4 loop healthCheck → insert UptimeRecord; getUptimeReport | see FR-1 | fr1_…, externalMonitor… |
| · Raise Downtime Alert | Fig 4.3 UC-4 («extend» Track Server Uptime); SD-4 alt [service down] | `AdminController.healthCheck` → `AlertNotifier.sendDowntimeAlert` (`LoggingAlertNotifier` stub) | downtimeRaisesAlertAndIncident |
| · Disable / Re-enable App Module + Show Maintenance Notice (actor Student) | Fig 4.3 UC-4; SD-4 disableModule → setEnabled(false) → isEnabled → maintenance notice; enableModule | see FR-5 | fr5_… |
| Log In (Password + TOTP) — included by all UCs | Fig 4.3 UC-1..UC-4; SD-1 authenticatedRequest(session); NFR Security | `S/security/AuthService`, `Totp`, `Session`, `AccountService`, `S/controllers/AuthController`, `BaseController.requireSession`; client `LoginScreen.tsx`; `app/tools/totp.mjs` | `T/SecurityTest` (7 tests), `T/security/TotpTest` (RFC 6238 vectors) |

## Non-functional requirements (D1 §4.2)

| NFR | Code | Test / evidence |
|---|---|---|
| Security (password + TOTP, permissions list, encryption) | `AuthService`, `Totp`, `PasswordHasher` (BCrypt), `Permissions`, `BaseController.requirePermission`, `FieldEncryptor` + `EncryptedStringConverter` (email, TOTP secret), `IntegrationKeyVerifier` | `T/SecurityTest`, `T/security/TotpTest` |
| Safety (warn on unsafe goals) | `Student.setGoals/safetyWarnings`, `ProfileController.setGoals`, `DailySummaryService` messages; client `logic.goalWarnings`, `ProfileScreen` GoalsCard | `T/Uc1NutritionTest` unsafeGoalsAreSavedWithWarnings; `C/__tests__/logic.test.ts` |
| Legislative-Privacy (consent before sharing) | `ConsentSettings` (defaults false), `PrivacyService.filterByConsent`, leaderboard filter | `T/Uc3SocialTest` friendStatsShowOnlyConsentedFields, leaderboardRanksFriendsWhoShare |
| Legislative-Safety (general wellness, no medical claims) | Disclaimer on `LoginScreen`, `ProfileScreen`, photo-estimate notice in `NutritionController.submitMealPhoto` | manual (UI) |
| Reliability / Availability (99% uptime, maintenance) | Uptime tracking (FR-1), module isolation (FR-5) | `T/Uc4AdminTest` |
| Interoperability (smart devices via BLE / HealthKit / Health Connect) | `SmartDeviceGateway` (server), `SmartDeviceAdapter` (client) interfaces with stubs | `T/Uc2ActivityTest` |
| Operational / Development (cross-platform mobile app, GitHub) | Expo / React Native client `C/` (A-26) | `npm run typecheck`, `npx expo export --platform web` |
| Usability (basic tasks within 5 minutes) | One-tap water refill, three meal-entry modes, 5-tab layout | manual (demo script in `app/README.md`) |
| Performance / Space (<1 s responses; ≤150 MB app) | Indexed queries per student/day (V1 schema); client web bundle ≈ 470 KB | not load-tested |

## Design artefacts

| Spec artefact | Code |
|---|---|
| D1 §5.2 class diagram — Users and Security: AuthService, Session, User, Student, Developer | `S/security/*` (one class each) |
| D1 §5.2 — Domain Data: UrecCardReader, RecVisit, FriendRequest, Friendship, WaterEntry, ActivityRecord, SmartDevice, DeviceLink, MealEntry, MealSource, DiningItem, ConsentSettings, PointAccount, PointTransaction, AppModule, UptimeRecord | `S/domain/*` (one entity + repository each); tables in `app/server/src/main/resources/db/migration/V1__schema.sql` |
| D1 §5.2 — Controllers: BaseController, SocialController, AdminController, ActivityController, NutritionController | `S/controllers/*` (method names match the diagram) |
| D1 §5.2 — Services: PrivacyService, FeatureFlagService, MonitoringService, PointsService, AIFoodService, DiningMenuService | `S/services/*` |
| D1 §5.1 SD-1..SD-4 lifelines "Mobile App (View)" / "AdminDashboard (View)" | `C/src/screens/*`, `C/App.tsx` |
| D1 §5.3.2 architecture: Client ↔ API Gateway (REST) ↔ Auth Gateway ↔ Social / Activity / Nutrition / Administrator services ↔ Encrypted Database; External: UTD Dining Menus, UREC Card Reader; Smart device → Client via Bluetooth | `C/src/api.ts`; `S/gateway/*`; `S/security/*`; `S/controllers` + `S/services`; PostgreSQL via `app/docker-compose.yml`; `S/stubs/*`; `C/src/smartDevice.ts` |
