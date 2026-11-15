# Isolation check — 2026-11-13 ~10:05 CST

Fresh `claude -p` session in this folder, launched with:
`--setting-sources project --strict-mcp-config --mcp-config {"mcpServers":{}} --disallowed-tools WebFetch WebSearch`
and `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`. Raw output: `isolation-check.jsonl`.

Startup config recorded by Claude Code (`system/init` line):
- `mcp_servers: []` (no connectors: no Gmail, Drive, Canvas, browser, notes)
- tools: built-in file/shell tools only; no WebFetch / WebSearch
- plugins: only Claude Code's built-in ones; none of the user's installed plugins

The session's own answer: the only instruction file it saw was this folder's
`CLAUDE.md`; no memory files; it knew the user's name only from the git author
line and an email address Claude Code attaches by default; it knew nothing
about a notes vault, school or personal life.
