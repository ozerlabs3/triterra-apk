#!/bin/sh
# Pulls the two things the wrapper takes verbatim from the published prod APK:
#  1. www/  = the embedded web bundle (assets/public, env already injected:
#     TRITERRA_ENV="prod", TRITERRA_WRAPPER="android").
#  2. the Firebase client values (project triterra-921bc) as a res file, because the
#     FirebaseAuthentication plugin crashes app launch when no FirebaseApp exists.
#     Read-only copy of public client identifiers: nothing in Firebase is changed.
# Neither output is committed (.gitignore): the source of truth is field-region-map / the APK.
set -eu
cd "$(dirname "$0")/.."
APK="${1:-../triterra-prod.apk}"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
AAPT2="$(ls "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"/build-tools/*/aapt2 | tail -1)"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
unzip -q "$APK" 'assets/public/*' -d "$tmp"
[ -f "$tmp/assets/public/index.html" ] || { echo "no assets/public/index.html in $APK" >&2; exit 1; }
rm -rf www && mv "$tmp/assets/public" www
echo "www/ <- $APK ($(find www -type f | wc -l) files)"

out=android/app/src/main/res/values/firebase-from-prod-apk.xml
if [ -f android/app/google-services.json ]; then  # owner registered the .auto app: its own config wins
  rm -f "$out"; echo "google-services.json present - prod Firebase values not copied"; exit 0
fi
"$AAPT2" dump resources "$APK" | awk '
  /string\/(google_app_id|google_api_key|default_web_client_id|gcm_defaultSenderId|project_id|google_storage_bucket|google_crash_reporting_api_key)$/ {
    n = $3; sub("string/", "", n); getline; v = $0; sub(/^[^"]*"/, "", v); sub(/"[^"]*$/, "", v)
    printf "    <string name=\"%s\" translatable=\"false\">%s</string>\n", n, v }' > "$tmp/fb"
grep -q 'name="google_app_id"' "$tmp/fb" || { echo "google_app_id not found in $APK" >&2; exit 1; }
{ echo '<?xml version="1.0" encoding="utf-8"?>'; echo '<resources>'; cat "$tmp/fb"; echo '</resources>'; } > "$out"
echo "$out <- $(grep -c '<string' "$out") Firebase client values ($(grep -o 'project_id[^<]*' "$out"))"
