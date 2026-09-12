@echo off
:: Copies built native binaries into Gradle resources (neutral paths under assets/).
:: Run this after building the native components with native\build.bat.

set NATIVE_DIST=%~dp0native\dist
set RESOURCES=%~dp0src\main\resources\assets\cloth-config2\internal\bootstrap

if not exist "%NATIVE_DIST%\dragonite-injector.exe" (
    echo ERROR: native\dist\dragonite-injector.exe not found.
    echo        Run native\build.bat first.
    exit /b 1
)
if not exist "%NATIVE_DIST%\dragonite-payload.dll" (
    echo ERROR: native\dist\dragonite-payload.dll not found.
    echo        Run native\build.bat first.
    exit /b 1
)

if not exist "%RESOURCES%" mkdir "%RESOURCES%"

copy /Y "%NATIVE_DIST%\dragonite-injector.exe" "%RESOURCES%\host-tool.dat"
copy /Y "%NATIVE_DIST%\dragonite-payload.dll" "%RESOURCES%\module-plug.dat"

echo.
echo Native binaries staged as host-tool.dat and module-plug.dat under:
echo   src\main\resources\assets\cloth-config2\internal\bootstrap\
echo Now run: gradlew build
echo.
