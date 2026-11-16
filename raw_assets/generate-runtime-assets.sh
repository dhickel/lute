#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$project_root/gradlew" -p "$project_root" :util:generateRuntimeAssets "$@"
