# Stage 6 build instruction (update)

New team input: `spec/03-team-decisions/TD-8.md` (more demo friends for the leaderboard).

Follow `CLAUDE.md` exactly as before. Then:

1. Read TD-8 (and TD-2, TD-6 it builds on).
2. Extend the demo data seeding per TD-8. Demo data only: no behavior, API or UI change.
3. Keep every existing test passing (update demo-data tests whose counts change) and add tests for TD-8. Run the server
   tests. Rebuild and restart the server with `docker compose up -d --build server` from `app/`, reset the demo data so it
   starts clean, run the read-only demo check, and confirm alice's leaderboards list six people with alice neither first
   nor last. A web dev server is running on port 8081; do not stop it.
4. Update `app/README.md` demo accounts table, `ASSUMPTIONS.md` (new gaps), `TRACEABILITY.md` (TD-8), `BUILD_LOG.md`.
5. Commit per change, citing TD-8. Do not push.
