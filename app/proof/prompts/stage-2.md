# Stage 2 build instruction (update)

New team deliverables were added to `spec/` since the stage-1 build:

- `spec/02-deliverable-2-draft/` : the team's Deliverable 2 report (draft)
- `spec/03-team-decisions/` : team decisions TD-1..TD-3 made after reviewing the stage-1 app
- `spec/04-team-repo-code/` : unit code and tests the team wrote (water logging)

Follow `CLAUDE.md` exactly as before. Then:

1. Read every file in `spec/` again, old and new.
2. Compare the existing app in `app/` with the spec. List every difference
   (feature, data model, validation, technology, screen, test) in `BUILD_LOG.md`.
3. Update the app so it matches the spec. Where spec documents disagree, use this
   order (later wins): Project Proposal, Deliverable 1, Deliverable 2 draft,
   team repo code (for the unit it covers), team decisions.
4. In `ASSUMPTIONS.md`, mark every assumption that the new inputs answer as
   `SUPERSEDED by <file/section>` (keep the row), and add new rows for new gaps.
5. Keep `TRACEABILITY.md` complete and add the new sources.
6. Keep every existing test passing, add tests for what changed, and run them.
   Make sure the stack still starts with `docker compose up` from `app/` and that
   the demo data starts clean (remove volumes with test leftovers if needed).
7. Update `app/README.md` if run steps changed. Commit per change, citing the spec. Do not push.
