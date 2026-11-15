# Stage 3 build instruction (update)

New team inputs were added to `spec/` since the stage-2 build:

- `spec/05-ui-prototype/` : the team's Figma prototype screens (PNG, one per screen, plus all-screens.png)
- `spec/03-team-decisions/TD-4.md` : the app's look must follow that prototype
- `spec/03-team-decisions/TD-5.md` : two-step sign-in (password first, code only if the device is not remembered; QR for Duo Mobile at registration)

A first stage-3 run (old instruction, without TD-5) kept running after it was meant to be stopped. Before it was
stopped it committed `9bf70c1` (TD-4 theme, headers, 5-tab bar) and `897c1dc` (Sign in styled like the prototype, but
still with the code field on the sign-in screen). Its uncommitted half edits were discarded. Review both commits: keep
what is correct, and redo the sign-in screen for TD-5. Then continue with the steps below, skipping what is already done.

Follow `CLAUDE.md` exactly as before. Then:

1. Read TD-4 and TD-5 and look at every PNG in `spec/05-ui-prototype/` (use the Read tool on the images).
2. Implement TD-5 first (server + client + tests), then restyle the client per TD-4. Time is short, so work in this
   order and commit after each step: (a) TD-5 server, (b) shared theme + tab bar, (c) Sign in, verify and register
   screens (TD-5 flow in the prototype style), (d) Food & Water, (e) Social, (f) Dashboard, (g) Activities,
   (h) Account, (i) developer dashboard.
3. Only change server code for TD-5. Do not remove features. Keep every existing test passing (update tests whose
   behavior TD-5 changes) and run the server tests, the client tests and a web export
   (`npx expo export --platform web`) at the end. A web dev server may already be running on port 8081; do not stop
   it. Rebuild and restart the server with `docker compose up -d --build server` from `app/` after the TD-5 server
   change and check the demo accounts still sign in.
4. Mark A-32 `SUPERSEDED by TD-4` and the affected parts of A-39 `SUPERSEDED by TD-5` in `ASSUMPTIONS.md`; add rows
   for new gaps. Add TD-4, TD-5 and the prototype to `TRACEABILITY.md`. Log what you did in `BUILD_LOG.md`.
   Update `app/README.md` sign-in steps.
5. Commit per change, citing TD-4 or TD-5. Do not push.
