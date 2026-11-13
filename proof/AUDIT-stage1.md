# Transcript audit, stage-1 build (2026-11-13, 10:07-10:46 CST)

Checked `proof/transcripts/build-1-stage1.jsonl` (210 tool calls: 123 Write, 58 Bash, 16 Read, 6 Edit, other).
Every tool call was scanned for web URLs, paths outside `C:\CometFit-cleanroom\build`, curl/wget, and
user folders. Result:

- **Web:** no web pages or APIs were read. All HTTP calls in the transcript go to `localhost`
  (the app's own test server).
- **Files:** every Read, Write and Edit targets a path inside this folder. Inputs read were the two PDFs in
  `spec/01-canvas-submitted/` (text via `pdftotext`, diagram pages rendered with `pdftoppm`).
- **Package downloads:** `npm install` / Maven downloaded open-source libraries (Spring Boot, Expo,
  React Native, JUnit, etc.) from the public package registries. These are programming libraries, not
  product information; the full list is in `app/server/pom.xml` and `app/client/package.json`.
- **Template file:** the Expo project template generated an `AGENTS.md` telling agents to read online
  docs. The AI did not follow it, neutralized it, and asked a human to delete it (see HUMAN_INTERVENTIONS).

Pushed to GitHub as branch `cleanroom-app`. First push (inputs + isolation check only, no code):
2026-11-13T16:14:56Z per GitHub's activity log.
