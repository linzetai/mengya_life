#!/usr/bin/env bash
# 萌芽人生统一测试入口（Gate 3F）。
# 详细设计见 TEST-RUNNER-DESIGN.md；模块注册表见 tests/test-map.json。
set -uo pipefail
cd "$(dirname "$0")/.."

# 工具链：优先复用设备上已有的 Android SDK env；不把绝对路径写进仓库。
if [ -f /opt/android-sdk/env.sh ]; then
  # shellcheck disable=SC1091
  . /opt/android-sdk/env.sh >/dev/null 2>&1 || true
elif [ -n "${PREFIX:-}" ] && [ -f ./env.sh ]; then
  # shellcheck disable=SC1091
  . ./env.sh >/dev/null 2>&1 || true
fi

# 该环境的 $ANDROID_HOME/platform-tools/adb 是 x86 二进制，无法在 aarch64 上执行；
# 优先使用系统 adb（本机 /usr/bin/adb），否则设备永远“找不到”。
if [ -x /usr/bin/adb ]; then
  export ADB=/usr/bin/adb
  export PATH="/usr/bin:$PATH"
fi

exec python3 -m tests.verify.cli "$@"
