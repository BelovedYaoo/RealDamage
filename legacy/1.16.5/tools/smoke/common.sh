#!/usr/bin/env bash
# 正式产物冒烟测试的公共部分。
#
# 三条铁律（这个仓库已经踩过的坑）：
#   1. 服务端一律 nohup 起、轮询有上限，绝不在前台跑（前台永不退出会卡死调用方）；
#   2. 停服不用 pkill -f（Windows 上会留下占着端口的僵尸进程），一律「netstat 取 PID →
#      Stop-Process」，并确认端口真的释放了再起下一个；
#   3. 服务端 stdout 里的中文是 GBK，UTF-8 的 grep 匹配不到中文，必须 iconv 转一次。
set -uo pipefail

export HTTPS_PROXY="${HTTPS_PROXY:-http://127.0.0.1:7890}"
export HTTP_PROXY="$HTTPS_PROXY"

# 产物按 Java 8 编译，冒烟也用 Java 8：这才是 1.16.5 的运行时环境
JDK8="${JDK8:-/c/Program Files/Eclipse Adoptium/jdk-8.0.502.7-hotspot}"
JAVA8="$JDK8/bin/java"

SMOKE_ROOT="${SMOKE_ROOT:-/e/Cache/rdtmp/realdamage-smoke}"
# JVM 不读 HTTP_PROXY，服务端自己下载 vanilla jar 时必须显式给代理
PROXY_JVM=(-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890
           -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890)

die() { echo "[FAIL] $*" >&2; exit 1; }
info() { echo "[info] $*"; }

require_files() {
    for f in "$@"; do
        [ -f "$f" ] || die "缺少文件：$f"
    done
}

# 等到日志里出现 "Done ("（服务端启动完成的标志），最多等 $2 秒
wait_for_done() {
    local log="$1" timeout="${2:-300}" deadline=$((SECONDS + ${2:-300}))
    while [ $SECONDS -lt $deadline ]; do
        if [ -f "$log" ] && grep -aq "Done (" "$log"; then
            return 0
        fi
        sleep 3
    done
    return 1
}

# 按端口找 PID 并强杀，然后确认端口真的没了
stop_server_by_port() {
    local port="$1" pid
    local pids
    pids="$(netstat -ano | grep -E "[:.]${port}[[:space:]]" | grep -i LISTENING | awk '{print $NF}' | sort -u)"
    if [ -z "$pids" ]; then
        info "端口 $port 上没有监听进程"
        return 0
    fi
    for pid in $pids; do
        info "停止 PID $pid（端口 $port）"
        powershell -NoProfile -Command "Stop-Process -Id $pid -Force" >/dev/null 2>&1 || true
    done
    # 最多等 30 秒，确认端口释放
    local deadline=$((SECONDS + 30))
    while [ $SECONDS -lt $deadline ]; do
        if ! netstat -ano | grep -E "[:.]${port}[[:space:]]" | grep -qi LISTENING; then
            info "端口 $port 已释放"
            return 0
        fi
        sleep 1
    done
    die "端口 $port 仍被占用，不能继续（否则下一个服务端会 BindException）"
}

# 把服务端 stdout 日志转成 UTF-8 文本。
#
# 两个加载器把中文写进 stdout 的编码并不一致：Forge 侧实测是 GBK（Windows 平台编码），
# Fabric 侧实测是 UTF-8。固定用 iconv -f GBK 会把后者转成乱码，于是中文关键词永远匹配不到
# （这个仓库历史上就因为这一类编码错配连续出现过三次假阴性）。所以先探测：整份文件能按
# UTF-8 解码就直接用，否则再按 GBK 转一次。
log_text() {
    local log="$1"
    if iconv -f UTF-8 -t UTF-8 "$log" >/dev/null 2>&1; then
        cat "$log"
    else
        iconv -f GBK -t UTF-8 "$log" 2>/dev/null
    fi
}

# 从服务端日志里按关键词取证
grep_log() {
    local log="$1" pattern="$2"
    log_text "$log" | grep -E "$pattern"
}

# 同一个工作目录不允许并发跑两份冒烟：两个进程会抢同一个日志与同一个端口，
# 先结束的那份会把后一份的服务端当自己的僵尸进程 kill 掉（实测踩过）
SMOKE_LOCK=""   # 必须在全局作用域，trap 在函数返回之后才执行

acquire_lock() {
    local dir="$1" lock="$1/.smoke.lock"
    SMOKE_LOCK="$lock"
    if [ -f "$lock" ]; then
        local pid
        pid="$(cat "$lock" 2>/dev/null)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            die "另一个冒烟进程（PID $pid）正在使用 $dir，请等它结束"
        fi
        info "清理残留锁（PID $pid 已不在）"
        rm -f "$lock"
    fi
    echo $$ >"$lock"
    trap 'rm -f "$SMOKE_LOCK"' EXIT
}
