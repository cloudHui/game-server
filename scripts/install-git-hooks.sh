#!/usr/bin/env bash
# 安装仓库内 .githooks 到当前 clone 的 .git/hooks（不改 git config）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/.githooks"
DST="$ROOT/.git/hooks"

[[ -d "$ROOT/.git" ]] || { echo "不是 git 仓库: $ROOT" >&2; exit 1; }
[[ -d "$SRC" ]] || { echo "缺少 $SRC" >&2; exit 1; }
mkdir -p "$DST"

for name in commit-msg prepare-commit-msg; do
  if [[ -f "$SRC/$name" ]]; then
    cp -f "$SRC/$name" "$DST/$name"
    chmod +x "$DST/$name"
    echo "已安装: .git/hooks/$name"
  fi
done

echo "完成。后续 git commit 将自动剥离 Cursor/Codex 署名 trailer。"
