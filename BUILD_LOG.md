# Build log

1. Read `PROMPT.md`, `CLAUDE.md`, listed `spec/01-canvas-submitted/` (2 PDFs).
2. Extracted text of `Team_9_SE3354_F26_ProjectProposal.pdf` and `Team_09_SE3354_F26_Deliverable_1.pdf` with `pdftotext -layout` (printed to stdout, nothing written into `spec/`).
3. Tried to render diagram pages: `pdftoppm` not installed and Read-tool PDF rendering also needs poppler -> failed. Fix: wrote scratch tool `app/.spec-render/extract-images.js` (Node, zlib only) that pulls embedded image XObjects out of the PDF and re-encodes them as PNG. 10 images extracted (git-ignored).
4. Viewed all 10 images: Fig 4.3 use case diagrams UC-1..UC-4, SD-1..SD-4 sequence diagrams (§5.1), class diagram (§5.2), client-server architecture diagram (§5.3.2).
5. Checked environment: Java 23.0.1, Maven 3.9.11, Node 22.23, npm 10.9. Docker CLI present but Docker Engine not running -> PostgreSQL via docker-compose is provided for the README, and an embedded H2 profile is used to build/test/run here (see A-2).
6. Backend stack chosen (general programming, not product): Spring Boot 3.4 (Java 21 target on JDK 23), JPA + Flyway, PostgreSQL / H2. `mvn dependency:resolve` OK.
7. Built foundation: V1 schema (one table per §5.2 class), User/Student/Developer/Session, AuthService (password BCrypt + TOTP RFC 6238), AES-GCM field encryption, BaseController, API gateway interceptor (feature-flag routing), Auth/Profile controllers, demo seeder. Commit "Foundation".
8. Smoke boot via `mvn spring-boot:run` in background: could not inspect (background output dir and `curl` denied by permissions). Fix: wrote `ContextLoadsTest` (H2) instead -> passes, proving Flyway schema == JPA mappings.
9. UC-1 backend: NutritionController, DailySummaryService, DiningMenuService, AIFoodService + FakeAIFoodService stub, UtdDiningFeed + StubUtdDiningFeed. Commit.
10. UC-2 backend: ActivityController, PointsService, SmartDeviceGateway + FakeSmartDeviceGateway stub, FakeUrecCardReader stub, IntegrationKeyVerifier. Commit.
11. UC-3 backend: SocialController, PrivacyService (filterByConsent), leaderboard. Commit.
12. UC-4 backend: AdminController, MonitoringService, HealthProbes, FeatureFlag endpoints for the app, MonitoringAgentStub, LoggingAlertNotifier stub, OutageSimulator stub, docker-compose + Dockerfile. ContextLoadsTest still green. Commit.
13. Client scaffold: `npx create-expo-app app/client --template blank-typescript` (Expo SDK 58, RN 0.88), then `npx expo install react-dom react-native-web @expo/metro-runtime expo-image-picker`. Read the image-picker API from its installed `.d.ts` files (no web docs).
14. Problem: the template added `app/client/AGENTS.md` (tells agents to fetch Expo docs from the web, against CLAUDE.md rule 8) and `app/client/.claude/settings.json` (enables an Expo plugin). `rm` and writes into `.claude/` need approval I could not get. Fix: replaced AGENTS.md with a short note, did not follow its instructions, and git-ignored both files. The template's untracked `LICENSE` was left uncommitted.
15. Wrote client: `api.ts`, `types.ts`, `logic.ts`, `ui.tsx`, `smartDevice.ts` (FakeSmartDevice stub), screens Login, Today, Log, Activity, Friends, Profile, AdminDashboard; `App.tsx` (student tabs + module guard, developer dashboard). `tsc --noEmit` clean; `npx expo export --platform web` bundles (468 KB). Committed per use case (UC-1..UC-4 client commits).
16. Server tests: ApiTestSupport + SecurityTest, TotpTest (RFC 6238 vectors), Uc1..Uc4 tests, MonitoringServiceTest. First run: 36/38. 2 failures came from the test helper double-encoding `%20` in MockMvc URLs; fixed the test, now 38/38 pass.
17. Client tests (`node --test --experimental-strip-types`): first failed because `node --test <dir>` is not supported on Node 22 -> used a glob; then failed on a TS constructor parameter property (unsupported by type stripping) -> rewrote it as an explicit field. 7/7 pass. Excluded `__tests__` from the app `tsc` run.
18. Wrote `app/tools/totp.mjs` and `app/tools/smoke.mjs` (end-to-end HTTP walk of UC-1..UC-4 with the demo accounts). First run hit a stale server on :8099 (TaskStop on the earlier `mvn spring-boot:run` left its forked JVM alive, still serving foundation-era code) -> ran the fresh build on :8098: 23/23 smoke checks pass; CORS preflight from http://localhost:8081 OK. Two orphaned smoke-test JVMs (ports 8098, 8099, in-memory DBs) could not be killed without approval; reported to the user.
19. Not verified here: the PostgreSQL profile (Docker Engine not running; same Flyway SQL, validated on H2 in PostgreSQL mode), and visual rendering of the client in a real browser (no browser automation available). Only the web bundle build and typecheck were verified for the client.
20. Wrote `app/README.md`, `ASSUMPTIONS.md` (A-1..A-32), `TRACEABILITY.md` (FR-1..FR-8, UC-1..UC-4 + sub-use-cases, NFRs, class/sequence/architecture artefacts -> code and tests). Commit.

## Stage 2 (2026-11-14): update to Deliverable 2 draft, team repo code, team decisions

Instruction: `proof/prompts/stage-2.md` (same text as the stage-2 prompt). Precedence when documents disagree
(later wins): Proposal, Deliverable 1, Deliverable 2 draft, team repo code (for the unit it covers), team decisions.

21. Re-read `PROMPT.md`, `CLAUDE.md` and every file in `spec/`: Proposal + D1 (`pdftotext -layout`, stdout only),
    `spec/02-deliverable-2-draft/` (README + PDF), `spec/03-team-decisions/` (README, TD-1..TD-3),
    `spec/04-team-repo-code/` (README, README-SOURCE, LogWater.java, LogWaterTest.java, run.sh, db/docker-compose.yml, db/init.sql).
22. Extracted the D2 PDF images with `app/.spec-render/extract-images.js` into `app/.spec-render/img-d2/` (git-ignored).
    Pixel-compared them with the D1 images: the 7 carried-forward diagrams (sequence diagrams SD-1..SD-4, class diagram
    §2.5.2, architecture §2.5.3, Fig 4.3) are **byte-identical** to D1. 3 new images = D2 §4.2 screenshots of
    `LogWater.java`, `LogWaterTest.java` and the JUnit output "OK (3 tests)"; they match the files in `spec/04-team-repo-code/`.
    D1's three use-case-diagram images are not in the D2 draft (§2.4.3 keeps only the table). Treated as a draft omission, not a change.
23. Baseline before any change: `mvn test` all green (38 tests).

### Differences between the stage-1 app and the spec (step 2 of the stage-2 prompt)

| # | Area | Stage-1 app | Spec now says | Source | Action |
|---|---|---|---|---|---|
| D-1 | Technology: database | PostgreSQL 16 (A-2 filled a gap) | "MySQL for database operations", Docker, `mysql:8.4` image | team repo `README.md`, `db/docker-compose.yml` | Switch to MySQL 8.4; H2 test profile in MySQL mode |
| D-2 | Data model: water | table `water_entries(student_id, volume_ml INT)` in ml | table `WATER_LOG(id, userId, amount FLOAT, loggedAt DEFAULT now)`; "only liters will be entered into db" | team repo `LogWater.java`, `db/init.sql` | New table `water_log` storing liters (FLOAT), migrate data |
| D-3 | Feature: water units | ml only | "Application supports both liters and gallons"; gallons x 3.785412 -> liters | `LogWater.java` | Accept `amount` + `isLiter`; keep `volumeMl` (D1/D2 class diagram `saveWaterEntry(volumeMl)`) as an alias |
| D-4 | Validation: water amount | 1..5000 ml, generic bean-validation message | `amount <= 0` -> "Negative amount."; unknown user -> "User doesn't exist."; amount truncated (floor) to 2 decimal liters | `LogWater.java`, D2 §4.3 TC-1..TC-3 | Same messages and rounding in `WaterLogService` |
| D-5 | Validation: missing water amount | 400 with a generic message | "negative or missing values should be rejected with an appropriate validation message" | D2 §4.1 | Explicit "Amount is required." |
| D-6 | Response: water save | entry + summary | `"Added 1.13L to Jane Doe"` | `LogWater.java`, D2 §4.3 TC-1 | Add `message` to the save response; client shows it |
| D-7 | Test: water unit | FR-6 API test only | TC-1 (0.3 gal -> 1.13 L), TC-2 (negative), TC-3 (unknown user); 3 x 500 mL refills = 1,500 mL | D2 §4.1, §4.3; `LogWaterTest.java` | Port as JUnit tests in the app's suite |
| D-8 | Screen: sign-up | shows raw TOTP secret + otpauth URI text | "Add to authenticator app" button, "Copy setup key" button, QR only on larger screens | TD-1 (1), (2) | Implement in LoginScreen |
| D-9 | Feature: sign-in | code required every sign-in (A-6) | "Remember this device for 30 days"; remembered device signs in with username + password only | TD-1 (3) | Trusted-device token (server table + client storage) |
| D-10 | Demo data | alice/bob share nothing, no history, not friends (A-18) | demo students share everything and have activity history; UC-3 must show real numbers and a leaderboard with more than one person | TD-2 | Seeder: consent on, friendship, 7 days of history |
| D-11 | Feature: water refill map | not built | out of scope, record as excluded Proposal feature | TD-3 | ASSUMPTIONS row only; no code |
| D-12 | Requirements text | FR-1..FR-8 | D2 §2.4.1 table lists FR-1..FR-7 only, but D2 §2.4.3 (UC-1 "2,6,8") and §2.4.4 RTM still contain FR-8 | D2 §2.4 | Treat as a copy omission; FR-8 stays (A-38) |
| D-13 | Run | `docker compose up` starts only the DB; server needs `--profile full` | (prompt) stack must start with `docker compose up` from `app/` | stage-2 prompt step 6 | Server starts by default |
| D-14 | NFR / UC / SD / class / architecture | as built | unchanged (identical text and diagrams) | D2 §2.4.2-2.5.3 | none |
| D-15 | Test tool | JUnit 5 (Spring Boot) | "JUnit for unit testing" (team uses JUnit 4.13.2) | team repo README | Keep JUnit 5 (same tool family; A-37) |

### Stage 2 steps

24. D-1 MySQL: replaced the PostgreSQL driver/Flyway module with `mysql-connector-j` + `flyway-mysql`; rewrote V1 in MySQL syntax (MySQL ignores inline `REFERENCES`, so foreign keys became table constraints; `DATETIME(6)`); H2 test/no-Docker profile switched to MySQL mode; docker-compose now `mysql:8.4` and the server starts with plain `docker compose up` (D-13). `mvn test` 38/38 on H2.
25. Stopped the stage-1 containers and deleted the old `app_cometfit-db` PostgreSQL volume (stage-1 smoke-test leftovers). First `docker compose up --build` failed inside Docker BuildKit ("parent snapshot ... does not exist", an engine cache error, not the build); the retry built fine. Flyway applied V1 on MySQL 8.4, Hibernate schema validation passed, `node app/tools/smoke.mjs`: 23/23 pass on MySQL.
26. D-2..D-7 water unit: V2 migration creates `water_log` (liters, FLOAT(24)), copies old entries (ml -> liters, truncated) and drops `water_entries`. `WaterLogService.enter(userId, amount, isLiter)` mirrors team `LogWater.enter` (same messages, gallon factor, truncation). `POST /api/nutrition/water` takes `amount` + `isLiter` (and still `volumeMl`) and returns `message`; the summary adds `waterLiters`. Found while writing it: `Math.floor(0.29 * 100)` = 28 in IEEE doubles (the team code has the same issue); fixed with a 1e-9 guard (A-35). Tests: new `WaterLogTest` (TC-1..TC-3 port + 4 more) and 2 API tests in `Uc1NutritionTest` (D2 §4.1 three 500 mL refills = 1,500 mL; negative/missing messages). Every pre-existing test passes unchanged: 47/47. Client: L/gal input, server message shown, liters on Today/Log; 2 new client tests (9/9), `tsc` clean. Smoke test +2 checks. On MySQL in Docker, V2 applied and the earlier 500 ml entry became 0.5 L; 25/25 smoke checks pass.
