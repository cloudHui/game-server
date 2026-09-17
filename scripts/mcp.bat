@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "PORT=18765"
set "JAR=%ROOT%\servertool\target\McpServer.jar"
set "LOGS=%ROOT%\servertool\logs\mcp"

if /I "%~1"=="start" goto start
if /I "%~1"=="stop" goto stop
if /I "%~1"=="status" goto status
if /I "%~1"=="restart" goto restart

:status
call :find_mcp_pid PID
if not defined PID goto status_not_running
echo [MCP] 鏈嶅姟姝ｅ湪杩愯锛孭ID: %PID%锛岀洃鍚鍙? %PORT%
echo [MCP] 鎺㈡椿妫€鏌?
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:18765/health' -TimeoutSec 2; Write-Host $r.Content } catch { Write-Host '鏃犳硶璁块棶鎺㈡椿鎺ュ彛' }"
exit /b 0

:status_not_running
echo [MCP] 鏈嶅姟鏈繍琛岋紝绔彛 %PORT% 绌洪棽銆?exit /b 0

:stop
call :find_mcp_pid PID
if not defined PID goto stop_not_running
echo [MCP] 姝ｅ湪缁堟杩涚▼ PID: %PID%...
taskkill /PID %PID% /T /F >nul 2>&1
echo [MCP] 鏈嶅姟宸插仠姝€?exit /b 0

:stop_not_running
echo [MCP] 鏈嶅姟鏈湪杩愯銆?exit /b 0

:start
call :find_mcp_pid PID
if defined PID goto start_already_running

if not exist "%JAR%" goto start_missing_jar

if not exist "%LOGS%" mkdir "%LOGS%" 2>nul
echo [MCP] 姝ｅ湪鍚姩缁熶竴 MCP 鏈嶅姟锛岀鍙? %PORT%...
start "game-server-mcp" /b cmd /c "cd /d "%ROOT%" && java -Dfile.encoding=UTF-8 -jar "%JAR%""
timeout /t 2 /nobreak >nul

call :find_mcp_pid PID
if not defined PID goto start_failed

echo [MCP] 鏈嶅姟鍚姩鎴愬姛锛孭ID: %PID%
echo [MCP] 鎺㈡椿妫€鏌?
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:18765/health' -TimeoutSec 2; Write-Host $r.Content } catch { Write-Host '鍚姩鍚庢殏鏈搷搴旀帰娲? }"
exit /b 0

:start_already_running
echo [MCP] 鏈嶅姟宸插湪杩愯锛孭ID: %PID%锛岀鍙? %PORT%
exit /b 0

:start_missing_jar
echo [MCP] 閿欒锛氭湭鎵惧埌 %JAR%锛岃鍏堟墽琛?mvn package -pl servertool 鎵撳寘銆?exit /b 1

:start_failed
echo [MCP] 鍚姩澶辫触锛岃妫€鏌ユ帶鍒跺彴杈撳嚭涓庢棩蹇椼€?exit /b 1

:restart
call :stop
timeout /t 1 /nobreak >nul
goto start

:find_mcp_pid
set "%~1="
for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$c = Get-NetTCPConnection -LocalPort 18765 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess; if ($c) { $c } else { $p = Get-CimInstance Win32_Process -Filter 'Name = ''java.exe''' | Where-Object { $_.CommandLine -like '*McpServer.jar*' -or $_.CommandLine -like '*McpServerMain*' } | Select-Object -First 1 -ExpandProperty ProcessId; if ($p) { $p } }"`) do set "%~1=%%I"
exit /b 0
