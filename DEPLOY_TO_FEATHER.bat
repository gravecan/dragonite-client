@echo off
REM Use this if BUILD_AND_DEPLOY.bat copies to the wrong folder.
REM Feather Client may use a custom Game Directory - find it in launcher settings.

cd /d "%~dp0"

echo Building...
call gradlew.bat clean build
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
echo JAR built: %JAR%
echo.
echo PASTE your Feather/Minecraft mods folder path below.
echo Example: C:\Users\YourName\AppData\Roaming\.minecraft\mods
echo Example: D:\Games\Feather\mods
echo.
set /p MODS="Mods folder path: "

if not exist "%MODS%" (
    echo Folder not found: %MODS%
    pause
    exit /b 1
)

del "%MODS%\cloth-config*.jar" 2>nul
copy "%JAR%" "%MODS%\"

echo.
echo Copied to %MODS%
echo Restart Minecraft.
pause
