#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

chmod +x .githooks/pre-commit
git config core.hooksPath .githooks

if ! command -v mvn >/dev/null 2>&1; then
  echo "Hooks configured. Maven is required to run the formatter and was not found on PATH."
else
  echo "Git hooks configured. Java sources will be formatted before each commit."
fi
