#!/bin/sh
# 按文件名日期选择日志；无条件直接复制，有条件才用 rg/grep 筛行。
set -eu
export LC_ALL=C

fail() { printf 'copylog: %s\n' "$*" >&2; exit 1; }
usage() {
    printf '%s\n' \
        '用法: sh copylog.sh [--from yyyy-MM-dd] [--to yyyy-MM-dd]' \
        '                   [--condition 关键字或ID] [--all] [--compress]' \
        '默认 game、昨天和今天的日志文件；--all 搜索三服；--compress 压缩为 tar.gz。'
}

from= to= condition= all=0 compress=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --from|--to|--condition)
            [ "$#" -ge 2 ] || fail "$1 缺少参数"
            case "$1" in --from) from=$2;; --to) to=$2;; --condition) condition=$2;; esac
            shift 2 ;;
        --from=*) from=${1#*=}; shift ;;
        --to=*) to=${1#*=}; shift ;;
        --condition=*) condition=${1#*=}; shift ;;
        --all) all=1; shift ;;
        --compress) compress=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail "未知参数: $1" ;;
    esac
done

# 日期按服务器时区解释，只用于匹配文件名，不解析日志行里的时间。
check_day() {
    case "$1" in
        [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
        *) fail "日期必须是 yyyy-MM-dd: $1" ;;
    esac
    [ "$(date -d "$1" +%F 2>/dev/null)" = "$1" ] || fail "无效日期: $1"
}
[ -z "$from" ] || check_day "$from"
[ -z "$to" ] || check_day "$to"
today=$(date +%F)
to=${to:-$today}
from=${from:-$(date -d "$to -1 day" +%F)}
[ "$(date -d "$from" +%s)" -le "$(date -d "$to" +%s)" ] || fail "--from 晚于 --to"

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
sources=${COPYLOG_SOURCE:-/data/log/90001}
servers=${COPYLOG_SERVER_ROOT:-/data/server/90001}
[ ! -d "$sources" ] || sources=$(CDPATH= cd -- "$sources" && pwd -P)
[ ! -d "$servers" ] || servers=$(CDPATH= cd -- "$servers" && pwd -P)
output=${COPYLOG_OUTPUT:-$script_dir/log}
mkdir -p -- "$output"
output=$(CDPATH= cd -- "$output" && pwd -P)
case "$output/" in "$sources/"*) fail "输出目录不能位于源日志目录内" ;; esac

if command -v rg >/dev/null 2>&1; then
    search=rg
elif command -v grep >/dev/null 2>&1; then
    search=grep
else
    fail "未找到 rg 或 grep"
fi
case "$condition" in
    ''|*[!0-9]*) numeric=0 ;;
    *) numeric=1 ;;
esac
match_lines() {
    if [ "$numeric" -eq 1 ]; then
        pattern="(^|[^0-9])$condition([^0-9]|$)"
        if [ "$search" = rg ]; then
            rg --no-config --color never --no-heading --no-line-number --encoding none -a -e "$pattern" -- "$1"
        else
            grep -aUE -e "$pattern" -- "$1"
        fi
    elif [ "$search" = rg ]; then
        rg --no-config --color never --no-heading --no-line-number --encoding none -aF -e "$condition" -- "$1"
    else
        grep -aUF -e "$condition" -- "$1"
    fi
}

# 临时目录仅保存本次结果；源日志和历史导出均不清理。
work=$(mktemp -d "$output/.copylog-XXXXXXXXXXXX")
cleanup() {
    case "$work" in "$output"/.copylog-????????????) rm -rf -- "$work" ;; esac
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir "$work/result"
files=0

export_file() {
    source=$1
    relative=$2
    [ -f "$source" ] && [ ! -L "$source" ] && [ -s "$source" ] || return 0
    printf '[处理] %s\n' "$relative"
    content=$source
    zipped=0
    case "$source" in *.[gG][zZ]) zipped=1 ;; esac
    if [ -n "$condition" ]; then
        # 仅选中文件且需要筛行时取一次快照，避免日志轮转导致前后读取不同文件。
        if [ "$zipped" -eq 1 ]; then
            gzip -cd -- "$source" > "$work/raw" || fail "读取 gzip 日志失败: $source"
        else
            cp -- "$source" "$work/raw" || fail "读取日志失败: $source"
        fi
        status=0
        match_lines "$work/raw" > "$work/rows" || status=$?
        [ "$status" -le 1 ] || fail "$search 检索失败: $source"
        [ -s "$work/rows" ] || return 0
        # rg/grep 会补末尾 LF；原文件末行没有换行且也命中时还原。
        if [ "$(tail -c 1 "$work/raw" | wc -l)" -eq 0 ]; then
            { tail -n 1 "$work/raw"; printf '\n'; } > "$work/last"
            tail -n 1 "$work/rows" > "$work/last-hit"
            if cmp -s "$work/last" "$work/last-hit"; then
                head -c -1 "$work/rows" > "$work/trimmed"
                mv -- "$work/trimmed" "$work/rows"
            fi
        fi
        content="$work/rows"
    fi
    target="$work/result/$relative"
    mkdir -p -- "$(dirname -- "$target")"
    if [ "$zipped" -eq 1 ] && [ -n "$condition" ]; then
        gzip -n -c -- "$content" > "$target"
    else
        cp -- "$content" "$target"
    fi
    files=$((files + 1))
}

services=gameserver
[ "$all" -eq 0 ] || services="gameserver worldserver commonserver"
printf '文件日期: %s ~ %s；服务: %s\n' "$from" "$to" "$services"
for service in $services; do
    root="$sources/$service"
    if [ -d "$root" ]; then
        find "$root" -type f ! -name '$DocumentStore_*' -print > "$work/candidates"
    else
        printf '跳过不存在的服务目录: %s\n' "$root"
        : > "$work/candidates"
    fi
    standalone="$servers/$service/nohup.out"
    if [ ! -e "$root/nohup.out" ] && [ -f "$standalone" ] && [ ! -L "$standalone" ]; then
        printf '%s\n' "$standalone" >> "$work/candidates"
    fi

    # 这里只看文件名：日期归档按日期选；无日期的当前日志仅在范围含今天时选。
    awk -v from="$from" -v to="$to" -v today="$today" '
        {
            name=$0; sub(/^.*\//, "", name)
            if (tolower(name) ~ /\.(zip|7z|tar|tgz|tar\.gz)$/) next
            day=today
            if (match(name, /[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/))
                day=substr(name, RSTART, RLENGTH)
            if (day>=from && day<=to) print
        }
    ' "$work/candidates" > "$work/list"
    printf '[%s] 选中文件: %s\n' "$service" "$(wc -l < "$work/list")"
    while IFS= read -r path; do
        relative=nohup.out
        case "$path" in "$root/"*) relative=${path#"$root"/} ;; esac
        export_file "$path" "$service/$relative"
    done < "$work/list"
done

if [ "$files" -eq 0 ]; then
    printf 'COPYLOG_EMPTY=1\n'
    exit 0
fi
if [ "$compress" -eq 1 ]; then
    destination="$output/log.tar.gz"
    printf '[压缩] %s 个文件\n' "$files"
    tar -czf "$work/archive.tar.gz" -C "$work/result" .
    # 固定覆盖 log.tar.gz；目标若是目录则拒绝。
    [ ! -d "$destination" ] || fail "目标已是目录，拒绝覆盖: $destination"
    mv -f -- "$work/archive.tar.gz" "$destination"
else
    # 目录结果保留唯一名，不覆盖、不删除已有下载目录。
    suffix=${work##*/.copylog-}
    destination="$output/logs_${from}_${to}_$suffix"
    mv -T -n -- "$work/result" "$destination"
    [ ! -d "$work/result" ] || fail "结果已存在，未覆盖: $destination"
fi
printf '导出文件数: %s\nCOPYLOG_RESULT=%s\n' "$files" "$destination"
