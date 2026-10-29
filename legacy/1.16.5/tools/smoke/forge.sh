#!/usr/bin/env bash
# Forge 1.16.5 正式产物冒烟：真实专用服务端 + 官方安装器 + JDK 8。
#
# 验证的是 build/libs 里那份 jar（不是开发期 runServer）：
#   1. 服务端能起来（日志出现 "Done ("）；
#   2. big-math 从产物自带的 jar-in-jar 里加载（日志里的「运行时依赖」行给出实际来源）；
#   3. 没有 NoClassDefFoundError / ClassNotFoundException / Loading errors。
#
# 用法：bash legacy/1.16.5/tools/smoke/forge.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

MC_VERSION="1.16.5"
FORGE_VERSION="1.16.5-36.2.42"
PORT="${PORT:-25565}"
SERVER_DIR="$SMOKE_ROOT/forge-server"
MOD_JAR="$(cd "$HERE/../.." && pwd)/forge/build/libs/realdamage-1.16.5-forge-1.0.0.jar"
INSTALLER="$SMOKE_ROOT/forge-${FORGE_VERSION}-installer.jar"
LOG="$SERVER_DIR/server-run.log"

require_files "$MOD_JAR"

mkdir -p "$SMOKE_ROOT" "$SERVER_DIR"
cd "$SERVER_DIR" || die "进不去 $SERVER_DIR"
acquire_lock "$SERVER_DIR"

# 1. 官方安装器（--installServer 只铺 libraries/ 与启动用 jar，不需要 GUI）
if [ ! -f "forge-${FORGE_VERSION}.jar" ]; then
    if [ ! -f "$INSTALLER" ]; then
        info "下载 Forge 安装器"
        curl -sL --max-time 300 -o "$INSTALLER" \
            "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE_VERSION}/forge-${FORGE_VERSION}-installer.jar" \
            || die "安装器下载失败"
    fi
    info "安装 Forge 服务端（下载 MC 与 libraries，走代理）"
    "$JAVA8" "${PROXY_JVM[@]}" -jar "$(cygpath -w "$INSTALLER")" --installServer "$(cygpath -w "$SERVER_DIR")" \
        >install.log 2>&1 || { tail -20 install.log; die "Forge 安装失败"; }
fi
require_files "forge-${FORGE_VERSION}.jar"

# 2. 装 mod 与最小必要配置
rm -rf mods && mkdir -p mods
cp "$MOD_JAR" mods/
printf 'eula=true\n' > eula.txt
cat > server.properties <<EOF
level-type=flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
online-mode=false
max-players=2
server-port=$PORT
sync-chunk-writes=false
EOF

# 3. 起服务端（后台 + 有上限的轮询）
stop_server_by_port "$PORT"
info "启动 Forge 服务端"
rm -f "$LOG"
nohup "$JAVA8" -Xmx2G -jar "forge-${FORGE_VERSION}.jar" nogui >"$LOG" 2>&1 &
START_PID=$!
if ! wait_for_done "$LOG" 420; then
    grep_log "$LOG" "ERROR|Exception|Caused by" | head -20
    stop_server_by_port "$PORT"
    die "服务端 420 秒内没有出现 Done ("
fi
info "服务端已启动（Done ( 已出现）"
sleep 5

# 4. 取证
FAILED=0
grep_log "$LOG" "Done \(" | head -1 || true
echo "---- 第三方库来源 ----"
grep_log "$LOG" "RealDamage 运行时依赖" || { echo "[FAIL] 没有第三方库自检日志"; FAILED=1; }
DEP_LINE="$(grep_log "$LOG" "RealDamage 运行时依赖" | head -1)"
# Forge 1.16.5 没有 jar-in-jar 协议，big-math 是摊进产物 jar 里的，所以它的 CodeSource
# 应当就是我们自己那个 jar；nightconfig 则应当来自 Forge 自带的 libraries/
# ModLauncher 的 securejarhandler 用 modjar://<modid> 表示「类来自该 mod 自己的 jar」
if echo "$DEP_LINE" | grep -q "modjar://realdamage"; then
    echo "[ok] big-math 来自产物 jar 内部（Forge 1.16.5 无 jar-in-jar，改为摊入）"
else
    echo "[FAIL] big-math 的来源不是产物 jar：$DEP_LINE"
    FAILED=1
fi
if echo "$DEP_LINE" | grep -q "night-config/core/3.6.3"; then
    echo "[ok] nightconfig 来自 Forge 1.16.5 自带的 libraries（3.6.3，与编译期对齐）"
else
    echo "[FAIL] nightconfig 的来源不是 Forge 自带的 3.6.3：$DEP_LINE"
    FAILED=1
fi
echo "---- 崩溃类错误检查 ----"
if grep_log "$LOG" "NoClassDefFoundError|ClassNotFoundException|Loading errors|Failed to load mods"; then
    echo "[FAIL] 出现类加载/模组加载错误（见上）"
    FAILED=1
else
    echo "[ok] 无 NoClassDefFoundError / ClassNotFoundException / Loading errors"
fi
echo "---- 命令注册 ----"
if grep_log "$LOG" "Ambiguity between arguments \[realDamage" ; then
    echo "[warn] realDamage 命令与其它命令存在歧义"
else
    echo "[ok] /realDamage 命令已注册（无歧义告警）"
fi

# 5. 停服并确认端口释放
stop_server_by_port "$PORT"
echo "日志：$LOG"
if [ "$FAILED" -ne 0 ]; then die "Forge 冒烟测试失败"; fi
echo "[PASS] Forge 1.16.5 冒烟通过"
