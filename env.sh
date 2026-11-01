#!/usr/bin/env sh
# ---------------------------------------------------------------------------
# 项目级环境脚本（只影响当前 shell，不改动全局 ~/.bashrc）
#
# 用法：  . ./env.sh
#
# 约定：本文件不写任何设备专属绝对路径，全部由 $PREFIX 推导。
#       PREFIX 的取值见 README.md「环境配置」一节。
# ---------------------------------------------------------------------------

if [ -z "${PREFIX:-}" ]; then
    echo "[env] 错误：PREFIX 未设置。" >&2
    echo "[env] Termux 侧通常已预置；若在 proot 容器内为空，" >&2
    echo "[env] 请按 README.md「环境配置」一节先 export PREFIX 再 source 本脚本。" >&2
    return 1 2>/dev/null || exit 1
fi

# JDK：Termux 侧 OpenJDK 21
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-21-openjdk}"

# Android SDK：Termux 侧 $PREFIX/opt/android-sdk
export ANDROID_HOME="${ANDROID_HOME:-$PREFIX/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

export PATH="$JAVA_HOME/bin:$PREFIX/bin:$PATH"

# 复用 Termux 侧已有的 Gradle 缓存目录（其中已含 gradle-8.9 发行包与 AGP 8.7.3），
# 避免重复下载整套依赖。
# 推导方式：去掉 PREFIX 末尾的 /usr，再拼 /home/.gradle
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${PREFIX%/usr}/home/.gradle}"

# ARM64 适配：SDK build-tools 内的 aapt2 为 x86-64 ELF，aarch64 上会报
#   "Syntax error: \"(\" unexpected"
# 这里用系统属性注入 AGP 的 aapt2 覆盖项，指向 Termux 原生 aarch64 版 aapt2。
# 这样做的好处：设备路径只存在于 shell 环境，不落进仓库文件。
if [ -x "$PREFIX/bin/aapt2" ]; then
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.project.android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
else
    echo "[env] 警告：未找到 $PREFIX/bin/aapt2，ARM64 构建可能失败" >&2
fi

echo "[env] JAVA_HOME        = $JAVA_HOME"
echo "[env] ANDROID_HOME     = $ANDROID_HOME"
echo "[env] GRADLE_USER_HOME = $GRADLE_USER_HOME"
echo "[env] aapt2 override   = ${PREFIX}/bin/aapt2"
