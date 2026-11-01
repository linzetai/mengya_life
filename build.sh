#!/usr/bin/env sh
# ---------------------------------------------------------------------------
# 一键构建（等价于手工 source env.sh 后执行 ./gradlew）
#
# 用法：  ./build.sh                # 等价于 ./gradlew :app:assembleDebug --no-daemon
#         ./build.sh :app:assembleDebug --no-daemon --offline
# ---------------------------------------------------------------------------
set -eu

cd "$(dirname "$0")"

if [ -z "${PREFIX:-}" ]; then
    echo "错误：PREFIX 未设置，无法定位 Termux 工具链。" >&2
    exit 1
fi

. ./env.sh

exec ./gradlew "${@:-:app:assembleDebug --no-daemon}"
