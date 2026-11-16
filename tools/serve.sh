#!/bin/bash
# Share updates from this Mac over Wi-Fi. Put the head unit on the same Wi-Fi (or your phone's hotspot
# with the Mac on it too), then in Polo Launcher: Launcher > Updates > Source, enter the address printed below.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
echo "Update source for the head unit:  http://${IP:-<this-Mac-IP>}:8765/"
echo "(leave this window open while the unit updates; Ctrl+C to stop)"
cd "$ROOT/dist/update" && python3 -m http.server 8765
