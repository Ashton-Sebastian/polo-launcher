#!/bin/bash
# Builds the launcher and writes an update folder (dist/update) that the head unit can update from.
#   tools/publish.sh            -> app + screens
# Then serve dist/update somewhere the unit can reach (your Mac on the same Wi-Fi, or GitHub).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT/android"
gradle assembleDebug --console=plain -q 2>&1 | grep -v "^Note" || true
VC=$(grep -oE "versionCode [0-9]+" app/build.gradle | grep -oE "[0-9]+")
VN=$(grep -oE "versionName '[^']+'" app/build.gradle | sed -E "s/versionName '(.*)'/\1/")
WV=$(tr -d '[:space:]' < app/src/main/assets/www/version.txt)
OUT="$ROOT/dist/update"
mkdir -p "$OUT"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT/PoloLauncher.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$ROOT/dist/PoloLauncher.apk"
rm -f "$OUT/web.zip"
(cd app/src/main/assets/www && zip -qr "$OUT/web.zip" .)
NOTES="${1:-}"
cat > "$OUT/update.json" <<JSON
{
  "versionCode": $VC,
  "versionName": "$VN",
  "apk": "PoloLauncher.apk?v=$VC",
  "webVersion": $WV,
  "webMinApp": $VC,
  "web": "web.zip?v=$WV",
  "notes": "$NOTES"
}
JSON
echo "Published app $VN ($VC), screens v$WV to $OUT"
