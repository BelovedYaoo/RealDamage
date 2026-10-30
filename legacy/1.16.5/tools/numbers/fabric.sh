#!/usr/bin/env bash
# Fabric 1.16.5 的开发期数值断言。
#
# 跑的是开发期 runServer：自检类只在开发期存在（两个加载器的产物都把它排除了）。
# Forge 侧那条链路是事件，这一侧是 mixin，所以同一个数值在两边都测一遍，
# 才算证明了「两个加载器数值一致」。
#
# 默认同时打开 mixin 导出（-Drealdamage.mixinExport=1 → JVM 里的 mixin.debug.export=true），
# 注入后的类会落在 fabric/run/.mixin.out/class/，用来做字节码级核对（见 README.md）。
# 想跳过导出：MIXIN_EXPORT=0 bash .../fabric.sh
#
# 用法：bash legacy/1.16.5/tools/numbers/fabric.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

PROJ="$(cd "$HERE/../.." && pwd)"      # legacy/1.16.5
WORKDIR="$PROJ/fabric"

# Loom 0.11 走 Gradle 7.x，用 JDK 17 跑 Gradle；产物本身仍是 Java 8 字节码
export JAVA_HOME="$JDK17"
[ -x "$JAVA_HOME/bin/java" ] || die "找不到 JDK 17：$JAVA_HOME"

mkdir -p "$WORKDIR/run"
printf 'eula=true\n' >"$WORKDIR/run/eula.txt"

# 锁与 pid 都放在 run/ 下：那是 gitignore 过的开发期目录，脚本被意外打断也不会弄脏工作区
acquire_lock "$WORKDIR/run"

GRADLE_ARGS=(-Drealdamage.selftest=1)
if [ "${MIXIN_EXPORT:-1}" = "1" ]; then
    GRADLE_ARGS+=(-Drealdamage.mixinExport=1)
fi

run_numbers "$PROJ/numbers-fabric.log" "$WORKDIR" "Fabric 1.16.5" -- \
    ./gradlew "${GRADLE_ARGS[@]}" runServer --console=plain

EXPORTED="$WORKDIR/run/.mixin.out/class/net/minecraft/world/entity/player/Player.class"
if [ "${MIXIN_EXPORT:-1}" = "1" ]; then
    if [ -f "$EXPORTED" ]; then
        echo "[ok] 注入后的 Player 已导出：$EXPORTED"
    else
        die "mixin 导出开关打开了，但没有找到 $EXPORTED"
    fi
fi
