@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0.."
set "LOCAL_JDK=D:\download\jdk-17.0.12"

if not exist "%LOCAL_JDK%\bin\java.exe" (echo 未找到JDK&exit /b 1)
if not exist "%LOCAL_JDK%\bin\jpackage.exe" (echo 未找到jpackage&exit /b 1)

set "JAVA_HOME=%LOCAL_JDK%"
set "PATH=%LOCAL_JDK%\bin;%PATH%"

echo 开始Maven编译...
call mvn -f pom.xml clean package || exit /b 1

if not exist "manager\target\ServerManager.jar" (echo 未找到jar&exit /b 1)

if exist build\manager-package\ServerManager rmdir /s /q build\manager-package\ServerManager
mkdir build\manager-package 2>nul

echo 生成应用镜像...
"%JAVA_HOME%\bin\jpackage.exe" --type app-image --name ServerManager --input manager\target --main-jar ServerManager.jar --main-class manager.ServerManager --dest build\manager-package --app-version 1.0 || exit /b 1

echo 打包成功！
endlocal