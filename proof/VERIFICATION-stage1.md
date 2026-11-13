# Human verification of the stage-1 build (2026-11-13)

Done by the team (via assistant). Nothing in the app was changed and no feedback was given to the AI.

- **Docker / PostgreSQL:** `docker compose --profile full up -d --build` built the server image, started
  PostgreSQL 16, Flyway created the schema, server healthy. `node app/tools/smoke.mjs` against it: all checks pass.
- **Browser:** client started with `npx expo start --web`; signed in as demo student `alice` (password + TOTP).
  Today, Log, Activity, Friends and Profile tabs all render with seeded data; logging a 500 ml refill updated the
  water total (500 to 1000 ml) and showed the low-water warning (FR-6, NFR Safety). No browser console errors.
