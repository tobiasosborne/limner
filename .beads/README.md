# Beads issue tracking

The local Dolt database under this directory is git-ignored. The tracked
`issues.jsonl` export is the only issue-state snapshot shared through Git.

Run `sh scripts/setup-device.sh` once in a fresh clone. It configures the
tracked hooks and bootstraps the local database without replacing existing
issue state.

Useful commands:

```sh
bd ready
bd show <id>
bd update <id> --claim
bd close <id>
bd export -o .beads/issues.jsonl
```

The pre-commit hook exports and stages `issues.jsonl`; the post-merge hook
imports an incoming snapshot. Do not configure or push a beads remote for
this repository.
