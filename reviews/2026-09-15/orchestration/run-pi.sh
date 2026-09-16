#!/usr/bin/env bash
# Run one limner brief with a pi worker (default: deepseek-flash, thinking high).
# Usage: run-pi.sh <task-id> [model] [thinking]
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ID="$1"; MODEL="${2:-deepseek-flash}"; THINK="${3:-high}"
mkdir -p "$DIR/out"
{ cat "$DIR/preamble.md" "$DIR/briefs/$ID.md"
  printf '\n\n## Your identity\n\n`pi %s (%s), run %s`\n' "$MODEL" "$THINK" "$ID"; } > "$DIR/out/$ID.prompt.md"
echo "[$(date -Is)] start $ID (pi $MODEL $THINK)" >> "$DIR/out/timeline.log"
cd /home/tobiasosborne/Projects/limner
timeout 14400 pi -p --provider deepseek --model "$MODEL" --thinking "$THINK" \
  --no-session --approve "$(cat "$DIR/out/$ID.prompt.md")" > "$DIR/out/$ID.log" 2>&1
rc=$?
echo "[$(date -Is)] end $ID rc=$rc" >> "$DIR/out/timeline.log"
exit $rc
