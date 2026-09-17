@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Kingdom MCP

set "MCP_NAME=KingdomMcpServer"
set "MCP_DIR=%~dp0"
set "MCP_JAR=%MCP_DIR%McpServer.jar"
for %%I in ("%MCP_DIR%..\..\..\..\Server") do set "MCP_ROOT=%%~fI"
set "MCP_PORT=18765"
set "MCP_JAVA_OPTS=-Xms64m -Xmx256m -Dfile.encoding=UTF-8"

if /i "%~1"=="install" goto do_install
if /i "%~1"=="start" goto start
if /i "%~1"=="stop" goto stop
if /i "%~1"=="restart" goto do_restart
if /i "%~1"=="status" goto status
if /i "%~1"=="uninstall" goto do_uninstall

:menu
cls
echo ========================================
echo   Kingdom MCP
echo   http://127.0.0.1:%MCP_PORT%/mcp
echo ========================================
call echo 1  状态
call echo 2  启动
call echo 3  停止
call echo 4  重启
call echo 5  安装登录启动
call echo 6  卸载登录启动
call echo 0  退出
echo ========================================
set "SEL="
set /p SEL=请选择: 
if "%SEL%"=="1" goto do_status
if "%SEL%"=="2" goto do_start
if "%SEL%"=="3" goto do_stop
if "%SEL%"=="4" goto do_restart
if "%SEL%"=="5" goto do_install
if "%SEL%"=="6" goto do_uninstall
if "%SEL%"=="0" goto :eof
echo 无效选择
goto wait

:do_status
call :status
goto wait
:do_start
call :start
goto wait
:do_stop
call :stop
goto wait
:do_restart
call :stop
call :start
if not "%~1"=="" exit /b %errorlevel%
goto wait
:do_install
call :java
if errorlevel 1 goto wait
powershell -NoProfile -Command "$p='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'; New-Item -Path $p -Force | Out-Null; Set-ItemProperty -Path $p -Name $env:MCP_NAME -Value ('\"'+$env:MCP_JAVA+'\" '+$env:MCP_JAVA_OPTS+' -jar \"'+$env:MCP_JAR+'\"')"
if errorlevel 1 (echo Install failed.& goto wait)
echo Installed login startup: %MCP_NAME%
call :start
if not "%~1"=="" exit /b %errorlevel%
goto wait
:do_uninstall
powershell -NoProfile -Command "$p='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'; Remove-ItemProperty -Path $p -Name $env:MCP_NAME -ErrorAction SilentlyContinue"
call :stop
echo Removed login startup: %MCP_NAME%
if not "%~1"=="" exit /b 0
goto wait
:wait
echo.
pause
goto menu

:start
if not exist "%MCP_JAR%" (echo Missing: %MCP_JAR%& exit /b 1)
call :pid
if defined MCP_PID (echo Already running, PID %MCP_PID%& exit /b 0)
call :java
if errorlevel 1 exit /b 1
start "" "%MCP_JAVA%" %MCP_JAVA_OPTS% -jar "%MCP_JAR%"
if errorlevel 1 exit /b 1
for /l %%I in (1,1,20) do (
    powershell -NoProfile -Command "Start-Sleep -Seconds 1"
    call :health
    if not errorlevel 1 (echo Started: http://127.0.0.1:%MCP_PORT%/mcp& exit /b 0)
)
echo Start failed. Check %MCP_ROOT%\.mcp\log
exit /b 1

:stop
call :pid
if not defined MCP_PID (echo Not running.& exit /b 0)
powershell -NoProfile -Command "Stop-Process -Id $env:MCP_PID -Force"
if errorlevel 1 exit /b 1
echo Stopped PID %MCP_PID%
exit /b 0

:status
call :pid
if not defined MCP_PID (echo Not running.& exit /b 1)
call :health
if errorlevel 1 (echo Process exists, PID %MCP_PID%, but health check failed.& exit /b 1)
echo Running, PID %MCP_PID%: http://127.0.0.1:%MCP_PORT%/mcp
exit /b 0

:java
set "MCP_JAVA=javaw.exe"
where javaw.exe >nul 2>&1
if errorlevel 1 if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "MCP_JAVA=%JAVA_HOME%\bin\javaw.exe"
where "%MCP_JAVA%" >nul 2>&1
if errorlevel 1 (echo Java 8 javaw.exe not found.& exit /b 1)
exit /b 0

:pid
set "MCP_PID="
for /f %%P in ('powershell -NoProfile -Command "$j=$env:MCP_JAR; Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'javaw.exe' -or $_.Name -eq 'java.exe') -and $_.CommandLine -like ('*'+$j+'*') } | Select-Object -First 1 -ExpandProperty ProcessId"') do set "MCP_PID=%%P"
exit /b 0

:health
powershell -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:'+$env:MCP_PORT+'/health') -TimeoutSec 2; if ($r.StatusCode -eq 200 -and $r.Content -eq 'OK') { exit 0 } } catch {}; exit 1" >nul 2>&1
exit /b %errorlevel%
