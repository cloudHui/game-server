#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

PORT=18765
MAIN_CLASS="com.gamer.data.mpcserver.McpServerMain"
JAR_FILE="servertool/target/McpServer.jar"

ACTION="${1:-status}"

get_pid() {
    lsof -ti :${PORT} 2>/dev/null || ss -lptn "sport = :${PORT}" 2>/dev/null | grep -oP 'pid=\K\d+' || true
}

do_status() {
    PID=$(get_pid)
    if [ -n "${PID}" ]; then
        echo "[MCP] 服务正在运行，PID: ${PID}，端口: ${PORT}"
        echo -n "[MCP] 探活结果: "
        curl -s http://127.0.0.1:${PORT}/health || echo "探活失败"
        echo ""
    else
        echo "[MCP] 服务未运行 (端口 ${PORT} 空闲)。"
    fi
}

do_stop() {
    PID=$(get_pid)
    if [ -n "${PID}" ]; then
        echo "[MCP] 停止进程 PID: ${PID}..."
        kill "${PID}" 2>/dev/null || true
        sleep 1
        kill -9 "${PID}" 2>/dev/null || true
        echo "[MCP] 服务已停止。"
    else
        echo "[MCP] 服务未在运行。"
    fi
}

do_start() {
    PID=$(get_pid)
    if [ -n "${PID}" ]; then
        echo "[MCP] 端口 ${PORT} 已被 PID ${PID} 占用，服务已在运行中。"
        return 0
    fi

    echo "[MCP] 准备启动统一 MCP 服务 (端口: ${PORT})..."
    if [ -f "${JAR_FILE}" ]; then
        nohup java -Dfile.encoding=UTF-8 -jar "${JAR_FILE}" > /dev/null 2>&1 &
    else
        nohup java -Dfile.encoding=UTF-8 -cp "servertool/target/classes:servertool/lib/*" "${MAIN_CLASS}" > /dev/null 2>&1 &
    fi

    sleep 2
    do_status
}

case "${ACTION}" in
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    restart)
        do_stop
        sleep 1
        do_start
        ;;
    status)
        do_status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
