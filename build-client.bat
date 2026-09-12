@echo off
cd /d "%~dp0"
call gradlew.bat build --no-daemon %*
exit /b %ERRORLEVEL%
