# How to verify this app was built only from our deliverables

1. **Inputs are fixed and timestamped.** `spec/` holds only Team 9 deliverables.
   `proof/MANIFEST.sha256` lists the SHA-256 of every input file. It was
   committed before any code existed (see `git log`). Stage-1 inputs are the
   exact files we submitted on Canvas; `proof/canvas-submissions.json` gives
   their Canvas submission times and hashes, which anyone with course access
   can re-check. Later stages (Deliverable 2 draft, unit-test code, Figma
   screens) are added in their own commits with their own hashes.
   Run: `sha256sum -c proof/MANIFEST.sha256`
2. **The AI was isolated.** The build ran in a fresh Claude Code session with no
   user memory, no personal instructions, no plugins/MCP tools and no web
   access (`.claude/settings.json`, `proof/isolation-check/`). The isolation
   check asks the session what it knows beyond this folder and records its
   startup configuration (tools, MCP servers, memory files).
3. **The only instruction was `PROMPT.md`** plus the rules in `CLAUDE.md`.
   Any human help is listed in `proof/HUMAN_INTERVENTIONS.md`.
4. **Full transcript** of the build session is in `proof/transcripts/`.
5. **Traceability:** `TRACEABILITY.md` maps every FR/UC to code and tests;
   `ASSUMPTIONS.md` lists every gap in the spec the AI had to fill.
