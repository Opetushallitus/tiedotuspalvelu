#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/lib/common-functions.sh"

readonly default_host="virkailija.testiopintopolku.fi"
readonly categories=("omat-viestit" "tiedotuspalvelu")

function main {
  require_command curl
  require_command jq

  local -r host="${LOKALISOINTI_HOST:-${1:-${default_host}}}"
  local -r fallback_dir="${repo}/src/main/resources"

  local category
  for category in "${categories[@]}"; do
    update_category "${host}" "${category}" "${fallback_dir}"
  done
}

function update_category {
  local -r host="$1"
  local -r category="$2"
  local -r fallback_dir="$3"
  local -r url="https://${host}/lokalisointi/api/v1/localisation?category=${category}"
  local -r fallback_file="${fallback_dir}/localisations-fallback-${category}.json"

  info "Fetching localisations from ${url}"
  local result
  result=$(curl --fail --silent --show-error "${url}" \
    | jq '[.[] | {key, locale, value}] | sort_by(.key, .locale)')

  printf '%s\n' "${result}" > "${fallback_file}"

  info "Wrote $(printf '%s' "${result}" | jq 'length') localisations (category=${category}) to ${fallback_file}"
}

main "$@"
