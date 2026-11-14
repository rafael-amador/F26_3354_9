# Transcript audit, stage-2 build (2026-11-14, about 10:35-11:05 CST, 28 min)

Checked `proof/transcripts/build-2-stage2.jsonl` (175 tool calls: 70 Bash, 47 Read, 28 Edit, 18 Write, 10 Grep, 2 Glob).
Startup message: no MCP servers, no web tools, model claude-opus-5-5, permission mode acceptEdits.

- **Web:** no web pages or APIs were read. Every URL in the transcript is `localhost` (the app and its
  database) or text inside app code (the `otpauth://` setup link format).
- **Files:** every Read, Write and Edit targets a path inside this folder. Two attempts to look outside it
  were **blocked** by the permission rules: listing the local Maven library cache (`~/.m2/repository`, to see
  whether the MySQL driver was already downloaded) and a scratch folder `/tmp/cf`. Neither returned anything.
- **Inputs read:** the PDFs in `spec/01-canvas-submitted/` and `spec/02-deliverable-2-draft/`, the TD files in
  `spec/03-team-decisions/`, and the team code in `spec/04-team-repo-code/`.
- **Package downloads:** Maven downloaded the MySQL JDBC driver and Flyway MySQL support from the public
  registry (programming libraries, listed in `app/server/pom.xml`). Docker pulled the official `mysql:8.4` image.
- **Protected files:** `git diff 96ddc59..8f1be4f` shows no change to `spec/`, `proof/`, `PROMPT.md`,
  `CLAUDE.md` or the root `README.md`.
