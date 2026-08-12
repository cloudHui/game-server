@echo off
setlocal
cd /d "%~dp0.."
call mvn -f manager\pom.xml clean package || exit /b 1
if exist build\manager-package\ServerManager rmdir /s /q build\manager-package\ServerManager
mkdir build\manager-package 2>nul
jpackage --type app-image --name ServerManager --input manager\target --main-jar ServerManager.jar --main-class manager.ServerManager --dest build\manager-package --app-version 1.0
