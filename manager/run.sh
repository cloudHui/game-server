#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
LOCAL_JAVA="../../jdk-17/bin/java"
if [[ ! -x "$LOCAL_JAVA" ]]; then LOCAL_JAVA="java"; fi
exec "$LOCAL_JAVA" -Dserver.root=. -jar manager/target/ServerManager.jar
