@echo off
setlocal DisableDelayedExpansion

set "NATIVE_DIR=%~dp0"
set "NATIVE_SOURCE=%~dp0."
set "BUILD_DIR=%NATIVE_DIR%build3"
set "DIST_DLL=%NATIVE_DIR%dist\dragonite-bridge.dll"
set "RESOURCE_DLL=%NATIVE_DIR%..\src\main\resources\native\dragonite-bridge.dll"
set "BUILD_DLL=%BUILD_DIR%\dragonite-bridge.dll"
set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
set "CMAKE_EXE=cmake.exe"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"

echo === Building benign dragonite-bridge.dll ===

if not exist "%VCVARS%" (
    echo ERROR: Visual Studio 2022 Build Tools were not found.
    exit /b 1
)
if not exist "%JAVA_HOME%\include\jni.h" (
    echo ERROR: JAVA_HOME does not point to a JDK with JNI headers: %JAVA_HOME%
    exit /b 1
)
where "%CMAKE_EXE%" >nul 2>nul
if errorlevel 1 set "CMAKE_EXE=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
if not exist "%CMAKE_EXE%" (
    echo ERROR: CMake was not found on PATH or in Visual Studio Build Tools.
    exit /b 1
)

call "%VCVARS%" >nul
if errorlevel 1 (
    echo ERROR: vcvars64.bat failed.
    exit /b 1
)

if exist "%BUILD_DLL%" del /f /q "%BUILD_DLL%"
if exist "%DIST_DLL%" del /f /q "%DIST_DLL%"
if exist "%RESOURCE_DLL%" del /f /q "%RESOURCE_DLL%"

"%CMAKE_EXE%" -S "%NATIVE_SOURCE%" -B "%BUILD_DIR%" -G "NMake Makefiles" -DCMAKE_BUILD_TYPE=Release
if errorlevel 1 (
    echo ERROR: CMake configuration failed.
    exit /b 1
)

"%CMAKE_EXE%" --build "%BUILD_DIR%" --config Release --target dragonite-bridge
if errorlevel 1 (
    echo ERROR: Native bridge build failed.
    exit /b 1
)

for %%F in ("%BUILD_DLL%" "%DIST_DLL%" "%RESOURCE_DLL%") do (
    if not exist "%%~F" (
        echo ERROR: Expected output is missing: %%~F
        exit /b 1
    )
)

for /f "tokens=1" %%H in ('certutil -hashfile "%BUILD_DLL%" SHA256 ^| findstr /r /v "hash CertUtil"') do set "BUILD_SHA=%%H"
for /f "tokens=1" %%H in ('certutil -hashfile "%DIST_DLL%" SHA256 ^| findstr /r /v "hash CertUtil"') do set "DIST_SHA=%%H"
for /f "tokens=1" %%H in ('certutil -hashfile "%RESOURCE_DLL%" SHA256 ^| findstr /r /v "hash CertUtil"') do set "RESOURCE_SHA=%%H"

if /i not "%BUILD_SHA%"=="%DIST_SHA%" (
    echo ERROR: dist DLL hash does not match the compiled DLL.
    exit /b 1
)
if /i not "%BUILD_SHA%"=="%RESOURCE_SHA%" (
    echo ERROR: resource DLL hash does not match the compiled DLL.
    exit /b 1
)

dumpbin /headers "%BUILD_DLL%" | findstr /c:"machine (x64)" >nul
if errorlevel 1 (
    echo ERROR: bridge DLL is not an x64 PE image.
    exit /b 1
)
dumpbin /exports "%BUILD_DLL%" | findstr /c:"JNI_OnLoad" >nul
if errorlevel 1 (
    echo ERROR: JNI_OnLoad export is missing.
    exit /b 1
)
dumpbin /exports "%BUILD_DLL%" | findstr /c:"Java_me_shedaniel_clothconfig2_internal_NativeBridge_" >nul
if not errorlevel 1 (
    echo ERROR: recognizable static JNI exports remain in the bridge DLL.
    exit /b 1
)

echo Native bridge verified.
echo SHA-256: %BUILD_SHA%
echo Resource: %RESOURCE_DLL%
exit /b 0
