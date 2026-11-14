# Traceability

Paths: `S/` = `app/server/src/main/java/cometfit/`, `T/` = `app/server/src/test/java/cometfit/`,
`C/` = `app/client/`, `M/` = `app/server/src/main/resources/db/migration/`.
Spec: Prop = Project Proposal, D1 = Deliverable 1, D2 = Deliverable 2 draft (`spec/02-deliverable-2-draft/`),
TRC = team repo code (`spec/04-team-repo-code/`), TD-n = team decisions (`spec/03-team-decisions/`).
D2 §2 carries D1 forward unchanged: D1 §4.x = D2 §2.4.x, D1 §5.x = D2 §2.5.x (diagrams pixel-identical).

## Sources

| Source | What it contributes | Where in the app |
|---|---|---|
| Prop (`spec/01-canvas-submitted/…ProjectProposal.pdf`) | Product idea, bottle refills, points, leaderboard; water refill map (excluded by TD-3) | rows below; A-41 |
| D1 (`spec/01-canvas-submitted/…Deliverable_1.pdf`) | FR-1..FR-8, NFRs, Fig 4.3 use cases, RTM, SD-1..SD-4, class diagram, architecture | whole app |
| D2 draft §2 | Same FRs/NFRs/use cases/RTM/diagrams as D1. §2.4.1 omits the FR-8 row, but §2.4.3 and §2.4.4 still list FR-8 (A-38) | unchanged |
| D2 draft §4.1 Test Plan | Water Tracking unit: valid / invalid / multiple refill entries; 3 x 500 mL = 1,500 mL; negative or missing values rejected with a message | `S/services/WaterLogService`; `T/WaterLogTest`; `T/Uc1NutritionTest` d2_… |
| D2 draft §4.2, §4.3 + TRC `LogWater.java`, `LogWaterTest.java` | Unit `enter(userId, amount, isLiter)`: liters or gallons, stored as liters truncated to 2 decimals; TC-1..TC-3 | `S/services/WaterLogService`, `S/domain/WaterEntry` (table `water_log`, `M/V2__water_log.sql`); `T/WaterLogTest` |
| TRC `README.md`, `db/docker-compose.yml`, `db/init.sql` | MySQL (8.4) for database operations, Docker, JUnit; WATER_LOG table | `app/docker-compose.yml`, `app/server/pom.xml`, `M/V1__schema.sql`, `M/V2__water_log.sql` (A-33, A-34, A-37) |
| TD-1 | "Add to authenticator app", "Copy setup key", QR only on larger screens, "Remember this device for 30 days" | `C/src/screens/LoginScreen.tsx`, `C/src/deviceTrust.ts`, `C/src/qr.ts`; `S/security/AuthService.login`, `S/security/TrustedDevice`, `M/V3__trusted_devices.sql`; `T/RememberDeviceTest` (A-39) |
| TD-2 | Seeded demo students share all data and have activity history | `S/DemoDataSeeder`; `T/DemoDataTest` (A-40) |
| TD-3 | Water refill map out of scope | not built (A-41) |

## Functional requirements

| Req / UC | Spec source | Code | Test |
|---|---|---|---|
| FR-1 Track server uptime and current status | D1 §4.1 FR-1, §4.4 RTM (UC-4, "Server Component"), SD-4 | `S/services/MonitoringService` (checkServers, getUptimePercent, report, incidents), `S/services/ServerHealthProbes`, `S/controllers/AdminController` (healthCheck, getUptimeReport, alerts, check-now), `S/stubs/MonitoringAgentStub`, `S/domain/UptimeRecord`; client `C/src/screens/AdminDashboard.tsx` | `T/Uc4AdminTest` fr1_uptimePercentageAndServerStatus, externalMonitorMustPresentIntegrationKey, downtimeRaisesAlertAndIncident; `T/services/MonitoringServiceTest`; `app/tools/smoke.mjs` "UC-4 FR-1" |
| FR-2 Track calorie intake | D1 §4.1 FR-2, §4.4 RTM (UC-1, "Calorie intake, user"), SD-1 | `S/controllers/NutritionController` (saveMealEntry, meals, submitMealPhoto), `S/services/DailySummaryService`, `S/domain/MealEntry`, `MealSource`; client `C/src/screens/LogScreen.tsx` (Manual / Photo), `TodayScreen.tsx` | `T/Uc1NutritionTest` fr2_manualMealIsSavedAndStillThereWhenReopened, fr2_mealValidation, photoEntryReturnsAnEstimateThenSavesAfterConfirmation, dailySummaryTotalsEverythingAgainstGoals; smoke "UC-1 FR-2" |
| FR-3 View own profile and others' profiles / friends' statistics | D1 §4.1 FR-3, §4.4 RTM (UC-3, "Profile Component, users"), SD-3; TD-2 (demo data) | `S/controllers/ProfileController` (own profile, updateProfile, changePassword), `S/controllers/SocialController` (search, profile, requests, friends, consent, getFriendStats, leaderboard), `S/services/PrivacyService`, `S/DemoDataSeeder`; client `C/src/screens/ProfileScreen.tsx`, `FriendsScreen.tsx` | `T/Uc3SocialTest` (all 5 tests); `T/DemoDataTest` (2 tests); `T/SecurityTest` changePasswordChecksTheOldPassword; smoke "UC-3" |
| FR-4 Connect smart devices | D1 §4.1 FR-4, §4.4 RTM (UC-2, "Smart device, Server"), SD-2, NFR Interoperability | `S/controllers/ActivityController` (linkDevice, unlinkDevice, pushSteps, records), `S/services/SmartDeviceGateway` + `S/stubs/FakeSmartDeviceGateway`, `S/domain/SmartDevice`, `DeviceLink`, `ActivityRecord`; client `C/src/smartDevice.ts` (FakeSmartDevice stub), `C/src/screens/ActivityScreen.tsx` | `T/Uc2ActivityTest` fr4_linkDeviceThenStepsSynchronise, pairingFailureAndUnlinkedDevicesAreRejected, reachingTheStepGoalEarnsPointsOncePerDay; `C/__tests__/logic.test.ts` FakeSmartDevice; smoke "UC-2 FR-4" |
| FR-5 Shut down portions of the app | D1 §4.1 FR-5, §4.4 RTM (UC-4, "Server, App"), SD-4 | `S/services/FeatureFlagService`, `S/domain/AppModule`, `S/gateway/ApiGatewayInterceptor` (503 MODULE_DISABLED), `S/controllers/AdminController` (disableModule, enableModule), `S/controllers/ModulesController` (isEnabled); client `C/App.tsx` (module guard), `C/src/ui.tsx` MaintenanceNotice, `AdminDashboard.tsx` | `T/Uc4AdminTest` fr5_disabledModuleShowsMaintenanceWhileOthersKeepWorking; `C/__tests__/logic.test.ts` module flags; smoke "UC-4 FR-5" |
| FR-6 Track water intake | D1 §4.1 FR-6, §4.4 RTM (UC-1, "Water intake, user"; low-intake message), SD-1, Prop §4 (bottle refills); D2 §4.1-4.3; TRC `LogWater.java` (liters or gallons, stored as liters) | `S/controllers/NutritionController` (saveWaterEntry: `amount` + `isLiter`, or `volumeMl`; waterEntries; summary), `S/services/WaterLogService` (= LogWater.enter), `S/services/DailySummaryService` (waterLiters, low-water message), `S/domain/WaterEntry` (table `water_log`), `S/security/Student.bottleSizeMl`; client `LogScreen.tsx` WaterCard (L / gal), `TodayScreen.tsx`, `C/src/logic.ts` toLiters, parsePositiveAmount | `T/WaterLogTest` (TC-1..TC-3 + 4); `T/Uc1NutritionTest` fr6_waterIsTrackedAndALowIntakeMessageIsShown, d2_threeRefillsOf500mlTotal1500ml_andGallonsAreConverted, d2_negativeOrMissingWaterIsRejectedWithAMessage; `C/__tests__/logic.test.ts` refill volume, water preview, amount parsing; smoke "UC-1 FR-6" (3 checks) |
| FR-7 Track visits to the rec center | D1 §4.1 FR-7, §4.4 RTM (UC-2, "Rec center swipes, server"), SD-2 | `S/controllers/ActivityController` (readerSwipe, recordVisit, visitHistory), `S/stubs/FakeUrecCardReader`, `S/services/PointsService`, `S/domain/RecVisit`, `UrecCardReader`, `PointAccount`, `PointTransaction`, `S/security/IntegrationKeyVerifier`; client `ActivityScreen.tsx` | `T/Uc2ActivityTest` fr7_cardSwipeRecordsVisitAndAwardsPoints, unknownReaderOrCardIsRejected; smoke "UC-2 FR-7" |
| FR-8 View UTD Dining nutritional info | D1 §4.1 FR-8 (missing from the D2 §2.4.1 table, still in D2 §2.4.3 and §2.4.4; A-38), §4.4 RTM (UC-1, "Food Information"), SD-1 alt [Dining menu item] | `S/services/DiningMenuService` (fetchItems), `S/services/UtdDiningFeed` + `S/stubs/StubUtdDiningFeed`, `S/domain/DiningItem`, `S/controllers/NutritionController` (menuLocations, getMenu, DINING_MENU entries); client `LogScreen.tsx` DiningMenu | `T/Uc1NutritionTest` fr8_diningMenusListItemsWithMacrosPerLocation, diningItemBecomesAMealEntryWithTheItemsMacros; smoke "UC-1 FR-8" |

## Use cases (D1 §4.3 Fig 4.3 diagrams + use case descriptions = D2 §2.4.3) and sequence diagrams (D1 §5.1 = D2 §2.5.1)

| Req / UC | Spec source | Code | Test |
|---|---|---|---|
| UC-1 Track Nutrition & Hydration (FR-2, 6, 8) | Fig 4.3 UC-1; SD-1 | Server: `NutritionController`, `DailySummaryService`, `DiningMenuService`, `WaterLogService`, `AIFoodService`/`FakeAIFoodService`. Client: `TodayScreen`, `LogScreen` | `T/Uc1NutritionTest` (11 tests), `T/WaterLogTest` (7 tests) |
| · Log Meal Calories (manual) | Fig 4.3 UC-1; SD-1 alt [Manual entry] | `NutritionController.saveMealEntry` (MANUAL); `LogScreen` ManualMeal | fr2_manualMeal… |
| · Log Meal by Photo (actor AI Food Recognition Service) | Fig 4.3 UC-1; SD-1 alt [Photo entry] submitMealPhoto → estimateMacros → review → confirm | `NutritionController.submitMealPhoto`, `S/stubs/FakeAIFoodService`; `LogScreen` PhotoMeal | photoEntryReturns…, photoMustBeAnImage |
| · Browse Campus Dining Menu / Add Dining Item to Meal Log / Maintain Dining Menu & Macros (actor UTD Dining) | Fig 4.3 UC-1; SD-1 alt [Dining menu item] getMenu → fetchItems | `NutritionController.getMenu`, `DiningMenuService.fetchItems`, `StubUtdDiningFeed`; `LogScreen` DiningMenu | fr8_…, diningItemBecomes… |
| · Log Water Intake | Fig 4.3 UC-1; SD-1 saveWaterEntry(volume); TRC LogWater.enter(userId, amount, isLiter); D2 §4.1-4.3 | `NutritionController.saveWaterEntry` → `WaterLogService.enter`; `LogScreen` WaterCard | fr6_…, d2_…, `T/WaterLogTest` |
| · View Daily Summary & Goals | Fig 4.3 UC-1; SD-1 "query daily totals vs goals" | `DailySummaryService.getDailySummary` (= Student.getDailySummary), `NutritionController.summary`; `TodayScreen` | dailySummaryTotals… |
| UC-2 Track Activity and Rec Center Visits (FR-4, 7) | Fig 4.3 UC-2; SD-2 | Server: `ActivityController`, `PointsService`, `SmartDeviceGateway`/`FakeSmartDeviceGateway`, `FakeUrecCardReader`. Client: `ActivityScreen`, `smartDevice.ts` | `T/Uc2ActivityTest` (5 tests) |
| · Connect Smart Device + Grant Health Permissions | Fig 4.3 UC-2; SD-2 "request health permissions → linkDevice → pair → save DeviceLink" | `ActivityController.linkDevice` (healthPermissionGranted required), `FakeSmartDeviceGateway.pair`, `DeviceLink.pair()`; `ActivityScreen` permission prompt | fr4_…, pairingFailure… |
| · Sync Steps & Movement Data (actor Smart Device) | Fig 4.3 UC-2; SD-2 loop [periodic sync] pushSteps | `ActivityController.pushSteps`; client auto-sync loop in `ActivityScreen` + `FakeSmartDevice` | fr4_… |
| · Check In at UREC (actor UREC Card Reader) | Fig 4.3 UC-2; SD-2 recordVisit → insert RecVisit | `ActivityController.readerSwipe` / `recordVisit`, `FakeUrecCardReader.sendSwipe` | fr7_… |
| · Earn Points | Fig 4.3 UC-2 («include» from Check In, «extend» from Sync); SD-2 awardPoints → update balance | `PointsService.awardPoints/getBalance`, `PointAccount.addPoints` | fr7_…, reachingTheStepGoal… |
| · View Visit History & Point Balance | Fig 4.3 UC-2; SD-2 "show visit history and points" | `ActivityController.visitHistory`; `ActivityScreen` | fr7_… |
| UC-3 View Friends' Progress (FR-3) | Fig 4.3 UC-3; SD-3; TD-2 | Server: `SocialController`, `PrivacyService`, `ProfileController`, `DemoDataSeeder` (TD-2). Client: `FriendsScreen`, `ProfileScreen` | `T/Uc3SocialTest` (5 tests), `T/DemoDataTest` (2 tests) |
| · Add / Accept Friend (actors User, Friend) | Fig 4.3 UC-3; SD-3 sendRequest → FriendRequest (pending) → notify → acceptRequest → Friendship | `SocialController.sendRequest/pendingRequests/acceptRequest/declineRequest`, `FriendRequest.accept()/decline()` | friendRequestLifecycle, declinedRequest… |
| · Set Data-Sharing Consent | Fig 4.3 UC-3; SD-3 setConsent(fields) → save ConsentSettings; TD-2 (demo accounts start shared) | `SocialController.getConsent/setConsent`, `ConsentSettings`; `FriendsScreen` ConsentCard; `DemoDataSeeder` | friendStatsShowOnlyConsentedFields; `T/DemoDataTest` |
| · View Friend's Statistics | Fig 4.3 UC-3; SD-3 getFriendStats → filterByConsent → consented stats only | `SocialController.getFriendStats`, `PrivacyService.filterByConsent` | friendStatsShowOnlyConsentedFields; demoStudentsAreFriendsAndShareRealNumbers |
| · View Leaderboard | Fig 4.3 UC-3 («extend» View Friend's Statistics); SD-3 "leaderboard rank" | `SocialController.leaderboard` | leaderboardRanksFriendsWhoShare; demoStudentsAreFriendsAndShareRealNumbers |
| · Edit Public Profile / Remove Friend | Fig 4.3 UC-3 | `ProfileController.update` (User.updateProfile), `SocialController.removeFriend` | fr3_…, friendRequestLifecycle |
| UC-4 Monitor and Control System (FR-1, 5) | Fig 4.3 UC-4; SD-4 | Server: `AdminController`, `MonitoringService`, `FeatureFlagService`, `ModulesController`, `MonitoringAgentStub`, `LoggingAlertNotifier`, `OutageSimulator`. Client: `AdminDashboard`, module guard in `App.tsx` | `T/Uc4AdminTest` (5 tests) |
| · Authorize Admin (Permissions + TOTP) | Fig 4.3 UC-4; SD-4 login → authorize(admin, permissions) | `AuthService.login/authorize`, `Developer.hasPermission`, `AdminController.authorize` (`/api/admin/access`) | developerIsAuthorizedByPermissions; `T/SecurityTest` permissionsList… |
| · View Uptime Dashboard / Track Server Uptime (actor Monitoring Service) | Fig 4.3 UC-4; SD-4 loop healthCheck → insert UptimeRecord; getUptimeReport | see FR-1 | fr1_…, externalMonitor… |
| · Raise Downtime Alert | Fig 4.3 UC-4 («extend» Track Server Uptime); SD-4 alt [service down] | `AdminController.healthCheck` → `AlertNotifier.sendDowntimeAlert` (`LoggingAlertNotifier` stub) | downtimeRaisesAlertAndIncident |
| · Disable / Re-enable App Module + Show Maintenance Notice (actor Student) | Fig 4.3 UC-4; SD-4 disableModule → setEnabled(false) → isEnabled → maintenance notice; enableModule | see FR-5 | fr5_… |
| Log In (Password + TOTP) — included by all UCs | Fig 4.3 UC-1..UC-4; SD-1 authenticatedRequest(session); NFR Security | `S/security/AuthService`, `Totp`, `Session`, `AccountService`, `S/controllers/AuthController`, `BaseController.requireSession`; client `LoginScreen.tsx`; `app/tools/totp.mjs` | `T/SecurityTest` (7 tests), `T/security/TotpTest` (RFC 6238 vectors) |
| · Two-factor setup after sign-up: "Add to authenticator app", "Copy setup key", QR only on larger screens | TD-1 (1), (2) | `AuthController.register` (totpSecret, otpauthUri); client `LoginScreen.tsx` AuthenticatorSetup + SetupQrCode, `C/src/qr.ts`, `C/src/logic.ts` isLargeScreen; `expo-clipboard`, `Linking` | `T/RememberDeviceTest` registrationReturnsTheSetupKeyAndAnAuthenticatorLink; `C/__tests__/logic.test.ts` QR on larger screens only, QR matrix |
| · "Remember this device for 30 days" | TD-1 (3); NFR Security | `AuthService.login` (device token, SHA-256 hash, 30 days), `S/security/TrustedDevice` + `TrustedDeviceRepository`, `M/V3__trusted_devices.sql`, `ProfileController.changePassword` (forgets devices); client `LoginScreen.tsx`, `C/src/deviceTrust.ts` | `T/RememberDeviceTest` (6 tests); `C/__tests__/logic.test.ts` remembered device; smoke "TD-1" (2 checks) |

## Non-functional requirements (D1 §4.2 = D2 §2.4.2, unchanged)

| NFR | Code | Test / evidence |
|---|---|---|
| Security (password + TOTP, permissions list, encryption) | `AuthService`, `Totp`, `PasswordHasher` (BCrypt), `Permissions`, `BaseController.requirePermission`, `FieldEncryptor` + `EncryptedStringConverter` (email, TOTP secret), `IntegrationKeyVerifier`; TD-1 remembered devices stored as hashes only | `T/SecurityTest`, `T/security/TotpTest`, `T/RememberDeviceTest` |
| Safety (warn on unsafe goals) | `Student.setGoals/safetyWarnings`, `ProfileController.setGoals`, `DailySummaryService` messages; client `logic.goalWarnings`, `ProfileScreen` GoalsCard | `T/Uc1NutritionTest` unsafeGoalsAreSavedWithWarnings; `C/__tests__/logic.test.ts` |
| Legislative-Privacy (consent before sharing) | `ConsentSettings` (defaults false; only the seeded demo accounts start shared, TD-2), `PrivacyService.filterByConsent`, leaderboard filter | `T/Uc3SocialTest` friendStatsShowOnlyConsentedFields, leaderboardRanksFriendsWhoShare; `T/DemoDataTest` realNewAccountsStillShareNothing |
| Legislative-Safety (general wellness, no medical claims) | Disclaimer on `LoginScreen`, `ProfileScreen`, photo-estimate notice in `NutritionController.submitMealPhoto` | manual (UI) |
| Reliability / Availability (99% uptime, maintenance) | Uptime tracking (FR-1), module isolation (FR-5) | `T/Uc4AdminTest` |
| Interoperability (smart devices via BLE / HealthKit / Health Connect) | `SmartDeviceGateway` (server), `SmartDeviceAdapter` (client) interfaces with stubs | `T/Uc2ActivityTest` |
| Operational / Development (cross-platform mobile app, GitHub) | Expo / React Native client `C/` (A-26) | `npm run typecheck`, `npx expo export --platform web` |
| Usability (basic tasks within 5 minutes) | One-tap water refill, three meal-entry modes, 5-tab layout; TD-1 faster sign-in | manual (demo script in `app/README.md`) |
| Performance / Space (<1 s responses; ≤150 MB app) | Indexed queries per student/day (V1-V3 schema); client web bundle ≈ 512 KB | not load-tested |

## Test documentation (D2 §4, IEEE 829)

| D2 item | Team artefact | App test |
|---|---|---|
| §4.1 Test plan: Water Tracking unit, valid / invalid / multiple refill entries | — | `T/WaterLogTest`; `T/Uc1NutritionTest` d2_threeRefillsOf500mlTotal1500ml_andGallonsAreConverted, d2_negativeOrMissingWaterIsRejectedWithAMessage |
| §4.2 Unit code + JUnit output | TRC `LogWater.java`, `LogWaterTest.java`, `run.sh` (JUnit 4, MySQL) | `S/services/WaterLogService` + `T/WaterLogTest` (JUnit 5, `mvn test`) |
| §4.3 TC-1 userId 1, 0.3 gal → 1.13 L, "Added 1.13L to Jane Doe" | `LogWaterTest.testInputGallons` | `T/WaterLogTest.tc1_inputGallons` |
| §4.3 TC-2 userId 2, -0.3 L → "Negative amount.", nothing added | `LogWaterTest.testNegativeAmount` | `T/WaterLogTest.tc2_negativeAmount` |
| §4.3 TC-3 userId 3 (unknown), 0.5 L → "User doesn't exist.", nothing added | `LogWaterTest.testUnknownUser` | `T/WaterLogTest.tc3_unknownUser` |

## Design artefacts

| Spec artefact | Code |
|---|---|
| D1 §5.2 (= D2 §2.5.2) class diagram — Users and Security: AuthService, Session, User, Student, Developer | `S/security/*` (one class each); plus `TrustedDevice` (TD-1, not in the diagram) |
| D1 §5.2 — Domain Data: UrecCardReader, RecVisit, FriendRequest, Friendship, WaterEntry, ActivityRecord, SmartDevice, DeviceLink, MealEntry, MealSource, DiningItem, ConsentSettings, PointAccount, PointTransaction, AppModule, UptimeRecord | `S/domain/*` (one entity + repository each); tables in `M/V1__schema.sql`; WaterEntry stored in `water_log` in liters (`M/V2__water_log.sql`, TRC) |
| D1 §5.2 — Controllers: BaseController, SocialController, AdminController, ActivityController, NutritionController | `S/controllers/*` (method names match the diagram) |
| D1 §5.2 — Services: PrivacyService, FeatureFlagService, MonitoringService, PointsService, AIFoodService, DiningMenuService | `S/services/*`; plus `WaterLogService` (TRC `LogWater`, the D2 §4.2 unit) |
| D1 §5.1 (= D2 §2.5.1) SD-1..SD-4 lifelines "Mobile App (View)" / "AdminDashboard (View)" | `C/src/screens/*`, `C/App.tsx` |
| D1 §5.3.2 (= D2 §2.5.3) architecture: Client ↔ API Gateway (REST) ↔ Auth Gateway ↔ Social / Activity / Nutrition / Administrator services ↔ Encrypted Database; External: UTD Dining Menus, UREC Card Reader; Smart device → Client via Bluetooth | `C/src/api.ts`; `S/gateway/*`; `S/security/*`; `S/controllers` + `S/services`; MySQL 8.4 via `app/docker-compose.yml` (TRC); `S/stubs/*`; `C/src/smartDevice.ts` |

## Excluded on purpose

| Spec item | Decision | Record |
|---|---|---|
| Prop §3 "a map showing the different and closest areas to refill their water" | Out of scope for this version, not built | TD-3; A-41 |
