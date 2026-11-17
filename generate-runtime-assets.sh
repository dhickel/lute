#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -d "$project_root/raw_assets" ]]; then
    printf 'Missing raw_assets directory. Restore the local source assets before generating.\n' >&2
    exit 1
fi
mkdir -p "$project_root/assets"
exec "$project_root/gradlew" -p "$project_root" :util:generateRuntimeAssets "$@"
