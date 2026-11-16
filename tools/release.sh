#!/bin/bash
# Build and publish an update to GitHub Releases. The head unit picks it up automatically.
#   tools/release.sh "what changed"
# Screen-only change: bump android/app/src/main/assets/www/version.txt
# App change:         also bump versionCode / versionName in android/app/build.gradle
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/tools/publish.sh" "${1:-}"
VN=$(grep -oE "versionName '[^']+'" "$ROOT/android/app/build.gradle" | sed -E "s/versionName '(.*)'/\1/")
WV=$(tr -d '[:space:]' < "$ROOT/android/app/src/main/assets/www/version.txt")
TAG="v${VN}-screens${WV}"
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists: bump www/version.txt (screens) or versionName (app) first." >&2; exit 1
fi
gh release create "$TAG" "$ROOT/dist/update/update.json" "$ROOT/dist/update/PoloLauncher.apk" "$ROOT/dist/update/web.zip" \
  --title "Polo Launcher $VN (screens v$WV)" --notes "${1:-Update}" --latest
echo "Released $TAG"
