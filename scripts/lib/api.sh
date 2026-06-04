#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

require_command() {
	local command_name="$1"
	if ! command -v "$command_name" >/dev/null 2>&1; then
		echo "Error: '$command_name' is required but was not found in PATH." >&2
		exit 127
	fi
}

require_tools() {
	require_command curl
	require_command jq
}

new_idempotency_key() {
	if command -v uuidgen >/dev/null 2>&1; then
		uuidgen
	else
		date +%s%N
	fi
}

usage() {
	echo "Usage: $1" >&2
	exit 2
}

pretty_json() {
	jq .
}

api_get() {
	local path="$1"
	curl --fail-with-body --silent --show-error \
		-X GET \
		"${BASE_URL}${path}" | pretty_json
}

api_post() {
	local path="$1"
	local body
	if [[ $# -ge 2 ]]; then
		body="$2"
	else
		body="{}"
	fi
	local idempotency_key="${IDEMPOTENCY_KEY:-$(new_idempotency_key)}"

	curl --fail-with-body --silent --show-error \
		-X POST \
		-H "Content-Type: application/json" \
		-H "Idempotency-Key: ${idempotency_key}" \
		--data-raw "$body" \
		"${BASE_URL}${path}" | pretty_json
}
