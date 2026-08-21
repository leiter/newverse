#!/usr/bin/env sh
# The firebase CLI is a self-contained binary that puts its own bundled node
# (v14, no --test runner) ahead of ours on PATH for the script it runs. Resolve
# the real node first and hand emulators:exec an absolute path.
set -e
NODE_BIN="$(command -v node)"
exec firebase emulators:exec \
  --only database \
  --project newverse-rules-test \
  "$NODE_BIN --test --test-concurrency=1"
