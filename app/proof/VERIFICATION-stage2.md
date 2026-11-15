# Human verification of the stage-2 build (2026-11-14)

Done by Rafael (via assistant) after the build session ended. No app code was changed.

- `docker compose up` from `app/`: MySQL 8.4 and the server start healthy (`/actuator/health` = UP).
- `node tools/smoke.mjs`: all checks pass (UC-1 to UC-4, FR-1 to FR-8, TD-1 remember-device).
- Browser (Expo web, localhost:8081):
  - Sign in as alice with a 6-digit code and "Remember this device for 30 days" on: works. After signing out,
    the screen says the device is remembered until 12/14/2026 and asks for username + password only.
  - Log tab: switching to gallons and entering 0.3 shows "Added 1.13L to Alice" (matches TC-1).
  - Friends tab: Bob is a friend, his stats are shared, leaderboard shows alice 65 / bob 30 (TD-2).
  - Create account: shows "Add to authenticator app", "Copy setup key", the QR code (wide window) and the setup key (TD-1).
- Then `docker compose down -v` + `up` to remove the smoke-test and browser-test data; demo data is fresh.

Seen, not fixed (cosmetic): the seeded "Breakfast (demo)" meal shows unrounded macros (e.g. 17.9375 g).
