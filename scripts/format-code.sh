#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd .. && pwd)"

cd "$repo/infra"
npx prettier . --write

cd "$repo"
./mvnw -q fmt:format

cd "$repo/web"
npx prettier . --write
