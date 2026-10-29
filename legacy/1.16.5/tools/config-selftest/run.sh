#!/usr/bin/env bash
# 配置层 / 函数计算的离线自检（1.16.5 分支版）。
#
# 为什么要单独做这个：配置层是从 main 分支搬过来的自研 nightconfig 实现，换到 1.16.5 后有两处
# 必须重新验证——
#   1. 代码改成了 Java 8 兼容（record → 普通类、List.of → Arrays.asList、String#stripLeading
#      → 手写）；这些问题在 JDK 17/21 上完全看不出来，只有真的在 JDK 8 上编译并运行才作数；
#   2. 依赖版本换成了 1.16.5 运行时真正自带的那一份（nightconfig 3.6.3，main 上是 3.6.4）。
#
# 因此这里 javac/java 都用 JDK 8、源/目标兼容级 8，跑的就是 1.16.5 的真实运行环境。
# 依赖 jar 全部取自 Gradle 缓存（构建本身已经下载过），不额外联网。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"     # legacy/1.16.5
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
OUT="$ROOT/build/config-selftest"
SRC="$ROOT/common/src/main/java/top/bemylover/realdamage"
SELFTEST="$ROOT/tools/config-selftest/ConfigSelftest.java"

# MC 1.16.5 的运行时是 Java 8：自检也必须在 Java 8 上跑，否则「能在 Java 8 上跑」这句话没有证据
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-8.0.502.7-hotspot}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
JAVAP="$JAVA_HOME/bin/javap"
[ -x "$JAVAC" ] || { echo "找不到 JDK 8 的 javac：$JAVAC" >&2; exit 2; }

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

# 依赖版本与 1.16.5 运行时对齐：
#   nightconfig core/toml 3.6.3 —— Forge 1.16.5 自带的那一份（Fabric 侧产物里打包的也是它）
#   big-math 2.3.2            —— 随产物分发的那一份
#   log4j 2.8.1               —— MC 1.16.5 自带的日志实现，只为让日志真能打出来
CP="$(jar com.electronwill.night-config/core 'core-3.6.3.jar')"
CP="$CP;$(jar com.electronwill.night-config/toml 'toml-3.6.3.jar')"
CP="$CP;$(jar ch.obermuhlner/big-math 'big-math-2.3.2.jar')"
CP="$CP;$(jar org.apache.logging.log4j/log4j-api 'log4j-api-2.8.1.jar')"
CP="$CP;$(jar org.apache.logging.log4j/log4j-core 'log4j-core-2.8.1.jar')"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# 只编译与加载器无关的两层：config/*.java 与 util/FunctionCalculationUtil.java
# （handler/mixin 依赖 MC 类型，不在这条链上）
"$JAVAC" -J-Dfile.encoding=UTF-8 -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8 \
    -encoding UTF-8 -source 8 -target 8 -nowarn \
    -cp "$CP" -d "$(win "$OUT/classes")" \
    "$(win "$SRC")"/config/*.java \
    "$(win "$SRC")"/util/FunctionCalculationUtil.java \
    "$(win "$SELFTEST")"

# 解耦检查：算法层不得再直接读配置类（含 import 与 RealDamageConfig.xxx 成员访问），否则等于没解耦
if grep -qE 'import .*\.RealDamageConfig;|RealDamageConfig\.[A-Za-z_]' "$SRC/util/FunctionCalculationUtil.java"; then
    echo "[FAIL] FunctionCalculationUtil 仍然引用 RealDamageConfig，未与配置解耦" >&2
    exit 1
fi
echo "  [ok] FunctionCalculationUtil 不再引用 RealDamageConfig"

# 顺带确认产物字节码真的是 8（Java 8 运行时能加载）
CLASSVER="$("$JAVAP" -verbose -classpath "$(win "$OUT/classes")" \
    top.bemylover.realdamage.config.RealDamageConfig | grep -m1 "major version")"
case "$CLASSVER" in
    *"major version: 52"*) echo "  [ok] RealDamageConfig 字节码 $CLASSVER（52 = Java 8）" ;;
    *) echo "[FAIL] 字节码版本不是 52（Java 8）：$CLASSVER" >&2; exit 1 ;;
esac

"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -cp "$(win "$OUT/classes");$CP" ConfigSelftest
