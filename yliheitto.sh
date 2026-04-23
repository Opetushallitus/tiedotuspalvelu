#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/scripts/lib/common-functions.sh"


function main {
  export ENV="$1"; shift
  case "${ENV}" in
    "hahtuva" | "dev" | "qa" | "prod")
      ;;
    *)
      fatal "Unknown env ${ENV}. Valid values: hahtuva, dev, qa, prod."
      ;;
  esac

  require_docker
  require_command aws
  require_command jq
  require_command pg_dump
  require_command pg_restore

  export IMAGE_TAG="tiedotuspalvelu/yliheitto-tunnel:latest"
  export CONTAINER_DUMP="tiedotuspalvelu-dump-${ENV}"
  export CONTAINER_RESTORE="tiedotuspalvelu-restore-${ENV}"
  export DUMP_FILENAME="tiedotuspalvelu-${ENV}-$( date +%Y-%m-%d ).dump"
  trap cleanup EXIT

  dump_from_old_database
  restore_to_new_database
}

function dump_from_old_database {
  export AWS_PROFILE="oph-yleiskayttoiset-${ENV}"
  check_aws_credentials

  case "${ENV}" in
    hahtuva) export TUNNEL_PORT="4201" ;;
    dev)     export TUNNEL_PORT="4202" ;;
    qa)      export TUNNEL_PORT="4203" ;;
    prod)    export TUNNEL_PORT="4204" ;;
  esac

  export SERVICE_NAME="OppijanumerorekisteriBastion"
  export DB_SECRET="OppijanumerorekisteriTiedotuspalveluDatabaseSecret"

  start_tunnel "$CONTAINER_DUMP"
  secret="$(aws secretsmanager get-secret-value --secret-id "${DB_SECRET}" --query 'SecretString' --output text)"
  db_username="$(echo "$secret" | jq -r '.username')"
  db_password="$(echo "$secret" | jq -r '.password')"
  db_name="$(echo "$secret" | jq -r '.dbname')"

  PGPASSWORD="$db_password" \
    pg_dump --user "$db_username" --host localhost --port "$TUNNEL_PORT" --dbname "$db_name" \
    --verbose --format custom --file "${DUMP_FILENAME}"
  docker kill "$CONTAINER_DUMP" || true
  docker wait "$CONTAINER_DUMP" || true
}

function restore_to_new_database {
  export AWS_PROFILE="oph-tiedotus-${ENV}"
  check_aws_credentials

  case "${ENV}" in
    hahtuva) export TUNNEL_PORT="4301" ;;
    dev)     export TUNNEL_PORT="4302" ;;
    qa)      export TUNNEL_PORT="4303" ;;
    prod)    export TUNNEL_PORT="4304" ;;
  esac

  export SERVICE_NAME="Bastion"
  export DB_SECRET="TiedotuspalveluDatabaseSecret"

  start_tunnel "$CONTAINER_RESTORE"
  secret="$(aws secretsmanager get-secret-value --secret-id "${DB_SECRET}" --query 'SecretString' --output text)"
  db_username="$(echo "$secret" | jq -r '.username')"
  db_password="$(echo "$secret" | jq -r '.password')"
  db_name="$(echo "$secret" | jq -r '.dbname')"
  PGPASSWORD="$db_password" \
    pg_restore --user "$db_username" --host localhost --port "$TUNNEL_PORT" --dbname "$db_name" \
     --verbose --clean --no-owner --no-privileges "${DUMP_FILENAME}"
  docker kill "$CONTAINER_RESTORE" || true
  docker wait "$CONTAINER_RESTORE" || true
}

function start_tunnel {
  local -r container_name="$1"; shift
  pushd "$repo/scripts/tunnel"
  docker build --tag "${IMAGE_TAG}" .
  info "Starting tunnel from port $TUNNEL_PORT to RDS"
  container_id=$( docker run \
    --env SERVICE_NAME --env DB_SECRET \
    --env AWS_PROFILE --env AWS_REGION --env AWS_DEFAULT_REGION \
    --env AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
    --env AWS_ACCESS_KEY_ID --env AWS_SECRET_ACCESS_KEY --env AWS_SESSION_TOKEN \
    --volume "${HOME}/.aws:/root/.aws" \
    --detach \
    --publish "$TUNNEL_PORT:1111" \
    --name "${container_name}" \
    --rm "${IMAGE_TAG}" )

  docker container logs --follow "${container_name}" &
  pid_logs=$!
  wait_for_container_to_be_healthy "$container_id"
  kill "${pid_logs}"
  popd
}

function check_aws_credentials {
  info "Checking AWS credentials for ${AWS_PROFILE}"
  if ! aws sts get-caller-identity >/dev/null; then
    fatal "AWS credentials are not configured env ${AWS_PROFILE}. Aborting."
  fi
}

function cleanup {
  echo "Cleaning up"
  docker kill "${CONTAINER_DUMP}" || true
  docker kill "${CONTAINER_RESTORE}" || true
  docker wait "${CONTAINER_DUMP}" || true
  docker wait "${CONTAINER_RESTORE}" || true
}

time main "$@"
