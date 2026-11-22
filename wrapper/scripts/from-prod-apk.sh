#!/bin/sh
# Fills www/ with the web bundle embedded in the published prod APK
# (assets/public, env already injected: TRITERRA_ENV="prod", TRITERRA_WRAPPER="android").
# Not committed (.gitignore): the source of truth is field-region-map.
# Firebase config for the .auto app comes from android/app/google-services.json (owner-provided).
set -eu
cd "$(dirname "$0")/.."
APK="${1:-../triterra-prod.apk}"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
unzip -q "$APK" 'assets/public/*' -d "$tmp"
[ -f "$tmp/assets/public/index.html" ] || { echo "no assets/public/index.html in $APK" >&2; exit 1; }
rm -rf www && mv "$tmp/assets/public" www
echo "www/ <- $APK ($(find www -type f | wc -l) files)"
