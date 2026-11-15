# Stage 5 build instruction (update)

New team inputs were added to `spec/` since the stage-4 build:

- `spec/07-logo/` : the CometFit logo (original on white, and a trimmed transparent version)
- `spec/03-team-decisions/TD-7.md` : where the logo is used

Follow `CLAUDE.md` exactly as before. Then:

1. Read TD-7 and look at both logo images (use the Read tool on them).
2. Generate the square icon assets from the transparent logo (Python with Pillow is available) and replace every default
   Expo icon in `app/client/assets/`; update `app.json` if needed. Add the logo to the sign-in flow screens per TD-7.
   Do not change server code or any other behavior.
3. Keep every existing test passing; run the client tests and a web export (`npx expo export --platform web`). A web dev
   server may be running on port 8081; do not stop it.
4. Add TD-7 and the logo files to `TRACEABILITY.md`, add rows for any new gaps to `ASSUMPTIONS.md`, log in `BUILD_LOG.md`.
5. Commit per change, citing TD-7. Do not push.
