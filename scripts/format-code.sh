#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd .. && pwd)"
source "${repo}/scripts/lib/common-functions.sh"

function main {
  run_prettier_format
  run_java_format
}

function run_prettier_format {
  cd "$repo/infra"
  init_nodejs
  npm_ci_if_needed
  npx prettier . --write

  cd "$repo/web"
  init_nodejs
  npm_ci_if_needed
  npx prettier . --write
}

function run_java_format {
  cd "$repo"
  select_java_version 21
  ./mvnw -q fmt:format
}

main "$@"
