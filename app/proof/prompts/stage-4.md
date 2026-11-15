# Stage 4 build instruction (update)

New team inputs were added to `spec/` since the stage-3 build:

- `spec/06-utd-dining/utd-dining-menus-2026-11-16.json` : real UTD Dining locations and menu items with published nutrition, with source URLs
- `spec/03-team-decisions/TD-6.md` : the dining feed must use that data

Follow `CLAUDE.md` exactly as before. Then:

1. Read TD-6 and the JSON file.
2. Update the UTD Dining stub feed, the demo data and the Food & Water dining menu UI per TD-6 (keep the TD-4 prototype style).
   Show "not published" for missing macros.
3. Keep every existing test passing (update tests that relied on the placeholder items) and add tests for TD-6:
   all 7 locations and 54 items load, null macros stay null, logging a calories-only item works.
   Run server and client tests and a web export (`npx expo export --platform web`). A web dev server may be running on
   port 8081; do not stop it. Rebuild and restart the server with `docker compose up -d --build server` from `app/`,
   reset the demo data so it starts clean, and check the demo accounts still sign in.
4. Mark A-16 `SUPERSEDED by TD-6` in `ASSUMPTIONS.md`; add rows for new gaps. Add TD-6 and the JSON to
   `TRACEABILITY.md`. Log what you did in `BUILD_LOG.md`.
5. Commit per change, citing TD-6. Do not push.
