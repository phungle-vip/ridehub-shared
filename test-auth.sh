#!/usr/bin/env bash
set -euo pipefail

# =========================
# HARD-CODE CONFIG (TEST ONLY)
# =========================
TOKEN_URL="https://keycloak.phungvip.io.vn/realms/jhipster/protocol/openid-connect/token"

# Client
CLIENT_ID="web_app"
CLIENT_SECRET="0TnpRknqjQYngbnbRW7hKECA8TbR4D7V"

# User (password grant)
USERNAME="admin"
PASSWORD="admin"

# Consul + Gateway
CONSUL_URL="https://consul.phungvip.io.vn"
CONSUL_ACL_TOKEN="f4security"
GATEWAY_URL="https://apigateway.phungvip.io.vn"

# Set to 1 if you want to print JSON responses (might leak info)
DEBUG=1

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
  -H "X-Consul-Token: $CONSUL_ACL_TOKEN" \
  "$CONSUL_URL/v1/catalog/services" | jq -r 'keys[]' | head -n 20

echo "==> 3) Test Gateway health via Bearer token ..."
curl -sS --fail-with-body \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$GATEWAY_URL/actuator/health" | jq

echo "==> Done."
