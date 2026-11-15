# CometFit — local setup and demo guide

CometFit (SE 3354 Team 9) is a student health-tracking app: meals and water (UC-1), activity and UREC visits (UC-2),
friends' progress (UC-3), and developer monitoring and control (UC-4). It was built only from the team's Proposal,
Deliverable 1, the Deliverable 2 draft, the team's water-logging unit code, team decisions TD-1..TD-5 and the team's Figma prototype screens (all in `../spec/`). `../TRACEABILITY.md` maps every requirement to code and tests. `../ASSUMPTIONS.md` lists every gap
that had to be filled.

```
app/
├── server/            Spring Boot REST server: API gateway, auth gateway, Nutrition / Activity / Social / Admin services
├── client/            Expo (React Native) mobile app — also runs in a web browser for the demo
├── tools/             totp.mjs (prints sign-in codes), smoke.mjs (end-to-end HTTP check), demo-check.mjs (read-only)
└── docker-compose.yml MySQL 8.4 database + the server
```

The architecture follows Deliverable 1 §5.3 (client-server): the mobile client talks to one REST API, and every call
goes through the auth gateway (password + TOTP sessions, permissions list) and the module (feature-flag) check before
it reaches a service. Outside systems (AI food recognition, UTD Dining menus, smart devices, the UREC card reader,
the monitoring service and the alert channel) are **stubs**. They are labelled `*** STUB ***` in the code and listed at
the end of this file.

---

## 1. Install prerequisites

| Tool | Version used | Check with |
|---|---|---|
| Java JDK | 21 or newer (built with 23) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js + npm | Node 22+ | `node -v` |
| Docker Desktop | recommended (MySQL + server) | `docker version` |

Without Docker, use the embedded H2 database (step 4, option B).

## 2. Install dependencies

```bash
# server (downloads the Maven dependencies)
cd app/server
mvn -DskipTests package

# client
cd ../client
npm install
```

## 3. Configure environment variables

Every variable has a development default, so **nothing needs to be set for a local demo**. For anything shared, set
at least `COMETFIT_DATA_KEY`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` and `COMETFIT_INTEGRATION_KEY`.

### Server (`app/server`)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3306/cometfit` (H2 file `./data/cometfit-mysql` with the `h2` profile) | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `cometfit` / `cometfit` | Database login |
| `DB_ROOT_PASSWORD` | `root` | MySQL root password (docker-compose only) |
| `COMETFIT_DATA_KEY` | development key | Base64 32-byte AES key that encrypts emails and TOTP secrets at rest. Generate one: `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`. **Changing it makes existing accounts unreadable.** |
| `COMETFIT_INTEGRATION_KEY` | `dev-integration-key` | Shared secret that the UREC card reader and the external monitor send in the `X-Integration-Key` header |
| `COMETFIT_ZONE` | `America/Chicago` | Time zone that defines "today" for daily totals |
| `COMETFIT_SESSION_HOURS` | `12` | Session lifetime |
| `COMETFIT_REMEMBER_DEVICE_DAYS` | `30` | How long "Remember this device" skips the 6-digit code (TD-1) |
| `COMETFIT_MONITOR_INTERVAL_SEC` | `30` | Health-check interval of the monitoring stub |
| `COMETFIT_MONITOR_STUB` | `true` | Run the built-in monitoring-service stub |
| `COMETFIT_SEED_DEMO` | `true` | Create the demo accounts below on start-up |
| `COMETFIT_CORS_ORIGINS` | `http://localhost:8081,http://localhost:19006` | Browser origins allowed to call the API |

### Client (`app/client`)

| Variable | Default | Purpose |
|---|---|---|
| `EXPO_PUBLIC_API_URL` | `http://localhost:8080` (`http://10.0.2.2:8080` on the Android emulator) | Server base URL |

To set variables: in bash, `export NAME=value`. In PowerShell, `$env:NAME = "value"`. You can also put client
variables in `app/client/.env` (this file is git-ignored).

## 4. Run the project locally

### Start the database and server

**Option A — everything in Docker (recommended)**

```bash
cd app
docker compose up --build        # MySQL 8.4 on localhost:3306 + the server on localhost:8080
```

Add `-d` to run it in the background. The server waits until MySQL is healthy. The first build downloads the Maven
dependencies inside the image, so it takes a few minutes.

**Option A2 — MySQL in Docker, server from Maven (for development)**

```bash
cd app
docker compose up -d db
cd server
mvn spring-boot:run
```

**Option B — no Docker (embedded H2 file database in MySQL mode)**

```bash
cd app/server
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Flyway creates the schema on first start (`V1` schema, `V2` water log in liters, `V3` remembered devices, `V4` sign-in
challenges for TD-5, `V5` UTD Dining building / serving size and nullable macros for TD-6). The server
is ready when <http://localhost:8080/actuator/health> shows `{"status":"UP"}`.

**Start the demo over with clean data** (removes everything, including test leftovers, then re-seeds the demo accounts):

```bash
cd app
docker compose down -v           # -v deletes the MySQL volume
docker compose up --build
```

With option B, stop the server and delete `app/server/data/` instead. *(Upgrading from the stage-1 PostgreSQL build:
the old `app_cometfit-db` volume and old H2 files are not used any more; delete them.)*

### Start the client

```bash
cd app/client
npm run web            # opens http://localhost:8081 in the browser
```

For a phone-like view in the browser, use the dev tools' device toolbar. On a real phone, run `npx expo start` and
open the app in Expo Go. Set `EXPO_PUBLIC_API_URL` to your laptop's LAN address, for example
`http://192.168.1.20:8080`.

### Sign in

| Username | Password | TOTP secret | Role |
|---|---|---|---|
| `alice` | `CometFit123!` | `COMETFITALICE234` | Student |
| `bob` | `CometFit123!` | `COMETFITBOBBY567` | Student |
| `starryskies23` (Sky) | `CometFit123!` | `COMETFITSTARRY23` | Student (TD-8): water leader |
| `nebulanomad` (Nova) | `CometFit123!` | `COMETFITNEBULA45` | Student (TD-8): logs the most calories |
| `lunarlifter` (Luna) | `CometFit123!` | `COMETFITLUNAR567` | Student (TD-8) |
| `orbitrunner` (Theo) | `CometFit123!` | `COMETFITORBIT234` | Student (TD-8): UREC regular, most points |
| `cosmocomet` (Cass) | `CometFit123!` | `COMETFITCOSMO345` | Student (TD-8): not a friend yet; pending friend request to alice |
| `dev` | `CometFit123!` | `COMETFITDEVADMIN` | Developer (admin dashboard) |

Sign-in has two steps (team decision TD-5):

1. **Sign in** — enter the username and password and press *Sign in*. No code is asked here.
2. **Verify it's you** — shown only when this browser/phone is not remembered. Enter the current 6-digit code from
   Duo Mobile (or any authenticator app with the secret above added), or print it with:

   ```bash
   node app/tools/totp.mjs COMETFITALICE234
   ```

   Tick **Remember this device for 30 days** to skip step 2 next time on this browser/phone (TD-1). The code is
   needed again on a new device, after 30 days, after a password change, or after *Account → Two-step verification →
   Forget this device*. The sign-in step expires after 5 minutes; if it does, press *Back to sign in*.

**Register** (from *New student? Create an account*) asks only for the account details. The next screen, **Set up
two-step verification**, shows a QR code to scan with Duo Mobile (on every screen size), plus **Add to authenticator
app** and **Copy setup key**. Enter the first 6-digit code and press *Finish setup* to sign in.

The six demo students `alice`, `bob`, `starryskies23`, `nebulanomad`, `lunarlifter` and `orbitrunner` are all
friends with each other, share all their stats, and have a week of meals, water, steps, UREC visits and points,
today included (TD-2, TD-8); their meals are real UTD Dining items (TD-6). On alice's Social screen every board lists
six people and alice is neither first nor last on any of them. `cosmocomet` has sent alice a friend request that is
still pending (TD-8). "Today" is the day the demo data was reset; on a later day, reset again. Accounts you register
yourself share nothing until you turn sharing on.

## 5. Run the tests

```bash
cd app/server && mvn test        # 76 API/unit tests on H2 (MySQL mode): Uc1..Uc4, WaterLogTest (team TC-1..TC-3),
                                 # RememberDeviceTest (TD-1), DemoDataTest (TD-2, TD-6), TwoStepSignInTest (TD-5),
                                 # Td6UtdDiningTest + StubUtdDiningFeedTest (TD-6), Td8DemoFriendsTest (TD-8), security
cd app/client && npm test        # 32 client unit tests (Node test runner), incl. authFlow (TD-5), td6Dining (TD-6)
cd app/client && npm run typecheck
cd app/client && npx expo export --platform web   # production web build in app/client/dist (= npm run export:web)

# End-to-end over real HTTP against a running server (uses the demo accounts; it logs meals/water for alice,
# so reset the demo data afterwards if you want it clean):
node app/tools/smoke.mjs http://localhost:8080
# Read-only: demo accounts sign in, TD-6 dining data, demo meals, TD-8 six-person boards and pending request
# (writes no demo data):
node app/tools/demo-check.mjs http://localhost:8080
```

## 6. Demo script (about 5 minutes)

Start from clean demo data (`docker compose down -v`, then `docker compose up --build`).

The five tabs follow the team's Figma prototype (TD-4): **Dashboard, Food & Water, Activities, Social, Account**.

0. **Sign-in (TD-5, TD-1)** — Sign in as `alice` with username and password; *Verify it's you* asks for the code.
   Tick *Remember this device for 30 days* and *Verify*. Log out (Account) and sign in again: no second step. To
   show sign-up, register a new student: *Set up two-step verification* shows the QR code for Duo Mobile, *Add to
   authenticator app* and *Copy setup key*; enter the first code to finish.
1. **UC-1** — **Dashboard** shows the UREC streak and a *Today* card (calories, water, steps, low-water message). On
   **Food & Water**, tap *+ 500 ml* (one bottle refill). Tap *Other amount (L / gal)*, enter `0.3`, choose *gal* and
   tap *Add 1.13 L*: the app answers "Added 1.13L to Alice" (water is stored in liters). A negative amount is not
   accepted. Tap *+ Log food*: log a meal manually, then *Dining menu*: the 7 UTD Dining locations with their
   buildings (TD-6). Open *Dining Hall West (Andromeda Hall)* — calories only, macros "not published" — and *Add*
   *Italian Nachos*; the macro totals note that it counts as 0 g. Open *Chick-fil-A* to show an item with published
   macros. Then *Photo (AI)* → pick any image, edit the estimate, *Confirm & save*. *Today's log* lists every entry;
   *Week* / *Month* show averages per day. alice's and bob's earlier days are real UTD Dining items (TD-6 §5).
2. **Safety** — in **Account → Daily goals & units**, enter 800 kcal or 6000 ml and watch the warnings appear.
3. **UC-2** — **Activities** → *+ Connect smart device* → *Allow* (simulated health prompt) → *+ Sync activity from
   device*. Then *Check in at UREC* (here or on the Dashboard): +10 points, the visit appears in the history and the
   Dashboard streak updates.
4. **UC-3** — **Social**: the friends leaderboards (water, calories, UREC visits, points) list six people: alice and
   her five demo friends (TD-2, TD-8), with a different leader on each board (starryskies23 water, nebulanomad
   calories, orbitrunner UREC visits and points). Water and points move as alice logs things in steps 1-3. Open a
   friend's *Stats*. In **Account → Privacy & data** turn one of alice's sharing switches off, then in a second
   (private) browser window sign in as `bob` and see alice drop off that board and the stat show "Not shared".
   *Friend requests* already shows the pending request from `cosmocomet` (TD-8): *Accept* it and Cass joins the
   boards (seven people).
5. **UC-4** — Sign in as `dev`. The developer dashboard shows uptime % and per-component status. Click *Simulate
   outage* on a component to see a downtime alert and an incident, then end it. *Disable* "Friends & Leaderboard" and
   switch to bob's window: the Social tab shows the maintenance notice while the other tabs keep working.
   *Re-enable* it.

Not built on purpose: the Proposal's water refill map (team decision TD-3, `../ASSUMPTIONS.md` A-41).

## 7. Stubs (external systems from the spec)

| External actor in the spec | Interface | Stub implementation |
|---|---|---|
| AI Food Recognition Service (UC-1) | `services/AIFoodService` | `stubs/FakeAIFoodService` — deterministic estimate from the image hash |
| UTD Dining menu data (UC-1, FR-8) | `services/UtdDiningFeed` | `stubs/StubUtdDiningFeed` — TD-6: the 7 UTD Dining locations / 54 items of `spec/06-utd-dining` (Monday 2026-11-16 menus, classpath copy `utd-dining/…json`), same menu every day, no live calls |
| Smart Device pairing (UC-2) | `services/SmartDeviceGateway` | `stubs/FakeSmartDeviceGateway` — always pairs, except device ids starting with `FAIL` |
| Smart Device readings + health permission (UC-2) | client `src/smartDevice.ts` `SmartDeviceAdapter` | `FakeSmartDevice` — simulated steps / heart rate |
| UREC Card Reader (UC-2) | `POST /api/activity/visits` with the integration key | `stubs/FakeUrecCardReader` — the "Check in at UREC" buttons (Dashboard, Activities) |
| Monitoring Service (UC-4) | `POST /api/admin/health-check` with the integration key | `stubs/MonitoringAgentStub` — timer inside the server |
| Downtime alert channel (UC-4) | `services/AlertNotifier` | `stubs/LoggingAlertNotifier` — logs the alert and shows it on the dashboard |
| A real outage (demo only) | — | `stubs/OutageSimulator` — the "Simulate outage" button |

## 8. REST API overview

All endpoints except register, login and health need `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/verify`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Profile (FR-3) | `GET/PUT /api/profile`, `PUT /api/profile/password`, `PUT /api/profile/goals` |
| Nutrition (UC-1) | `POST /api/nutrition/meals/photo`, `GET /api/nutrition/menu/locations`, `GET /api/nutrition/menu/location-info` (TD-6: building, menu, source), `GET /api/nutrition/menu?location=`, `POST/GET /api/nutrition/meals`, `POST/GET /api/nutrition/water`, `GET /api/nutrition/summary` |
| Activity (UC-2) | `POST/GET /api/activity/devices`, `DELETE /api/activity/devices/{id}`, `POST /api/activity/steps`, `GET /api/activity/records`, `POST /api/activity/visits` (reader), `GET /api/activity/visits`, `POST /api/activity/stub-reader/swipe` |
| Social (UC-3) | `GET /api/social/users/search?q=`, `GET /api/social/users/{id}`, `POST/GET /api/social/requests`, `POST /api/social/requests/{id}/accept\|decline`, `GET /api/social/friends`, `DELETE /api/social/friends/{id}`, `GET/PUT /api/social/consent`, `GET /api/social/friends/{id}/stats`, `GET /api/social/leaderboard` |
| Modules (FR-5) | `GET /api/modules`, `GET /api/modules/{id}` |
| Admin (UC-4) | `GET /api/admin/access`, `GET /api/admin/uptime?hours=`, `POST /api/admin/uptime/check-now`, `GET /api/admin/alerts`, `GET /api/admin/modules`, `POST /api/admin/modules/{id}/disable\|enable`, `POST /api/admin/health-check` (monitor), `POST /api/admin/stub/outage` |

Sign-in (stage 3, TD-5): `POST /api/auth/login` takes `username`, `password` and optional `deviceToken` (a remembered
device). It returns a session (`token`, `user`, `rememberedDevice: true`) only for a remembered device; otherwise
`verificationRequired: true` with a single-use `challengeId` (5 minutes) and **no** token. `POST /api/auth/verify`
takes `challengeId`, `totpCode` and optional `rememberDevice` and returns the session, plus `deviceToken` /
`trustedUntil` when the device was just remembered. `POST /api/auth/register` returns `totpSecret`, `otpauthUri` and a
`setupChallengeId` (15 minutes) that the first code completes through `/verify`. Every credential failure is the same
`401 "Invalid username, password or code"`. `POST /api/nutrition/water` takes `{"amount": 0.3, "isLiter": false}` (liters or gallons, team
`LogWater`) or `{"volumeMl": 500}`, and returns `message` (for example "Added 1.13L to Alice").

When a module is disabled, its endpoints answer `503 {"code":"MODULE_DISABLED"}` and the app shows a maintenance
notice.

## Troubleshooting

- **"Cannot reach the CometFit server"** in the app: the server isn't running, or `EXPO_PUBLIC_API_URL` is wrong.
- **"Invalid username, password or code" on Verify it's you:** the 6-digit code is wrong or expired, the laptop clock
  is off, or the sign-in step timed out (5 minutes) / had 5 wrong codes — press *Back to sign in* and start again.
- **Port 8080 already in use:** set `PORT=8090` before starting the server, and set `EXPO_PUBLIC_API_URL=http://localhost:8090` for the client.
- **CORS error in the browser:** add the page's origin to `COMETFIT_CORS_ORIGINS`.
- **"Could not decrypt field":** `COMETFIT_DATA_KEY` changed after the accounts were created. Restore the old key or reset the database.
