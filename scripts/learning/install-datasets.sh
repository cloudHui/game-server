#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="${1:-datasets}"
TARGET_DIR="${2:-data/learning/datasets}"

[[ -d "$PACKAGE_DIR" ]] || { echo "数据包目录不存在: $PACKAGE_DIR" >&2; exit 1; }
mkdir -p "$TARGET_DIR"

install_archive() {
  local archive=$1 marker="$TARGET_DIR/.${1}.sha256" source="$PACKAGE_DIR/$1" digest stage
  [[ -f "$source" ]] || return 0
  digest="$(sha256sum "$source" | awk '{print $1}')"
  [[ -f "$marker" && "$(tr -d '[:space:]' <"$marker")" == "$digest" ]] && return 0
  stage="$(mktemp -d "$TARGET_DIR/.dataset-stage.XXXXXX")"
  tar -xzf "$source" -C "$stage"
  cp -a "$stage/." "$TARGET_DIR/"
  rm -rf "$stage"
  printf '%s\n' "$digest" >"$marker"
  echo "已安装: $archive"
}

for archive in characters.tar.gz dictionary.tar.gz english-kids.tar.gz english-vocab.tar.gz poetry-idx.tar.gz; do
  install_archive "$archive"
done

if [[ -f "$PACKAGE_DIR/poetry.jsonl.gz" ]]; then
  digest="$(sha256sum "$PACKAGE_DIR/poetry.jsonl.gz" | awk '{print $1}')"
  marker="$TARGET_DIR/.poetry.jsonl.gz.sha256"
  if [[ ! -f "$marker" || "$(tr -d '[:space:]' <"$marker")" != "$digest" ]]; then
    tmp="$(mktemp "$TARGET_DIR/.poetry.jsonl.XXXXXX")"
    gzip -cd "$PACKAGE_DIR/poetry.jsonl.gz" >"$tmp"
    mv "$tmp" "$TARGET_DIR/poetry.jsonl"
    printf '%s\n' "$digest" >"$marker"
    echo "已安装: poetry.jsonl.gz"
  fi
fi

[[ ! -f "$PACKAGE_DIR/textbooks.json" ]] || cp -f "$PACKAGE_DIR/textbooks.json" "$TARGET_DIR/textbooks.json"
echo "学习资源已就绪: $TARGET_DIR"
