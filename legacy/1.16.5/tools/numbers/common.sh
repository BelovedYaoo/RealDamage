#!/usr/bin/env bash
# 开发期数值断言的公共部分。
#
# 这一层和 tools/smoke 的区别：smoke 验的是 build/libs 里的产物能不能起来、依赖从哪来、
# 命令有没有注册；这里验的是「伤害算出来到底掉多少血」。断言本身写在
# common/src/devtest 的 RealDamageSelfTest 里（两个加载器共用一份期望值），
# 本脚本只负责：起开发期服务端 → 等它自己跑完并停服 → 从日志里取数核对。
#
# 三条铁律（这个仓库已经踩过的坑）：
#   1. 服务端一律 nohup 起、轮询有上限，绝不在前台跑（前台永不退出会卡死调用方）；
#   2. 停服不用 pkill -f（Windows 上会留下占着端口的僵尸进程），一律「netstat 取 PID →
#      Stop-Process」，并确认端口真的释放了再起下一个；
#   3. 服务端 stdout 里的中文编码不稳定（Forge 侧实测 GBK，Fabric 侧实测 UTF-8），
#      必须先探测再转，否则中文 grep 会连续假阴性。
#
# 注意：自检结束时服务端是自己 halt 掉的，Forge 侧这会让 Gradle 6 的 daemon 连接断掉，
# 于是 gradlew 退出码非 0（"Could not receive a message from the daemon"）。那是收尾噪音，
# 不是断言失败。所以本脚本不看 gradlew 的退出码，只认日志里的正向证据
# （RESULT PASSED 与每条 [SELFTEST-CHECK] 的 ok=true）。
set -uo pipefail

export HTTPS_PROXY="${HTTPS_PROXY:-http://127.0.0.1:7890}"
export HTTP_PROXY="$HTTPS_PROXY"

# Gradle 6.9.4（Forge 侧 ForgeGradle 4.1 的上限）跑不了 JDK 17；Loom 0.11 跑不了 JDK 8。
# 两边因此各用各的 Gradle JVM，被测产物本身两边都是 Java 8 字节码。
JDK8="${JDK8:-/c/Program Files/Eclipse Adoptium/jdk-8.0.502.7-hotspot}"
JDK17="${JDK17:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot}"

PORT="${PORT:-25565}"

die() { echo "[FAIL] $*" >&2; exit 1; }
info() { echo "[info] $*"; }

# 服务端 stdout 的编码不固定：整份文件能按 UTF-8 解码就直接用，否则按 GBK 转一次
log_text() {
    local log="$1"
    if iconv -f UTF-8 -t UTF-8 "$log" >/dev/null 2>&1; then
        cat "$log"
    else
        iconv -f GBK -t UTF-8 "$log" 2>/dev/null
    fi
}

grep_log() {
    local log="$1" pattern="$2"
    log_text "$log" | sed 's/\r$//' | grep -E "$pattern"
}

# 等日志里出现某个正向标记（服务端启动完成 / 自检结果），最多等 $3 秒
wait_for_pattern() {
    local log="$1" pattern="$2" timeout="${3:-300}"
    local deadline=$((SECONDS + timeout))
    while [ $SECONDS -lt $deadline ]; do
        if [ -f "$log" ] && grep_log "$log" "$pattern" >/dev/null 2>&1; then
            return 0
        fi
        sleep 3
    done
    return 1
}

# 按端口找 PID 并强杀，然后确认端口真的没了
stop_server_by_port() {
    local port="$1" pid pids
    pids="$(netstat -ano | grep -E "[:.]${port}[[:space:]]" | grep -i LISTENING | awk '{print $NF}' | sort -u)"
    if [ -z "$pids" ]; then
        info "端口 $port 上没有监听进程"
        return 0
    fi
    for pid in $pids; do
        info "停止 PID $pid（端口 $port）"
        powershell -NoProfile -Command "Stop-Process -Id $pid -Force" >/dev/null 2>&1 || true
    done
    local deadline=$((SECONDS + 30))
    while [ $SECONDS -lt $deadline ]; do
        if ! netstat -ano | grep -E "[:.]${port}[[:space:]]" | grep -qi LISTENING; then
            info "端口 $port 已释放"
            return 0
        fi
        sleep 1
    done
    die "端口 $port 仍被占用，不能继续"
}

# 同一个工作目录不允许并发跑两份：两份会抢同一个端口与同一个 run/ 目录
NUMBERS_LOCK=""

acquire_lock() {
    local dir="$1" lock="$1/.numbers.lock"
    NUMBERS_LOCK="$lock"
    if [ -f "$lock" ]; then
        local pid
        pid="$(cat "$lock" 2>/dev/null)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            die "另一个数值断言进程（PID $pid）正在使用 $dir，请等它结束"
        fi
        info "清理残留锁（PID $pid 已不在）"
        rm -f "$lock"
    fi
    echo $$ >"$lock"
    trap 'rm -f "$NUMBERS_LOCK"' EXIT
}

# 核对一条断言。
#
# 取数只认 ASCII 的 [SELFTEST-CHECK] 行（key/expected/actual/ok），不认中文描述：
# 中文要在 GBK 与 UTF-8 之间往返，一旦转错就是假阴性，而这条正是要防「以为跑了其实没验」。
assert_check() {
    local log="$1" key="$2" expected="$3"
    local line
    # 注意 || true 必须写在 $( ) 里面：写在外面会被当成字符串的一部分劫进变量（实测过）
    line="$(grep_log "$log" "\[SELFTEST-CHECK\] key=${key} " || true)"
    if [ -z "$line" ]; then
        echo "[FAIL] 日志里没有 key=$key 的断言记录"
        return 1
    fi
    if ! echo "$line" | grep -q "expected=${expected} actual=${expected} ok=true"; then
        echo "[FAIL] key=$key 期望 $expected，实际记录为：$line"
        return 1
    fi
    echo "  [ok] $key = $expected（$line）"
    return 0
}

# 起服务端、等自检跑完、核对全部断言。参数：日志路径 + 启动命令（数组）
# 用法：run_numbers <log> <workdir> <loader 名> -- <启动命令...>
run_numbers() {
    local log="$1" workdir="$2" name="$3"
    shift 3
    [ "${1:-}" = "--" ] && shift

    stop_server_by_port "$PORT"
    rm -f "$log"
    info "启动 $name 开发期服务端（自检跑完会自己停服）"
    # nohup + 后台：前台跑永不退出，会把这个调用卡死
    ( cd "$workdir" && nohup "$@" >"$log" 2>&1 & echo $! >"$workdir/run/.numbers.pid" )
    local pid
    pid="$(cat "$workdir/run/.numbers.pid" 2>/dev/null || true)"

    if ! wait_for_pattern "$log" "SELFTEST\] RESULT" 420; then
        grep_log "$log" "ERROR|Exception|Caused by|FAILED" | head -20 || true
        stop_server_by_port "$PORT"
        die "$name：420 秒内没有看到自检结果（RESULT）"
    fi

    # 自检结束后服务端会 halt；给收尾留一点时间，再按端口兜底清一次
    local deadline=$((SECONDS + 60))
    while [ $SECONDS -lt $deadline ]; do
        if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
            break
        fi
        sleep 2
    done
    stop_server_by_port "$PORT"
    rm -f "$workdir/run/.numbers.pid"

    echo "---- $name 数值断言 ----"
    grep_log "$log" "SELFTEST\]" || true
    echo "---- 核对 ----"
    local failed=0
    # 三条任务要求的数值 + 创造/旁观模式那条历史缺陷回归点 + 保护期两条（同一套上下文的对照）
    assert_check "$log" bypass_armor 10.0 || failed=1
    assert_check "$log" vanilla_armor_baseline 3.0 || failed=1
    assert_check "$log" invulnerable_entity 0.0 || failed=1
    assert_check "$log" creative_abilities 0.0 || failed=1
    assert_check "$log" protection_period 3.0 || failed=1
    assert_check "$log" after_protection 10.0 || failed=1
    if grep_log "$log" "SELFTEST\] RESULT PASSED" >/dev/null 2>&1; then
        echo "  [ok] 自检总结果 PASSED"
    else
        echo "[FAIL] 自检总结果不是 PASSED"
        failed=1
    fi
    echo "日志：$log"
    [ "$failed" -eq 0 ] || die "$name 数值断言失败"
    echo "[PASS] $name 数值断言全部通过"
}
