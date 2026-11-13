# CometFit — local setup and demo guide

CometFit (SE 3354 Team 9) is a student health-tracking app: meals and water (UC-1), activity and UREC visits (UC-2),
friends' progress (UC-3), and developer monitoring and control (UC-4). It was built only from the team's Proposal and
Deliverable 1. `../TRACEABILITY.md` maps every requirement to code and tests. `../ASSUMPTIONS.md` lists every gap
that had to be filled.

```
app/
├── server/            Spring Boot REST server: API gateway, auth gateway, Nutrition / Activity / Social / Admin services
├── client/            Expo (React Native) mobile app — also runs in a web browser for the demo
├── tools/             totp.mjs (prints sign-in codes), smoke.mjs (end-to-end HTTP check)
└── docker-compose.yml PostgreSQL database (and optionally the server)
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
| Docker Desktop | optional, for PostgreSQL | `docker version` |

Without Docker, use the embedded H2 database (step 3, option B).

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
at least `COMETFIT_DATA_KEY`, `DB_PASSWORD` and `COMETFIT_INTEGRATION_KEY`.

### Server (`app/server`)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cometfit` (H2 file `./data/cometfit` with the `h2` profile) | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `cometfit` / `cometfit` | Database login |
| `COMETFIT_DATA_KEY` | development key | Base64 32-byte AES key that encrypts emails and TOTP secrets at rest. Generate one: `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`. **Changing it makes existing accounts unreadable.** |
| `COMETFIT_INTEGRATION_KEY` | `dev-integration-key` | Shared secret that the UREC card reader and the external monitor send in the `X-Integration-Key` header |
| `COMETFIT_ZONE` | `America/Chicago` | Time zone that defines "today" for daily totals |
| `COMETFIT_SESSION_HOURS` | `12` | Session lifetime |
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

**Option A — PostgreSQL in Docker (default profile)**

```bash
cd app
docker compose up -d db          # PostgreSQL 16 on localhost:5432
cd server
mvn spring-boot:run
```

**Option B — no Docker (embedded H2 file database)**

```bash
cd app/server
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Either way, Flyway creates the schema on first start. The server is ready when
<http://localhost:8080/actuator/health> shows `{"status":"UP"}`. The H2 data is stored in `app/server/data/`;
delete that folder to start over.

*(Optional: run everything in containers with `docker compose --profile full up -d --build`.)*

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

Every sign-in needs the current 6-digit code. Get it from any authenticator app (add the secret), or from:

```bash
node app/tools/totp.mjs COMETFITALICE234
```

New students can register from the sign-in screen. The app then shows their own TOTP secret once.

## 5. Run the tests

```bash
cd app/server && mvn test        # 38 API/unit tests on H2, one test class per use case (Uc1..Uc4) + security
cd app/client && npm test        # client unit tests (Node test runner)
cd app/client && npm run typecheck

# End-to-end over real HTTP against a running server (uses the demo accounts):
node app/tools/smoke.mjs http://localhost:8080
```

## 6. Demo script (about 5 minutes)

1. **UC-1** — Sign in as `alice`. On **Log**, tap *+1 refill*, then log a meal manually. Switch to *Dining menu* →
   *Dining Hall* → *Add* an item. Switch to *Photo (AI)* and pick any image, edit the estimate, then *Confirm & save*.
   **Today** shows calories, macros, water progress and the low-water message.
2. **Safety** — in **Profile → Daily goals**, enter 800 kcal or 6000 ml and watch the warnings appear.
3. **UC-2** — **Activity** → *Connect smart device* → *Allow* (simulated health prompt) → *Sync now*. Then
   *Simulate UREC card swipe*, which earns +10 points and appears in the visit history.
4. **UC-3** — **Friends** → search `bob` → *Profile* → *Add friend*. In a second browser window (or a private
   window), sign in as `bob` and *Accept*. As `alice`, turn sharing on under *What my friends can see*. As `bob`,
   open alice's *Stats* and the leaderboard.
5. **UC-4** — Sign in as `dev`. The dashboard shows uptime % and per-service status. Click *Simulate outage* on a
   service to see a downtime alert and an incident, then end it. *Disable* "Friends & Leaderboard" and switch to
   bob's window: the Friends tab shows the maintenance notice while the other tabs keep working. *Re-enable* it.

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

When a module is disabled, its endpoints answer `503 {"code":"MODULE_DISABLED"}` and the app shows a maintenance
notice.

## Troubleshooting

- **"Cannot reach the CometFit server"** in the app: the server isn't running, or `EXPO_PUBLIC_API_URL` is wrong.
- **Login fails with a correct password:** the 6-digit code is wrong or expired, or the laptop clock is off.
- **Port 8080 already in use:** set `PORT=8090` before starting the server, and set `EXPO_PUBLIC_API_URL=http://localhost:8090` for the client.
- **CORS error in the browser:** add the page's origin to `COMETFIT_CORS_ORIGINS`.
- **"Could not decrypt field":** `COMETFIT_DATA_KEY` changed after the accounts were created. Restore the old key or reset the database.
