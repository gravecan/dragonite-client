@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  Dragonite Native Build
echo ============================================================

:: ------- Sanity checks -------
where cmake >nul 2>&1 || (
    echo ERROR: cmake not on PATH. Install CMake 3.20+.
    exit /b 1
)
where cl >nul 2>&1 || (
    echo ERROR: cl.exe not found. Run this from an x64 Native Tools Command Prompt.
    echo        Or run: "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
    exit /b 1
)
if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME is not set. Point it at a JDK.
    exit /b 1
)
if not exist "%JAVA_HOME%\include\jni.h" (
    echo ERROR: jni.h not found under %%JAVA_HOME%%\include. Is JAVA_HOME a JDK?
    exit /b 1
)

echo JAVA_HOME = %JAVA_HOME%

:: ------- Build -------
set BUILD_DIR=%~dp0build
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo.
echo [1/2] Configuring CMake...
cmake -S "%~dp0" -B "%BUILD_DIR%" -G "Ninja" -DCMAKE_BUILD_TYPE=Release 2>nul
if errorlevel 1 (
    echo Ninja not found, falling back to NMake...
    cmake -S "%~dp0" -B "%BUILD_DIR%" -G "NMake Makefiles" -DCMAKE_BUILD_TYPE=Release
    if errorlevel 1 (
        echo ERROR: CMake configure failed.
        exit /b 1
    )
)

echo.
echo [2/2] Building...
cmake --build "%BUILD_DIR%" --config Release
if errorlevel 1 (
    echo ERROR: Build failed.
    exit /b 1
)

echo.
echo ============================================================
echo  Build complete. Output in: %~dp0dist\
echo    dragonite-injector.exe
echo    dragonite-payload.dll
echo ============================================================
echo.
echo Next: copy dist\dragonite-injector.exe and dist\dragonite-payload.dll
echo into src\main\resources\native\ so Gradle embeds them in the JAR.
echo.
