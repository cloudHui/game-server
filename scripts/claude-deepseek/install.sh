#!/usr/bin/env bash
set -Eeuo pipefail

RAW_BASE="${CLAUDE_DEEPSEEK_RAW_BASE:-https://raw.githubusercontent.com/cloudHui/game-server/main/scripts/claude-deepseek}"
INSTALL_DIR="${CLAUDE_DEEPSEEK_INSTALL_DIR:-$HOME/.local/share/claude-deepseek}"
BIN_DIR="${CLAUDE_DEEPSEEK_BIN_DIR:-$HOME/.local/bin}"

command -v curl >/dev/null 2>&1 || {
  printf '缺少 curl，请先安装。\n' >&2
  exit 1
}

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
for file in claude-deepseek.sh install-matt-skills.sh matt-skills-required.md; do
  curl -fsSL "$RAW_BASE/$file" -o "$INSTALL_DIR/$file"
done
chmod +x "$INSTALL_DIR/claude-deepseek.sh" "$INSTALL_DIR/install-matt-skills.sh"
ln -sfn "$INSTALL_DIR/claude-deepseek.sh" "$BIN_DIR/claude-deepseek"

export PATH="$BIN_DIR:$PATH"
if [[ -r /dev/tty ]]; then
  exec "$INSTALL_DIR/claude-deepseek.sh" setup </dev/tty
fi
exec "$INSTALL_DIR/claude-deepseek.sh" setup
