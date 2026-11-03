#!/usr/bin/env bash
# 真机 UI 回归：一条命令跑完日记 / 目标 / 主题 / 导航四套验收。
#
# 用法：
#   export DEV=192.168.x.x:<无线调试端口>
#   ./tools/ui-verify/run_all.sh
#
# 前置：
#   - 手机已开启「无线调试」，adb devices 能看到设备；
#   - 已安装被测 APK（app/build/outputs/apk/debug/app-debug.apk）；
#   - 手机保持亮屏，测试期间不要切到别的应用（脚本会尝试拉回前台）。
set -euo pipefail

cd "$(dirname "$0")/../.."

if [ -z "${DEV:-}" ]; then
  echo "错误：请先 export DEV=<手机IP>:<无线调试端口>" >&2
  exit 1
fi

export SHOT_DIR="${SHOT_DIR:-docs/reports/logs/verify-shots}"
mkdir -p "$SHOT_DIR" docs/reports/logs

echo "== 设备 =="
adb -s "$DEV" devices | tail -2

echo "== 放手机关屏 =="
adb -s "$DEV" shell input keyevent KEYCODE_WAKEUP || true
adb -s "$DEV" shell wm dismiss-keyguard || true

fail=0
for script in verify_goals verify_diary verify_theme verify_navigation; do
  echo
  echo "======== $script ========"
  if python3 "tools/ui-verify/${script}.py" 2>&1 | tee -a "docs/reports/logs/ui-verify-${script}.log" | tail -40; then
    echo "[$script] OK"
  else
    echo "[$script] 有断言失败（见日志）" >&2
    fail=1
  fi
done

echo
echo "截图目录：$SHOT_DIR"
exit $fail
