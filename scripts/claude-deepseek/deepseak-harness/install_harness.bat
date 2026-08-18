@echo off
chcp 65001 >nul
title DeepSeek Harness 一键安装脚本 (Windows)

echo ========================================
echo   DeepSeek Harness 一键安装脚本
echo   适用于 Windows 10/11
echo ========================================
echo.

REM ============================================
REM 步骤1: 检测并安装 Node.js
REM ============================================
echo [1/5] 检测 Node.js 环境...

where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [!] 未检测到 Node.js，正在下载安装程序...
    echo     请在弹出的安装窗口中按提示完成安装。
    echo     安装完成后，请按任意键继续本脚本...
    echo.
    
    powershell -Command "Invoke-WebRequest -Uri 'https://nodejs.org/dist/v22.13.0/node-v22.13.0-x64.msi' -OutFile '%TEMP%\node.msi'"
    start /wait %TEMP%\node.msi
    del %TEMP%\node.msi 2>nul
    
    echo.
    echo [*] 请确认 Node.js 已安装完成，然后按任意键继续...
    pause >nul
    
    where node >nul 2>nul
    if %errorlevel% neq 0 (
        echo [X] Node.js 安装失败或未完成，请手动安装后重新运行本脚本
        echo     下载地址: https://nodejs.org/
        pause
        exit /b 1
    )
) else (
    echo [√] Node.js 已安装: 
    node --version
)

echo.

REM ============================================
REM 步骤2: 配置 npm 镜像并安装 Harness
REM ============================================
echo [2/5] 配置 npm 并使用国内镜像安装 Harness...

call npm config set registry https://registry.npmmirror.com

call npm install -g @deepseek-ai/dsh --registry https://registry.npmmirror.com

if %errorlevel% neq 0 (
    echo [X] Harness 安装失败，请检查网络连接后重试
    echo     可尝试手动执行: npm install -g @deepseek-ai/dsh
    pause
    exit /b 1
)
echo [√] DeepSeek Harness 安装成功

echo.

REM ============================================
REM 步骤3: 创建技能配置文件
REM ============================================
echo [3/5] 创建技能配置文件...

set CONFIG_FILE=%USERPROFILE%\enable-skills.yml
(
echo - update:
echo     - id: skill-filesystem
echo       disabled: false
echo     - id: tool-skill
echo       disabled: false
echo     - id: skill-badge
echo       disabled: false
) > "%CONFIG_FILE%"

if exist "%CONFIG_FILE%" (
    echo [√] 配置文件已创建: %CONFIG_FILE%
) else (
    echo [X] 配置文件创建失败，请检查磁盘权限
    pause
    exit /b 1
)

echo.

REM ============================================
REM 步骤4: 刷新环境变量并确定 dsh 调用方式
REM ============================================
echo [4/6] 刷新环境变量...

call refreshenv >nul 2>nul

where dsh >nul 2>nul
if %errorlevel% neq 0 (
    echo [!] dsh 命令暂未识别，将使用 npx 方式调用
    set "DSH_CMD=npx @deepseek-ai/dsh"
) else (
    echo [√] dsh 命令已识别
    set "DSH_CMD=dsh"
)

echo.

REM ============================================
REM 步骤5: 安装插件（使用 %DSH_CMD% 变量，且每行独立容错）
REM ============================================
echo [5/6] 安装插件（网络问题将自动跳过）...

echo [*] 安装 ModSearch (联网搜索)...
call %DSH_CMD% plugin --profile web add @liustack/modsearch --registry https://registry.npmmirror.com
if %errorlevel% neq 0 echo [!] ModSearch 安装失败，跳过

echo [*] 安装 dsh-vision-toolkit (看图)...
call %DSH_CMD% plugin --profile web add @anionex/dsh-vision-toolkit --registry https://registry.npmmirror.com
if %errorlevel% neq 0 echo [!] dsh-vision-toolkit 安装失败，跳过

echo [*] 安装 dsh-web-ui (界面增强)...
call %DSH_CMD% plugin --profile web add github:zhu1090093659/dsh-web-ui#main
if %errorlevel% neq 0 echo [!] dsh-web-ui 安装失败，跳过

echo [*] 安装 dsh-at-file (文件引用)...
call %DSH_CMD% plugin --profile web add github:omdsh-dev/dsh-at-file#main
if %errorlevel% neq 0 echo [!] dsh-at-file 安装失败，跳过

echo [*] 安装 dsh-annotation (批注)...
call %DSH_CMD% plugin --profile web add git+https://github.com/omdsh-dev/dsh-annotation.git
if %errorlevel% neq 0 echo [!] dsh-annotation 安装失败，跳过

echo [*] 安装 dsh-context-doctor (Token监控)...
call %DSH_CMD% plugin --profile web add github:Zhenyu98/dsh-context-doctor#main
if %errorlevel% neq 0 echo [!] dsh-context-doctor 安装失败，跳过

echo [√] 插件安装流程结束（部分失败可后续手动补装）

echo.

REM ============================================
REM 步骤6: 启动 Harness
REM ============================================
echo [6/6] 启动 DeepSeek Harness...
echo ========================================
echo  安装完成！正在启动 Web 服务...
echo  浏览器将自动打开 http://127.0.0.1:3080
echo  首次使用请在设置中配置 API Key
echo  按 Ctrl+C 可停止服务
echo ========================================
echo.

start http://127.0.0.1:3080
npx @deepseek-ai/dsh web --patch "%CONFIG_FILE%"

pause