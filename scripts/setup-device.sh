#!/usr/bin/env sh
# Configure a checkout to use the tracked beads hooks and local database.
set -eu

git config core.hooksPath .githooks
echo "git core.hooksPath = .githooks"

if ! command -v bd >/dev/null 2>&1; then
  echo "bd is required; install beads before running this setup" >&2
  exit 1
fi

bd bootstrap --yes

# bd 1.0 may infer a Dolt remote from Git's origin. This repository syncs
# beads only through the tracked JSONL, and its agents must never push.
if bd dolt remote list 2>/dev/null | grep -q '^origin'; then
  bd dolt remote remove origin
fi
