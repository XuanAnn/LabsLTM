@echo off
cd /d "%~dp0"
if not exist "target\lab-ftp-1.0.jar" powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
if errorlevel 1 goto end
if not exist "target\dependency\mysql-connector-j-9.2.0.jar" powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
if errorlevel 1 goto end
powershell -NoProfile -ExecutionPolicy Bypass -File run.ps1 server
:end
pause
