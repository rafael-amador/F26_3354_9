# CometFit — local setup and demo guide

CometFit (SE 3354 Team 9) is a student health-tracking app: meals and water (UC-1), activity and UREC visits (UC-2),
friends' progress (UC-3), and developer monitoring and control (UC-4). It was built only from the team's Proposal,
Deliverable 1, the Deliverable 2 draft, the team's water-logging unit code, and team decisions TD-1..TD-3 (all in `../spec/`). `../TRACEABILITY.md` maps every requirement to code and tests. `../ASSUMPTIONS.md` lists every gap
that had to be filled.

```
app/
├── server/            Spring Boot REST server: API gateway, auth gateway, Nutrition / Activity / Social / Admin services
├── client/            Expo (React Native) mobile app — also runs in a web browser for the demo
├── tools/             totp.mjs (prints sign-in codes), smoke.mjs (end-to-end HTTP check)
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

Flyway creates the schema on first start (`V1` schema, `V2` water log in liters, `V3` remembered devices). The server
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
| `dev` | `CometFit123!` | `COMETFITDEVADMIN` | Developer (admin dashboard) |

The first sign-in on a device needs the current 6-digit code. Get it from any authenticator app (add the secret),
or from:

```bash
node app/tools/totp.mjs COMETFITALICE234
```

Tick **Remember this device for 30 days** and later sign-ins on that browser/phone only need username and password
(TD-1). The code is needed again on a new device, after 30 days, or after a password change.

New students can register from the sign-in screen. The app then shows **Add to authenticator app** (opens the
authenticator with the account filled in), **Copy setup key**, and a QR code when the window is wide (a laptop),
so a phone can scan it.

The demo students `alice` and `bob` are already friends, share all their stats, and have a week of meals, water,
steps, UREC visits and points (TD-2). Accounts you register yourself share nothing until you turn sharing on.

## 5. Run the tests

```bash
cd app/server && mvn test        # 55 API/unit tests on H2 (MySQL mode): Uc1..Uc4, WaterLogTest (team TC-1..TC-3),
                                 # RememberDeviceTest (TD-1), DemoDataTest (TD-2), security
cd app/client && npm test        # 12 client unit tests (Node test runner)
cd app/client && npm run typecheck

# End-to-end over real HTTP against a running server (uses the demo accounts):
node app/tools/smoke.mjs http://localhost:8080
```

## 6. Demo script (about 5 minutes)

Start from clean demo data (`docker compose down -v`, then `docker compose up --build`).

0. **Sign-in (TD-1)** — Sign in as `alice` with the code and tick *Remember this device for 30 days*. Sign out and
   sign in again: only username and password are needed. To show sign-up, register a new student and point out
   *Add to authenticator app*, *Copy setup key* and the QR code (wide window only).
1. **UC-1** — On **Log**, tap *+1 refill*. Then enter `0.3`, choose *gal* and tap *Add 1.13 L*: the app answers
   "Added 1.13L to Alice" (water is stored in liters). Entering a negative amount is not accepted. Log a meal manually. Switch to *Dining menu* →
   *Dining Hall* → *Add* an item. Switch to *Photo (AI)* and pick any image, edit the estimate, then *Confirm & save*.
   **Today** shows calories, macros, water progress and the low-water message.
2. **Safety** — in **Profile → Daily goals**, enter 800 kcal or 6000 ml and watch the warnings appear.
3. **UC-2** — **Activity** → *Connect smart device* → *Allow* (simulated health prompt) → *Sync now*. Then
   *Simulate UREC card swipe*, which earns +10 points and appears in the visit history.
4. **UC-3** — **Friends**: `bob` is already a friend (TD-2). Open bob's *Stats* (calories, water, steps, visits,
   points) and the leaderboard with both students. Turn one of alice's sharing switches off under *What my friends can
   see*, then in a second (private) browser window sign in as `bob` and see that stat change to "Not shared". To show
   friend requests, register a new student and send `alice` a request.
5. **UC-4** — Sign in as `dev`. The dashboard shows uptime % and per-service status. Click *Simulate outage* on a
   service to see a downtime alert and an incident, then end it. *Disable* "Friends & Leaderboard" and switch to
   bob's window: the Friends tab shows the maintenance notice while the other tabs keep working. *Re-enable* it.

Not built on purpose: the Proposal's water refill map (team decision TD-3, `../ASSUMPTIONS.md` A-41).

## 7. Stubs (external systems from the spec)

| External actor in the spec | Interface | Stub implementation |
|---|---|---|
| AI Food Recognition Service (UC-1) | `services/AIFoodService` | `stubs/FakeAIFoodService` — deterministic estimate from the image hash |
| UTD Dining menu data (UC-1, FR-8) | `services/UtdDiningFeed` | `stubs/StubUtdDiningFeed` — fixed sample menu for "Dining Hall" and "Campus Store" |
| Smart Device pairing (UC-2) | `services/SmartDeviceGateway` | `stubs/FakeSmartDeviceGateway` — always pairs, except device ids starting with `FAIL` |
| Smart Device readings + health permission (UC-2) | client `src/smartDevice.ts` `SmartDeviceAdapter` | `FakeSmartDevice` — simulated steps / heart rate |
| UREC Card Reader (UC-2) | `POST /api/activity/visits` with the integration key | `stubs/FakeUrecCardReader` — the "Simulate UREC card swipe" button |
| Monitoring Service (UC-4) | `POST /api/admin/health-check` with the integration key | `stubs/MonitoringAgentStub` — timer inside the server |
| Downtime alert channel (UC-4) | `services/AlertNotifier` | `stubs/LoggingAlertNotifier` — logs the alert and shows it on the dashboard |
| A real outage (demo only) | — | `stubs/OutageSimulator` — the "Simulate outage" button |

## 8. REST API overview

All endpoints except register, login and health need `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Profile (FR-3) | `GET/PUT /api/profile`, `PUT /api/profile/password`, `PUT /api/profile/goals` |
| Nutrition (UC-1) | `POST /api/nutrition/meals/photo`, `GET /api/nutrition/menu/locations`, `GET /api/nutrition/menu?location=`, `POST/GET /api/nutrition/meals`, `POST/GET /api/nutrition/water`, `GET /api/nutrition/summary` |
| Activity (UC-2) | `POST/GET /api/activity/devices`, `DELETE /api/activity/devices/{id}`, `POST /api/activity/steps`, `GET /api/activity/records`, `POST /api/activity/visits` (reader), `GET /api/activity/visits`, `POST /api/activity/stub-reader/swipe` |
| Social (UC-3) | `GET /api/social/users/search?q=`, `GET /api/social/users/{id}`, `POST/GET /api/social/requests`, `POST /api/social/requests/{id}/accept\|decline`, `GET /api/social/friends`, `DELETE /api/social/friends/{id}`, `GET/PUT /api/social/consent`, `GET /api/social/friends/{id}/stats`, `GET /api/social/leaderboard` |
| Modules (FR-5) | `GET /api/modules`, `GET /api/modules/{id}` |
| Admin (UC-4) | `GET /api/admin/access`, `GET /api/admin/uptime?hours=`, `POST /api/admin/uptime/check-now`, `GET /api/admin/alerts`, `GET /api/admin/modules`, `POST /api/admin/modules/{id}/disable\|enable`, `POST /api/admin/health-check` (monitor), `POST /api/admin/stub/outage` |

Request bodies changed in stage 2: `POST /api/auth/login` takes `username`, `password`, and `totpCode` and/or
`deviceToken`, plus optional `rememberDevice` (TD-1). The response includes `deviceToken` / `trustedUntil` when a device
was just remembered. `POST /api/nutrition/water` takes `{"amount": 0.3, "isLiter": false}` (liters or gallons, team
`LogWater`) or `{"volumeMl": 500}`, and returns `message` (for example "Added 1.13L to Alice").

When a module is disabled, its endpoints answer `503 {"code":"MODULE_DISABLED"}` and the app shows a maintenance
notice.

## Troubleshooting

- **"Cannot reach the CometFit server"** in the app: the server isn't running, or `EXPO_PUBLIC_API_URL` is wrong.
- **Login fails with a correct password:** the 6-digit code is wrong or expired, or the laptop clock is off.
- **Port 8080 already in use:** set `PORT=8090` before starting the server, and set `EXPO_PUBLIC_API_URL=http://localhost:8090` for the client.
- **CORS error in the browser:** add the page's origin to `COMETFIT_CORS_ORIGINS`.
- **"Could not decrypt field":** `COMETFIT_DATA_KEY` changed after the accounts were created. Restore the old key or reset the database.
