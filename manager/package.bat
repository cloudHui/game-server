@echo off
setlocal

rem 切换到 Server 仓库根目录，确保后续所有相对路径不受调用位置影响。
cd /d "%~dp0.."

rem Windows 打包必须显式使用仓库上两级目录中的 JDK 17。
rem 目录约定：Server\manager\package.bat -> ..\..\jdk-17。
set "LOCAL_JDK=..\..\jdk-17"

rem java.exe 用于 Maven 编译；缺失时立即退出，避免误用 PATH 中的其他 JDK。
if not exist "%LOCAL_JDK%\bin\java.exe" (
    echo 未找到 JDK 17: "%LOCAL_JDK%\bin\java.exe"
    exit /b 1
)

rem jpackage.exe 只随完整 JDK 提供；JRE 或不完整的 JDK 无法生成应用镜像。
if not exist "%LOCAL_JDK%\bin\jpackage.exe" (
    echo 未找到完整 JDK 17: "%LOCAL_JDK%\bin\jpackage.exe"
    exit /b 1
)

rem 同时设置 JAVA_HOME 和 PATH，保证 Maven 及其子进程都使用上述 JDK 17。
set "JAVA_HOME=%CD%\%LOCAL_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem 清理并编译 manager 模块；编译失败时停止打包，保留 Maven 的退出码语义。
call mvn -f manager\pom.xml clean package || exit /b 1

rem 删除上一次生成的应用镜像，并确保本次打包的输出目录存在。
if exist build\manager-package\ServerManager rmdir /s /q build\manager-package\ServerManager
mkdir build\manager-package 2>nul

rem 使用同一套 JDK 17 的 jpackage 生成 Windows app-image（非安装包）。
rem 输入为 Maven 生成的 ServerManager.jar，启动类为 manager.ServerManager。
"%JAVA_HOME%\bin\jpackage.exe" --type app-image --name ServerManager --input manager\target --main-jar ServerManager.jar --main-class manager.ServerManager --dest build\manager-package --app-version 1.0
