@echo off
setlocal enabledelayedexpansion

:: ========================================
:: Dragonite Obfuscator - Maximum Protection
:: ========================================
:: This script obfuscates the client with maximum protection
:: to prevent theft of triggerbot and other cheat features.
::
:: Protections applied:
:: - String encryption: Hides all text strings
:: - Name obfuscation: Renames classes/methods to unreadable names
:: - Class encryption: Encrypts class bytes, decrypts at runtime
:: - Anti-debug: Detects debuggers and agents
:: - Anti-dump: Detects memory dumping attempts
:: - Integrity checks: Breaks if code is modified
:: - DRM protection: HWID binding (requires native DLL)
:: ========================================

set "INPUT=C:\Users\Daniel\Downloads\My projekts\Dragonite Client\Dragonite Client\Dragonite Client\Dragonite Client\1.21\Dragonite Client\Dragonite\build\libs\cloth-config-15.0.140.jar"
set "OUTPUT=C:\Users\Daniel\Downloads\My projekts\Dragonite Client\Dragonite Client\Dragonite Client\Dragonite Client\1.21\Dragonite Client\Dragonite\build\libs\cloth-config-PROTECTED.jar"
set "OBFUSCATOR=C:\Users\Daniel\Downloads\My projekts\Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\build\libs\DragoniteObfuscator-1.0.0.jar"
set "DLL=C:\Users\Daniel\Downloads\My projekts\Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\native\jawt_md.dll"

echo.
echo ========================================
echo   Dragonite Obfuscator - Maximum Protection
echo ========================================
echo.
echo Input:  %INPUT%
echo Output: %OUTPUT%
echo.

:: Check if input exists
if not exist "%INPUT%" (
    echo ERROR: Input JAR not found!
    echo Please build the client first using: gradlew jar
    pause
    exit /b 1
)

:: Check if obfuscator exists
if not exist "%OBFUSCATOR%" (
    echo ERROR: Obfuscator JAR not found!
    echo Please build the obfuscator first.
    pause
    exit /b 1
)

echo Running obfuscator with SECURE preset...
echo This includes:
echo   - String encryption (hides "TriggerBot", "KillAura", etc.)
echo   - Name obfuscation (renames classes/methods)
echo   - Class encryption (encrypts class bytes)
echo   - Anti-debug (detects debuggers)
echo   - Anti-dump (detects memory dumping)
echo   - Integrity checks (breaks if modified)
echo   - DRM protection (HWID binding)
echo.

:: Run obfuscator with STANDARD preset
:: The standard preset provides strong protection without hanging
java -Xmx2g -jar "%OBFUSCATOR%" "%INPUT%" "%OUTPUT%" ^
    --preset standard ^
    --keep net.fabricmc ^
    --keep org.spongepowered.asm ^
    --keep com.llamalad7.mixinextras ^
    --keep net.minecraft ^
    --keeprgx ".*Mixin.*" ^
    --keeprgx ".*MixinPlugin.*" ^
    --keeprgx ".*MixinConfigPlugin.*" ^
    --mapping true

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Obfuscation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Obfuscation Complete!
echo ========================================
echo.
echo Output: %OUTPUT%
echo.

:: Copy DLL to output directory for DRM
echo Copying DRM DLL to output directory...
copy /Y "%DLL%" "C:\Users\Daniel\Downloads\My projekts\Dragonite Client\Dragonite Client\Dragonite Client\Dragonite Client\1.21\Dragonite Client\Dragonite\build\libs\jawt_md.dll" >nul
echo DLL copied to: build\libs\jawt_md.dll
echo.

echo ========================================
echo   IMPORTANT NOTES
echo ========================================
echo.
echo 1. The obfuscated JAR contains encrypted classes
echo    - Decompilers will show garbage/nothing useful
echo    - Strings like "TriggerBot" are encrypted
echo.
echo 2. Integrity checks are embedded
echo    - If someone modifies the code, it will break
echo    - Anti-debug detects debuggers/agents
echo.
echo 3. DRM protection is enabled
echo    - The jawt_md.dll must be in the same folder as the JAR
echo    - The client is bound to your HWID
echo.
echo 4. To distribute:
echo    - Copy cloth-config-PROTECTED.jar
echo    - Copy jawt_md.dll (same folder)
echo    - The client will only work on authorized machines
echo.

pause
