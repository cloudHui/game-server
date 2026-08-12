@echo off
setlocal
cd /d "%~dp0.."
java -Dserver.root=. -jar manager\target\ServerManager.jar
