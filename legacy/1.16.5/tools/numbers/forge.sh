#!/usr/bin/env bash
# Forge 1.16.5 的开发期数值断言。
#
# 跑的是开发期 runServer（不是 build/libs 里的产物 jar）：自检类被两个加载器的构建都排除在产物外，
# 只有开发期才存在。走的是生产路径本身——PlayerEntity#hurt → LivingEntity#hurt →
# PlayerEntity#actuallyHurt → LivingHurtEvent，因此这里测到的数值就是玩家真会被扣的血量。
#
# 用法：bash legacy/1.16.5/tools/numbers/forge.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

PROJ="$(cd "$HERE/../.." && pwd)"      # legacy/1.16.5
WORKDIR="$PROJ/forge"

# ForgeGradle 4.1 只支持 Gradle 6.x，而 Gradle 6.9.4 跑不了 JDK 17：Gradle JVM 必须是 JDK 8
export JAVA_HOME="$JDK8"
[ -x "$JAVA_HOME/bin/java" ] || die "找不到 JDK 8：$JAVA_HOME"

mkdir -p "$WORKDIR/run"
# 开发期服务端同样要 eula；自制一份，免得每次都被 EULA 卡住
printf 'eula=true\n' >"$WORKDIR/run/eula.txt"

# 锁与 pid 都放在 run/ 下：那是 gitignore 过的开发期目录，脚本被意外打断也不会弄脏工作区
acquire_lock "$WORKDIR/run"

run_numbers "$PROJ/numbers-forge.log" "$WORKDIR" "Forge 1.16.5" -- \
    ./gradlew -Drealdamage.selftest=1 runServer --console=plain
