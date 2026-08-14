#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <MCEEW-BungeeCord.jar> <proxy.jar> <runtime-label>"
  exit 2
fi

plugin_jar="$(realpath "$1")"
proxy_jar="$(realpath "$2")"
runtime_label="$3"
runtime="$(mktemp -d)"
trap 'rm -rf "$runtime"' EXIT

expected_version="$(unzip -p "$plugin_jar" bungee.yml | awk -F': ' '$1 == "version" { print $2 }')"
if [[ -z "$expected_version" ]]; then
  echo "Bungee metadata contains no version."
  exit 1
fi

mkdir -p "$runtime/plugins/MCEEW"
cp "$plugin_jar" "$runtime/plugins/"
cp "$proxy_jar" "$runtime/proxy.jar"
unzip -p "$plugin_jar" config.yml > "$runtime/plugins/MCEEW/config.yml"
sed -i '0,/enabled: true/s//enabled: false/' "$runtime/plugins/MCEEW/config.yml"

# Disable optional proxy modules through the proxy's own supported modules.yml.
# The MCEEW smoke exercises only the plugin and avoids unrelated module downloads.
printf 'version: 2\nmodules: []\n' > "$runtime/modules.yml"

cd "$runtime"
set -o pipefail
(sleep 6; echo eew; echo mceew; echo 'eew info jma'; echo 'eew info cenc'; \
  echo 'eew reload'; sleep 2; echo end) | \
  timeout 30s java -jar proxy.jar 2>&1 | tee runtime.log

grep -F "Loaded plugin MCEEW version $expected_version" runtime.log
grep -F "Enabled plugin MCEEW version $expected_version" runtime.log
grep -F "MCEEW BungeeCord $expected_version platform shell initialized." runtime.log
grep -F 'MCEEW BungeeCord operational runtime is disabled by configuration.' runtime.log
test "$(grep -Fc 'Plugin version: v' runtime.log)" -ge 2
grep -F 'Platform: BungeeCord / Waterfall' runtime.log
test "$(grep -Fc 'MCEEW runtime is not currently available.' runtime.log)" -eq 2
grep -F 'Configuration reloaded successfully.' runtime.log
grep -F 'MCEEW BungeeCord platform shell shut down.' runtime.log
grep -Fx 'platform_config_version: 1' plugins/MCEEW/config.yml
grep -Fx '  enabled: false' plugins/MCEEW/config.yml

if grep -Fq 'Connected to WebSocket API.' runtime.log; then
  echo "$runtime_label unexpectedly opened the Wolfx WebSocket."
  exit 1
fi
if grep -Eq 'ClassNotFoundException|NoClassDefFoundError|UnsupportedClassVersionError|NoSuchMethodError|AbstractMethodError|LinkageError' runtime.log; then
  echo "$runtime_label found a linkage failure."
  exit 1
fi

echo "$runtime_label smoke passed with MCEEW BungeeCord $expected_version."
