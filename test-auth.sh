#!/usr/bin/env bash
set -euo pipefail

# =========================
# CONFIG FROM ENVIRONMENT VARIABLES
# =========================
# Required env vars:
#   KEYCLOAK_CLIENT_SECRET  - Keycloak client secret for web_app
#   CONSUL_ACL_TOKEN        - Consul ACL token
#   KEYCLOAK_USERNAME        - (optional, default: admin)
#   KEYCLOAK_PASSWORD        - (optional, default: admin)

TOKEN_URL="https://keycloak.phungvip.io.vn/realms/jhipster/protocol/openid-connect/token"

# Client
CLIENT_ID="web_app"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:?Error: KEYCLOAK_CLIENT_SECRET env var is required}"

# User (password grant)
USERNAME="${KEYCLOAK_USERNAME:-admin}"
PASSWORD="${KEYCLOAK_PASSWORD:-admin}"

# Consul + Gateway
CONSUL_URL="https://consul.phungvip.io.vn"
CONSUL_TOKEN="${CONSUL_ACL_TOKEN:?Error: CONSUL_ACL_TOKEN env var is required}"
GATEWAY_URL="https://apigateway.phungvip.io.vn"

# Set to 1 if you want to print JSON responses (might leak info)
DEBUG="${DEBUG:-0}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1"; exit 1; }; }
need curl
need jq

echo "==> 1) Request token from Keycloak ..."

# NOTE: password grant (you already enabled Direct access grants in client)
JSON="$(curl -sS --fail-with-body -X POST "$TOKEN_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD")" || {
    echo "❌ Token request failed"
    exit 1
  }

[[ "$DEBUG" == "1" ]] && echo "$JSON" | jq

ACCESS_TOKEN="$(echo "$JSON" | jq -r '.access_token // empty')"
if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "❌ No access_token returned. Full response:"
  echo "$JSON" | jq
  exit 1
fi
echo "✅ Got access token"

echo "==> 2) Test Consul via ACL token ..."
curl -sS --fail-with-body \
  -H "X-Consul-Token: $CONSUL_TOKEN" \
  "$CONSUL_URL/v1/catalog/services" | jq -r 'keys[]' | head -n 20

echo "==> 3) Test Gateway health via Bearer token ..."
curl -sS --fail-with-body \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$GATEWAY_URL/actuator/health" | jq

echo "==> Done."
