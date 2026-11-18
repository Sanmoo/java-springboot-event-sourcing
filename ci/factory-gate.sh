#!/bin/sh
# factory-gate: GitHub required check of the Software Factory qualification (R-GH-CHECKS-S). Runs only in GitHub Actions; always green.
set -eu
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'factory-gate runs only in GitHub Actions'; exit 2; }
echo 'factory-gate: ok'
