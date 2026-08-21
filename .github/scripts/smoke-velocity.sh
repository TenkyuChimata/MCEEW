#!/usr/bin/env bash
set -euo pipefail

ready_marker='Done ('
reload_marker='[MCEEW] Configuration reloaded successfully.'
ready_timeout_seconds="${VELOCITY_SMOKE_READY_TIMEOUT_SECONDS:-20}"
reload_timeout_seconds="${VELOCITY_SMOKE_RELOAD_TIMEOUT_SECONDS:-15}"
process_timeout_seconds="${VELOCITY_SMOKE_PROCESS_TIMEOUT_SECONDS:-45}"
poll_seconds="${VELOCITY_SMOKE_POLL_SECONDS:-0.1}"

print_log_tail() {
  local log_file="$1"

  echo "Last 150 lines of $log_file:" >&2
  if [[ -f "$log_file" ]]; then
    tail -n 150 "$log_file" >&2
  else
    echo '(runtime log does not exist yet)' >&2
  fi
}

wait_for_log() {
  local log_file="$1"
  local needle="$2"
  local timeout_seconds="$3"
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    if [[ -f "$log_file" ]] && grep -Fq -- "$needle" "$log_file"; then
      echo "Velocity smoke observed: $needle" >&2
      return 0
    fi
    sleep "$poll_seconds"
  done

  echo "Timed out after ${timeout_seconds}s waiting for: $needle" >&2
  print_log_tail "$log_file"
  return 1
}

produce_commands() {
  local log_file="$1"

  if ! wait_for_log "$log_file" "$ready_marker" "$ready_timeout_seconds"; then
    printf '%s\n' shutdown
    return 1
  fi

  echo 'Velocity smoke sending: eew, mceew, eew reload' >&2
  printf '%s\n' eew mceew 'eew reload'

  if ! wait_for_log "$log_file" "$reload_marker" "$reload_timeout_seconds"; then
    printf '%s\n' shutdown
    return 1
  fi

  echo 'Velocity smoke sending: shutdown' >&2
  printf '%s\n' shutdown
}

run_smoke() {
  local proxy_jar="$1"
  local log_file="$2"
  local -a pipeline_statuses

  rm -f "$log_file"
  set +e
  produce_commands "$log_file" | \
    timeout "${process_timeout_seconds}s" java -jar "$proxy_jar" 2>&1 | \
    tee "$log_file"
  pipeline_statuses=("${PIPESTATUS[@]}")
  set -e

  if (( pipeline_statuses[0] != 0 || pipeline_statuses[1] != 0 || pipeline_statuses[2] != 0 )); then
    echo "Velocity smoke pipeline failed: producer=${pipeline_statuses[0]} proxy=${pipeline_statuses[1]} tee=${pipeline_statuses[2]}" >&2
    print_log_tail "$log_file"
    return 1
  fi
}

if [[ "${1:-}" == '--commands' ]]; then
  if [[ $# -ne 2 ]]; then
    echo "Usage: $0 --commands <runtime.log>" >&2
    exit 2
  fi
  produce_commands "$2"
elif [[ $# -eq 1 || $# -eq 2 ]]; then
  run_smoke "$1" "${2:-runtime.log}"
else
  echo "Usage: $0 <velocity.jar> [runtime.log]" >&2
  exit 2
fi
