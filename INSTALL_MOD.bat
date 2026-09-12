@echo off
cd /d "%~dp0"

echo ============================================================
echo  DRAGONITE CLIENT - FORCE INSTALL (fixes "GUI not updating")
echo ============================================================
echo.

echo [1/3] Building...
call gradlew.bat clean build --no-daemon
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

set JAR=build\libs\cloth-config-15.0.140.jar
if not exist "%JAR%" (
    echo ERROR: JAR not found
    pause
    exit /b 1
)

echo.
echo [2/3] Removing OLD mod (close Minecraft first!)...
set MODS=%APPDATA%\.minecraft\mods
if not exist "%MODS%" mkdir "%MODS%"
del "%MODS%\cloth-config*.jar" 2>nul
echo   Cleaned %MODS%

echo.
echo [3/3] Copying NEW mod...
copy "%JAR%" "%MODS%\" >nul
if errorlevel 1 (
    echo   Failed - copy manually: %JAR%
) else (
    echo   Installed to %MODS%
)

echo.
echo ============================================================
echo  DONE. Next:
echo  1. CLOSE Minecraft completely
echo  2. Start Minecraft
echo  3. Press Right Control - open GUI
echo  4. Title should say "Dragonite Client [v4]"
echo.
echo  If you still see old GUI: Feather Client uses a CUSTOM
echo  folder. Check: Feather Launcher - Settings - Game Directory
echo  Copy the JAR to: [that folder]\mods\
echo.
echo  JAR location: %CD%\%JAR%
echo ============================================================
pause
