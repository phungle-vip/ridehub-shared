#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARED_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

OPENAPI_DIR="${1:-$SHARED_ROOT/target/openapi}"
OUT_ROOT="${2:-$SHARED_ROOT/src/main/java}"
GEN_VER="${3:-7.14.0}"
VALIDATE="${4:-false}"

mkdir -p "$OUT_ROOT"

TOOLS_DIR="${HOME}/.cache/openapi-generator"
CLI_JAR="$TOOLS_DIR/openapi-generator-cli-${GEN_VER}.jar"
LOG_DIR="$SHARED_ROOT/target/openapi-gen-logs"
SRC_FOLDER=""

mkdir -p "$TOOLS_DIR" "$LOG_DIR"

# Require java & curl
for bin in java curl; do
  command -v "$bin" >/dev/null 2>&1 || { echo "ERROR: $bin is required."; exit 1; }
done

# Download CLI if missing
if [ ! -f "$CLI_JAR" ]; then
  echo "==> Downloading openapi-generator-cli ${GEN_VER}"
  curl -fsSL -o "$CLI_JAR" \
    "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${GEN_VER}/openapi-generator-cli-${GEN_VER}.jar"
fi

# Create a temporary directory with Unix-style paths
TEMP_SPECS_DIR="$(mktemp -d)"
trap "rm -rf '$TEMP_SPECS_DIR'" EXIT

shopt -s nullglob
ORIGINAL_SPECS=()

# 1. Check specified OPENAPI_DIR first
if [ -d "$OPENAPI_DIR" ]; then
  ORIGINAL_SPECS=("$OPENAPI_DIR"/*.json "$OPENAPI_DIR"/*.yaml "$OPENAPI_DIR"/*.yml)
fi

# 2. Fallback: Read directly from ridehub-contract if OPENAPI_DIR is empty (offline mode)
if [ ${#ORIGINAL_SPECS[@]} -eq 0 ]; then
  CONTRACT_OPENAPI="$SHARED_ROOT/../ridehub-contract/src/main/openapi"
  if [ -d "$CONTRACT_OPENAPI" ]; then
    ORIGINAL_SPECS=("$CONTRACT_OPENAPI"/*.json "$CONTRACT_OPENAPI"/*.yaml "$CONTRACT_OPENAPI"/*.yml)
    if [ ${#ORIGINAL_SPECS[@]} -gt 0 ]; then
      echo "==> No live specs found in $OPENAPI_DIR. Using contract specs from: $CONTRACT_OPENAPI"
    fi
  fi
fi

if [ ${#ORIGINAL_SPECS[@]} -eq 0 ]; then
  echo "No OpenAPI specs found in $OPENAPI_DIR or ridehub-contract/src/main/openapi"
  exit 0
fi

# Copy specs to temp directory to avoid Windows/submodule path issues
echo "==> Found ${#ORIGINAL_SPECS[@]} OpenAPI spec(s). Preparing generation..."
for spec in "${ORIGINAL_SPECS[@]}"; do
  cp "$spec" "$TEMP_SPECS_DIR/"
done

SPECS=("$TEMP_SPECS_DIR"/*.json "$TEMP_SPECS_DIR"/*.yaml "$TEMP_SPECS_DIR"/*.yml)

OK=0
FAIL=0
FAILED_LIST=()

for SPEC in "${SPECS[@]}"; do
  # Raw service name from filename
  SVC="$(basename "$SPEC")"
  SVC="${SVC%.*}"

  # Normalize service name to standard 'ms{service}' format
  # e.g. msbooking -> msbooking, booking-api -> msbooking, ms_route -> msroute
  NORM_SVC="$(echo "$SVC" | tr '[:upper:]' '[:lower:]' | sed -E 's/-api$//' | sed -E 's/[^a-z0-9]//g')"
  if [[ ! "$NORM_SVC" =~ ^ms ]]; then
    NORM_SVC="ms${NORM_SVC}"
  fi

  SVC_PKG="$NORM_SVC"
  API_SUFFIX="$(echo "$NORM_SVC" | sed -E 's/^([a-z])/\U\1/')Api"

  OUT_DIR="$OUT_ROOT"
  LOG_FILE="$LOG_DIR/${SVC_PKG}.log"

  echo "==> Generating Feign client for ${SVC_PKG} (from $(basename "$SPEC"))"

  # Clean only this service's generated client package
  rm -rf "${OUT_DIR}/com/ridehub/feign/${SVC_PKG}/client" || true

  # Set JVM system properties to disable validation and avoid path issues
  JAVA_OPTS="-Duser.timezone=UTC -Dfile.encoding=UTF-8"

  if JAVA_OPTS="$JAVA_OPTS" java -jar "$CLI_JAR" generate \
      -g java \
      --library feign \
      -i "$SPEC" \
      -o "$OUT_DIR" \
      -p sourceFolder="${SRC_FOLDER}" \
      -p apiPackage="com.ridehub.feign.${SVC_PKG}.client.api" \
      -p modelPackage="com.ridehub.feign.${SVC_PKG}.client.model" \
      -p invokerPackage="com.ridehub.feign.${SVC_PKG}.client.invoker" \
      --additional-properties useJakartaEe=true,dateLibrary=java8,interfaceOnly=true,useTags=true,hideGenerationTimestamp=true,apiNameSuffix="${API_SUFFIX}",useBeanValidation=true,performBeanValidation=true,useOptional=true,generateParameterObjects=true,aggregateParameters=true,paramNamingStrategy=camelCase,groupByTags=true,useSpringBoot3=true,serializableModel=true \
      --global-property models,apis,supportingFiles,modelTests=false,apiTests=false,modelDocs=false,apiDocs=false \
      --skip-validate-spec \
      --enable-post-process-file \
      >"$LOG_FILE" 2>&1; then

    # Remove generator leftovers outside the com folder
    rm -rf "${OUT_DIR}/test" \
           "${OUT_DIR}/docs" \
           "${OUT_DIR}/pom.xml" \
           "${OUT_DIR}/build.gradle" \
           "${OUT_DIR}/README.md" \
           "${OUT_DIR}/.openapi-generator" \
           "${OUT_DIR}/.openapi-generator-ignore" \
           "${OUT_DIR}/.github" \
           "${OUT_DIR}/.gitignore" \
           "${OUT_DIR}/.travis.yml" \
           "${OUT_DIR}/build.sbt" \
           "${OUT_DIR}/git_push.sh" \
           "${OUT_DIR}/gradle" \
           "${OUT_DIR}/gradlew" \
           "${OUT_DIR}/gradlew.bat" \
           "${OUT_DIR}/gradle.properties" \
           "${OUT_DIR}/settings.gradle" \
           "${OUT_DIR}/api" \
           "${OUT_DIR}/src" 2>/dev/null || true

    echo "   OK: ${SVC_PKG} (logs: ${LOG_FILE})"
    OK=$((OK+1))
  else
    echo " FAIL: ${SVC_PKG} (see ${LOG_FILE})"
    FAIL=$((FAIL+1))
    FAILED_LIST+=("$SVC_PKG")
  fi
done

echo
echo "==> Generation summary: OK=${OK} / FAIL=${FAIL}"
if [ "$FAIL" -gt 0 ]; then
  printf 'Failed services: %s\n' "${FAILED_LIST[*]}"
  echo "Check logs under: ${LOG_DIR}"
  exit 1
fi