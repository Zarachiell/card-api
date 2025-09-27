#!/usr/bin/env bash
set -euo pipefail

# ================== PARAMS ==================
KC_BOOTSTRAP_URL="${KC_BOOTSTRAP_URL:-http://localhost:8081}"
REALM="${REALM:-cards}"
CLIENT_ID="${CLIENT_ID:-cards-client}"
SCOPE_READ="${SCOPE_READ:-card:read}"
SCOPE_WRITE="${SCOPE_WRITE:-card:write}"
ACCESS_TOKEN_LIFESPAN="${ACCESS_TOKEN_LIFESPAN:-300}"  # 5 min

: "${KEYCLOAK_ADMIN:?Env KEYCLOAK_ADMIN não definido}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Env KEYCLOAK_ADMIN_PASSWORD não definido}"

log() { echo ">> $*"; }
kcadm() { /opt/keycloak/bin/kcadm.sh "$@"; }

# ================== 1) START KC ==================
log "Starting Keycloak (args: $*)"
/opt/keycloak/bin/kc.sh "$@" &
KC_PID=$!

# ================== 2) LOGIN KCADM ==================
log "Waiting Keycloak to accept admin login at ${KC_BOOTSTRAP_URL} ..."
until kcadm config credentials --server "${KC_BOOTSTRAP_URL}" --realm master \
       --user "${KEYCLOAK_ADMIN}" --password "${KEYCLOAK_ADMIN_PASSWORD}" >/dev/null 2>&1
do sleep 2; done
log "kcadm login OK"

# ================== 3) REALM ==================
if ! kcadm get "realms/${REALM}" >/dev/null 2>&1; then
  log "Creating realm '${REALM}'"
  kcadm create realms -s realm="${REALM}" -s enabled=true
else
  log "Realm '${REALM}' already exists"
fi
log "Setting accessTokenLifespan=${ACCESS_TOKEN_LIFESPAN}s"
kcadm update "realms/${REALM}" -s accessTokenLifespan="${ACCESS_TOKEN_LIFESPAN}"

# ================== 4) CLIENT (confidential + SA) ==================
log "Ensuring client '${CLIENT_ID}' (confidential, service accounts ON)"
kcadm create clients -r "${REALM}" \
  -s clientId="${CLIENT_ID}" -s protocol=openid-connect \
  -s publicClient=false -s serviceAccountsEnabled=true \
  -s standardFlowEnabled=false -s directAccessGrantsEnabled=false >/dev/null 2>&1 || true

CID="$(kcadm get clients -r "${REALM}" -q clientId="${CLIENT_ID}" --fields id --format csv --noquotes | tr -d '\r' | head -n1)"
if [ -z "${CID}" ]; then
  echo "ERROR: could not resolve client id for '${CLIENT_ID}'"; exit 1
fi

# garantir flags no client
kcadm update "clients/${CID}" -r "${REALM}" \
  -s publicClient=false -s serviceAccountsEnabled=true \
  -s standardFlowEnabled=false -s directAccessGrantsEnabled=false >/dev/null

# Secret (garante existência e lê)
kcadm create "clients/${CID}/client-secret" -r "${REALM}" >/dev/null 2>&1 || true
CLIENT_SECRET="$(kcadm get "clients/${CID}/client-secret" -r "${REALM}" --fields value --format csv --noquotes | tr -d '\r' | head -n1)"

# ================== 5) CLIENT-SCOPES ==================
ensure_scope () {
  local NAME="$1"
  local ID
  ID="$(kcadm get client-scopes -r "${REALM}" -q name="${NAME}" --fields id --format csv --noquotes | tr -d '\r' | head -n1)"
  if [ -z "${ID}" ]; then
    log "Creating client-scope '${NAME}'"
    ID="$(kcadm create client-scopes -r "${REALM}" -s name="${NAME}" -s protocol="openid-connect" -i | tr -d '\r' | head -n1)"
  fi
  if [ -z "${ID}" ]; then
    echo "ERROR: could not ensure client-scope '${NAME}'"; exit 1
  fi
  echo "${ID}"
}

SREAD_ID="$(ensure_scope "${SCOPE_READ}")"
SWRITE_ID="$(ensure_scope "${SCOPE_WRITE}")"

# ================== 6) ASSIGN DEFAULT SCOPES AO CLIENTE ==================
assign_default_if_missing () {
  local SCOPE_ID="$1"
  # já atribuído?
  if ! kcadm get "clients/${CID}/default-client-scopes" -r "${REALM}" --fields id,name --format csv --noquotes | cut -d, -f1 | tr -d '\r' | grep -qx "${SCOPE_ID}"; then
    kcadm update "clients/${CID}/default-client-scopes/${SCOPE_ID}" -r "${REALM}" -n >/dev/null
  fi
}

assign_default_if_missing "${SREAD_ID}"
assign_default_if_missing "${SWRITE_ID}"

log "Client default scopes now:"
kcadm get "clients/${CID}/default-client-scopes" -r "${REALM}" --fields name | sed 's/^/   - /'

# ================== 7) RESUMO ==================
cat <<EOF
===== Keycloak bootstrap completed =====
Realm:            ${REALM}
Client ID:        ${CLIENT_ID}
Client Secret:    ${CLIENT_SECRET}

Issuer:           ${KC_BOOTSTRAP_URL}/realms/${REALM}
JWKS:             ${KC_BOOTSTRAP_URL}/realms/${REALM}/protocol/openid-connect/certs
Token endpoint:   ${KC_BOOTSTRAP_URL}/realms/${REALM}/protocol/openid-connect/token

Expected client default scopes: ${SCOPE_READ}, ${SCOPE_WRITE}
=======================================
EOF

# ================== 8) KEEP PROCESS ==================
wait "${KC_PID}"
