#!/usr/bin/env bash
# Run only after the device owner authorizes phone operations again.
# DEV=<authorized adb serial> bash tools/ui-verify/restore_ui_0253_display.sh
set -euo pipefail
: "${DEV:?Set DEV to the authorized phone adb serial}"
adb -s "$DEV" shell wm size reset
adb -s "$DEV" shell wm density reset
adb -s "$DEV" shell settings put system font_scale 0.9
adb -s "$DEV" shell cmd uimode night auto
printf '%s\n' 'Restored original physical size/density, font scale 0.9 and night auto.'
