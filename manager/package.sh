#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
LOCAL_JDK="../../jdk-17"
if [[ ! -x "$LOCAL_JDK/bin/jpackage" ]]; then LOCAL_JDK="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"; fi
JAVA_HOME="$LOCAL_JDK" PATH="$LOCAL_JDK/bin:$PATH" mvn -f manager/pom.xml clean package
rm -rf build/manager-package/ServerManager
mkdir -p build/manager-package
"$LOCAL_JDK/bin/jpackage" --type app-image --name ServerManager --input manager/target --main-jar ServerManager.jar --main-class manager.ServerManager --dest build/manager-package --app-version 1.0
