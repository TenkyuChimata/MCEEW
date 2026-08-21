#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime="$(mktemp -d)"
producer_pid=''
trap '[[ -z "$producer_pid" ]] || kill "$producer_pid" 2>/dev/null || true; rm -rf "$runtime"' EXIT

runtime_log="$runtime/runtime.log"
commands="$runtime/commands.log"
diagnostics="$runtime/diagnostics.log"
: > "$runtime_log"

VELOCITY_SMOKE_READY_TIMEOUT_SECONDS=3 \
VELOCITY_SMOKE_RELOAD_TIMEOUT_SECONDS=3 \
VELOCITY_SMOKE_POLL_SECONDS=0.01 \
  bash "$script_dir/smoke-velocity.sh" --commands "$runtime_log" \
  > "$commands" 2> "$diagnostics" &
producer_pid=$!

sleep 0.1
printf '%s\n' \
  '[00:00:00 INFO]: Booting up Velocity...' \
  '[00:00:06 INFO]: Loading plugins; startup has exceeded the former five-second delay.' \
  >> "$runtime_log"
sleep 0.1
if [[ -s "$commands" ]]; then
  echo 'Velocity smoke emitted a command before the readiness marker.' >&2
  exit 1
fi

printf '%s\n' '[00:00:07 INFO]: Done (7.00s)!' >> "$runtime_log"
deadline=$((SECONDS + 2))
while (( SECONDS < deadline )) && (( $(wc -l < "$commands") < 3 )); do
  sleep 0.01
done
mapfile -t before_reload < "$commands"
if [[ "${before_reload[*]}" != 'eew mceew eew reload' ]]; then
  echo "Unexpected commands before reload completion: ${before_reload[*]}" >&2
  exit 1
fi

printf '%s\n' '[00:00:08 INFO]: Reload is still in progress.' >> "$runtime_log"
sleep 0.1
if (( $(wc -l < "$commands") != 3 )); then
  echo 'Velocity smoke emitted shutdown before reload completion.' >&2
  exit 1
fi

printf '%s\n' '[00:00:09 INFO]: [MCEEW] Configuration reloaded successfully.' >> "$runtime_log"
wait "$producer_pid"
producer_pid=''

mapfile -t final_commands < "$commands"
expected_commands=(eew mceew 'eew reload' shutdown)
if (( ${#final_commands[@]} != ${#expected_commands[@]} )); then
  echo "Expected ${#expected_commands[@]} commands, found ${#final_commands[@]}." >&2
  exit 1
fi
for index in "${!expected_commands[@]}"; do
  if [[ "${final_commands[$index]}" != "${expected_commands[$index]}" ]]; then
    echo "Command $index was '${final_commands[$index]}', expected '${expected_commands[$index]}'." >&2
    exit 1
  fi
done

echo 'Velocity smoke readiness regression passed.'
