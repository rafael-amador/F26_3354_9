# Clean-room build rules (CometFit, SE 3354 Team 9)

You are building CometFit using ONLY the team's course deliverables in `spec/`.

1. **Only source of truth is `spec/`.** Do not use outside knowledge of any real
   product, company, API or person to decide what the app does. General
   programming knowledge (languages, frameworks, how to write a REST endpoint)
   is allowed; product decisions are not.
2. **Cite everything.** Every feature, screen, endpoint, class and table must
   trace to an ID or section in `spec/` (FR-x, UC-x, NFR type, diagram name,
   section number). Put the citation in a code comment and in `TRACEABILITY.md`.
3. **When the spec is silent, do not invent silently.** Make the smallest
   reasonable choice and record it in `ASSUMPTIONS.md` as:
   `A-n | what was missing | choice made | spec section it relates to`.
4. **External actors become stubs.** Any outside system named in the spec
   (e.g. a device, card reader, food service, monitoring service) is built as an
   interface plus a fake implementation, clearly labeled as a stub.
5. **Never edit** `spec/`, `proof/`, `PROMPT.md` or this file.
6. Write all code under `app/`. Commit after each use case with a message like
   `UC-1: <what> (FR-2, FR-6)`. Do not push.
7. Keep `BUILD_LOG.md` updated: one line per step (what you read, what you built,
   what failed, how you fixed it).
8. No web access. Do not read anything outside this folder.
