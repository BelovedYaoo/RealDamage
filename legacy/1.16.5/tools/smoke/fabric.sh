#!/usr/bin/env bash
# Fabric 1.16.5 正式产物冒烟：真实专用服务端 + Fabric 官方 server launcher + JDK 8。
#
# 验证的是 build/libs 里那份 jar：
#   1. 服务端能起来（日志出现 "Done ("）；
#   2. big-math 与 nightconfig 从产物自带的 jar-in-jar（META-INF/jars）里加载；
#   3. 没有 NoClassDefFoundError / ClassNotFoundException / Mixin 报错。
#
# 用法：bash legacy/1.16.5/tools/smoke/fabric.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

MC_VERSION="1.16.5"
LOADER_VERSION="${LOADER_VERSION:-0.14.24}"
INSTALLER_VERSION="${INSTALLER_VERSION:-1.1.2}"
FABRIC_API_VERSION="0.42.0+1.16"
PORT="${PORT:-25565}"
SERVER_DIR="$SMOKE_ROOT/fabric-server"
MOD_JAR="$(cd "$HERE/../.." && pwd)/fabric/build/libs/realdamage-1.16.5-fabric-1.0.0.jar"
LAUNCHER="$SERVER_DIR/fabric-server-launch.jar"
API_JAR="$SMOKE_ROOT/fabric-api-${FABRIC_API_VERSION}.jar"
LOG="$SERVER_DIR/server-run.log"

require_files "$MOD_JAR"

mkdir -p "$SMOKE_ROOT" "$SERVER_DIR"
cd "$SERVER_DIR" || die "进不去 $SERVER_DIR"
acquire_lock "$SERVER_DIR"

# 1. Fabric 官方 server launcher（内嵌 loader，启动时自己拉 vanilla server jar）
if [ ! -f "$LAUNCHER" ]; then
    info "下载 Fabric server launcher"
    curl -sL --max-time 300 -o "$LAUNCHER" \
        "https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" \
        || die "launcher 下载失败"
fi
if [ ! -f "$API_JAR" ]; then
    # 注意：maven.fabricmc.net 上同名的 fabric-api 构件只是个 2KB 的「聚合模块」，
    # 里面只有 fabric.mod.json，真正的模块都在它的 POM 依赖里——放进 mods/ 后
    # Fabric Loader 不会去网上拉依赖，于是运行时会
    # ClassNotFoundException: net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback。
    # 玩家实际安装的是官方发布的 bundle（各模块以 jar-in-jar 内嵌），这里取的就是那一份。
    info "下载 Fabric API（官方 bundle）"
    API_URL_VERSION="${FABRIC_API_VERSION/+/%2B}"
    curl -sL --max-time 300 -o "$API_JAR" \
        "https://cdn.modrinth.com/data/P7dR8mSH/versions/${API_URL_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" \
        || die "Fabric API 下载失败"
fi
# 防呆：聚合模块只有 2KB，真 bundle 里嵌了四十多个模块
[ "$(stat -c %s "$API_JAR")" -gt 100000 ] || die "Fabric API 不是官方 bundle（只拿到聚合模块？）：$API_JAR"

# 2. 装 mod 与最小必要配置
rm -rf mods && mkdir -p mods
cp "$MOD_JAR" mods/
cp "$API_JAR" mods/
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
info "启动 Fabric 服务端"
rm -f "$LOG"
nohup "$JAVA8" -Xmx2G "${PROXY_JVM[@]}" -jar "$LAUNCHER" nogui >"$LOG" 2>&1 &
if ! wait_for_done "$LOG" 480; then
    grep_log "$LOG" "ERROR|Exception|Caused by" | head -20
    stop_server_by_port "$PORT"
    die "服务端 480 秒内没有出现 Done ("
fi
info "服务端已启动（Done ( 已出现）"
sleep 5

# 4. 取证
FAILED=0
grep_log "$LOG" "Done \(" | head -1 || true
echo "---- 第三方库来源 ----"
grep_log "$LOG" "RealDamage 运行时依赖" || { echo "[FAIL] 没有第三方库自检日志"; FAILED=1; }
# Fabric 会把 jar-in-jar 的嵌套 jar 解到 .fabric/processedMods/ 下，文件名带内容哈希
for lib in "big-math-2.3.2" "night-config_core-3.6.3" "night-config_toml-3.6.3"; do
    if grep_log "$LOG" "运行时依赖" | grep -q "$lib"; then
        echo "[ok] $lib 已加载"
    else
        echo "[FAIL] $lib 未加载"
        FAILED=1
    fi
done
echo "---- 崩溃类错误检查 ----"
if grep_log "$LOG" "NoClassDefFoundError|ClassNotFoundException|Loading errors|Failed to load mods|MixinApplyError|InvalidInjectionException"; then
    echo "[FAIL] 出现类加载 / Mixin / 模组加载错误（见上）"
    FAILED=1
else
    echo "[ok] 无 NoClassDefFoundError / ClassNotFoundException / Mixin 报错"
fi

# 5. 停服并确认端口释放
stop_server_by_port "$PORT"
echo "日志：$LOG"
if [ "$FAILED" -ne 0 ]; then die "Fabric 冒烟测试失败"; fi
echo "[PASS] Fabric 1.16.5 冒烟通过"
