#!/usr/bin/env bash
# Run one limner task with a Codex gpt-5.6-sol xhigh subagent.
# Usage: run-task.sh <task-id>   (brief read from briefs/<task-id>.md, preamble prepended)
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"   # reviews/2026-09-15/orchestration; prompts/logs go to $DIR/out (git-ignored)
ID="$1"
mkdir -p "$DIR/out"
{ cat "$DIR/preamble.md" "$DIR/briefs/$ID.md"
  printf '\n\n## Your identity\n\n`Codex gpt-5.6-sol (xhigh), run %s`\n' "$ID"; } > "$DIR/out/$ID.prompt.md"
echo "[$(date -Is)] start $ID" >> "$DIR/out/timeline.log"
timeout 14400 codex exec --skip-git-repo-check -C /home/tobiasosborne/Projects/limner \
  -m gpt-5.6-sol -c 'model_reasoning_effort="xhigh"' -s workspace-write \
  -o "$DIR/out/$ID.report.md" - < "$DIR/out/$ID.prompt.md" > "$DIR/out/$ID.log" 2>&1
rc=$?
echo "[$(date -Is)] end $ID rc=$rc" >> "$DIR/out/timeline.log"
exit $rc
