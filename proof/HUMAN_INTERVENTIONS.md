# Human interventions

Every time a person changed code, config or inputs after the build started, it is listed here.

| When | Who | What | Why |
|---|---|---|---|
| 2026-11-13 10:10 | Rafael (via assistant) | Stopped the first build run after ~5 min and restarted it with more shell commands allowed (pdftoppm, wc, grep, etc.). No inputs, rules or prompt changed. Transcript kept as build-0-aborted-permissions. | First run could not render the diagram pages because the command was not on the allow list. |
| 2026-11-13 10:50 | Rafael (via assistant) | After the stage-1 build finished: stopped two leftover smoke-test server processes (ports 8098/8099), deleted Expo template files the AI flagged but could not delete (app/client/AGENTS.md, app/client/.claude/settings.json, app/client/LICENSE). No app code changed. | Cleanup the AI requested in its final report. |
| 2026-11-13 11:05 | Rafael (via assistant) | Added root README.md (prompt citation required by the instructor, conditions, human-input policy) and spec/03-team-decisions/ for future team corrections. No app code changed. | Instructor asked for a prompt citation in the README. |
