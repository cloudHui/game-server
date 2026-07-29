#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROTOC="${SCRIPT_DIR}/protoc-linux-x86_64"

if [[ ! -x "${PROTOC}" ]]; then
  echo "缺少 Linux 协议生成器或不可执行: ${PROTOC}" >&2
  exit 1
fi

for source in const.proto model.proto gate.proto lobby.proto game.proto server.proto; do
  "${PROTOC}" --proto_path="${SCRIPT_DIR}" --java_out="${SCRIPT_DIR}" "${SCRIPT_DIR}/${source}"
done

echo "协议 Java 类生成完成: ${SCRIPT_DIR}/proto"
