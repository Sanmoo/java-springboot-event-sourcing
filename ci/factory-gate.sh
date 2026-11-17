#!/bin/sh
# factory-gate: GitHub required check of the Software Factory qualification (R-GH-CHECKS). Runs only in GitHub Actions.
set -eu
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'factory-gate runs only in GitHub Actions'; exit 2; }
f=docs/FACTORY_CHECKS.md
[ -f "$f" ] || { echo "factory-gate: $f absent, nothing to gate"; exit 0; }
grep -qx 'Checked-by: factory-gate' "$f" || { echo "::error file=$f::factory-gate requires the exact line 'Checked-by: factory-gate' in $f"; exit 1; }
echo 'factory-gate: ok'
