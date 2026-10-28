#!/usr/bin/env bash
# 配置层 / 函数计算的自检脚本。
#
# 目的：配置层从 ForgeConfigSpec/ModConfigSpec 换成自研 nightconfig 实现后，
# 缺项补全、越界收敛、注释写回、坏文件不覆盖、函数参数退化这些行为都要有回归网，
# 而它们全都与 Minecraft、加载器无关，可以直接用 javac/java 跑，不必等服务器起来。
#
# 依赖的 jar 全部取自 Gradle 缓存（构建本身已经把对应版本下载过），不额外联网。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
OUT="$ROOT/build/config-selftest"

# JDK：用 21 编译（工程声明的构建 JVM），源码按 17 语义编译，便于 1.19.2/1.20.1 变体对照
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

# javac/java 是原生 Windows 程序：classpath 必须用 Windows 路径和分号分隔，
# 否则 git-bash 的 /e/... 形式会被当成无效路径
win() { cygpath -w "$1"; }

jar() {
    local found
    found="$(find "$CACHE/$1" -name "$2" 2>/dev/null | head -1)"
    if [ -z "$found" ]; then
        echo "找不到依赖 $1/$2，请先跑一次 gradle 构建以下载依赖" >&2
        exit 2
    fi
    win "$found"
}

# nightconfig 与 slf4j 的版本要和 Forge 1.20.1 内置的一致（core/toml 3.6.4），
# 这样自检覆盖的 API 面就是三个加载器运行时真正能用到的那一份
CP="$(jar com.electronwill.night-config/core 'core-3.6.4.jar');$(jar com.electronwill.night-config/toml 'toml-3.6.4.jar');$(jar org.slf4j/slf4j-api 'slf4j-api-1.7.36.jar');$(jar ch.obermuhlner/big-math 'big-math-2.3.2.jar')"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# 只编译与加载器无关的两层：config/*.java 与 util/FunctionCalculationUtil.java
"$JAVAC" -J-Dfile.encoding=UTF-8 -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8 -encoding UTF-8 -source 17 -target 17 -nowarn \
    -cp "$CP" -d "$(win "$OUT/classes")" \
    "$(win "$ROOT")"/src/main/java/top/bemylover/realdamage/config/*.java \
    "$(win "$ROOT")"/src/main/java/top/bemylover/realdamage/util/FunctionCalculationUtil.java \
    "$(win "$ROOT")"/tools/config-selftest/ConfigSelftest.java

# 解耦检查：算法层不得再直接读配置类（含 import 与 RealDamageConfig.xxx 成员访问），否则等于没解耦
if grep -qE 'import .*\.RealDamageConfig;|RealDamageConfig\.[A-Za-z_]' "$ROOT/src/main/java/top/bemylover/realdamage/util/FunctionCalculationUtil.java"; then
    echo "[FAIL] FunctionCalculationUtil 仍然引用 RealDamageConfig，未与配置解耦" >&2
    exit 1
fi
echo "  [ok] FunctionCalculationUtil 不再引用 RealDamageConfig"

"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$(win "$OUT/classes");$CP" ConfigSelftest
