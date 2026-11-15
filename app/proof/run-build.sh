#!/usr/bin/env bash
# Exact command used to run the clean-room build. Usage: proof/run-build.sh <prompt-file> <transcript-name>
cd "$(dirname "$0")/.."
export CLAUDE_CODE_DISABLE_AUTO_MEMORY=1
claude -p "$(cat "$1")" \
  --setting-sources project \
  --strict-mcp-config --mcp-config '{"mcpServers":{}}' \
  --disallowed-tools WebFetch WebSearch RemoteTrigger PushNotification CronCreate ScheduleWakeup SendMessage DesignSync "Bash(git push:*)" "Bash(curl:*)" "Bash(wget:*)" \
  --allowedTools Read Glob Grep Edit Write Task "Bash(mvn:*)" "Bash(java:*)" "Bash(javac:*)" "Bash(npm:*)" "Bash(npx:*)" "Bash(node:*)" "Bash(docker:*)" "Bash(git add:*)" "Bash(git commit:*)" "Bash(git status:*)" "Bash(git log:*)" "Bash(git diff:*)" "Bash(ls:*)" "Bash(mkdir:*)" "Bash(cat:*)" "Bash(cd:*)" "Bash(pdftotext:*)" "Bash(sleep:*)" "Bash(cp:*)" "Bash(mv:*)" "Bash(pdftoppm:*)" "Bash(pdfimages:*)" "Bash(pdfinfo:*)" "Bash(wc:*)" "Bash(head:*)" "Bash(tail:*)" "Bash(grep:*)" "Bash(find:*)" "Bash(which:*)" "Bash(echo:*)" "Bash(sed:*)" "Bash(sort:*)" "Bash(uniq:*)" "Bash(diff:*)" "Bash(touch:*)" "Bash(test:*)" "Bash(pwd:*)" "Bash(python:*)" "Bash(python3:*)" "Bash(bash:*)" "Bash(sh:*)" "Bash(./mvnw:*)" "Bash(chmod:*)" "Bash(xargs:*)" "Bash(tee:*)" "Bash(rm:*)" "Bash(timeout:*)" \
  --permission-mode acceptEdits \
  --output-format stream-json --verbose \
  < /dev/null > "proof/transcripts/$2.jsonl" 2> "proof/transcripts/$2.stderr.txt"
echo "exit $?" >> "proof/transcripts/$2.stderr.txt"
