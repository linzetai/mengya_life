#!/usr/bin/env bash
# Gate 2C 真机验收：一条命令跑完 QA 包（隔离数据）+ 正式包（只读冒烟）。
#
# 用法：
#   export DEV=192.168.x.x:<无线调试端口>
#   ./tools/ui-verify/run_all.sh
#
# 前置：
#   - 手机已开启无线调试，adb devices 状态为 device；
#   - QA 包（com.mengyalife.app.qa）与正式包（com.mengyalife.app）都已安装；
#   - 正式包里保留本轮验收用的旧数据（2 条 v1 迁移日记）。
#
# 安全边界：
#   - QA 脚本会 `pm clear com.mengyalife.app.qa`，它是独立 applicationId + 独立数据库；
#   - 正式包脚本只读，不清数据、不新建日记/目标、不切主题（导出只写外部文件）。
set -uo pipefail

cd "$(dirname "$0")/../.."

if [ -z "${DEV:-}" ]; then
  echo "错误：请先 export DEV=<手机IP>:<无线调试端口>" >&2
  exit 1
fi

QA_PKG="com.mengyalife.app.qa"
PROD_PKG="com.mengyalife.app"
export SHOT_DIR="${SHOT_DIR:-docs/reports/logs/g2c-shots}"
export EXPORT_OUT="${EXPORT_OUT:-docs/reports/logs/g2c-export}"
LOG_DIR="docs/reports/logs"
mkdir -p "$SHOT_DIR" "$EXPORT_OUT" "$LOG_DIR"

echo "== 设备 =="
adb -s "$DEV" devices | tail -2
echo "== 已安装 =="
adb -s "$DEV" shell pm list packages | grep -E "com.mengyalife.app(.qa)?$" || true

if ! adb -s "$DEV" shell pm list packages | grep -qx "package:$QA_PKG"; then
  echo "错误：未安装 QA 包 $QA_PKG（./gradlew :app:assembleQa 后 adb install）" >&2
  exit 1
fi

adb -s "$DEV" shell input keyevent KEYCODE_WAKEUP || true
adb -s "$DEV" shell wm dismiss-keyguard || true
adb -s "$DEV" shell logcat -b crash -c || true

fail=0
run() {
  local pkg="$1" script="$2"
  echo
  echo "======== [$pkg] $script ========"
  if MENGYA_PKG="$pkg" python3 "tools/ui-verify/${script}.py" 2>&1 \
      | tee -a "$LOG_DIR/g2c-${script}.log" | tail -60; then
    echo "[$script] OK"
  else
    echo "[$script] 有断言失败（见 $LOG_DIR/g2c-${script}.log）" >&2
    fail=1
  fi
}

# ---- QA：会写/清 QA 自己的数据 ----
run "$QA_PKG" verify_settings
run "$QA_PKG" verify_diary
run "$QA_PKG" verify_theme
run "$QA_PKG" verify_navigation
run "$QA_PKG" verify_goals
run "$QA_PKG" verify_trends
run "$QA_PKG" verify_dates
run "$QA_PKG" verify_export

# ---- 正式包：只读冒烟（不 clear、不新建） ----
run "$PROD_PKG" verify_prod_smoke

echo
echo "截图目录：$SHOT_DIR"
echo "导出样本：$EXPORT_OUT"
exit $fail
