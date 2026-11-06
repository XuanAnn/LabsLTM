@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" client.ClientUI
:end
pause
