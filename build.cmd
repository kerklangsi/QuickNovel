@echo off
cd /d "%~dp0"

echo ========================================
echo        QuickNovel Debug Build
echo ========================================
echo.

echo [1/4] Stopping Gradle...
call gradlew.bat --stop >nul 2>&1

echo [2/4] Stopping possible file-locking processes...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1

echo [3/4] Removing old build files...
if exist "app\build" (
    rmdir /S /Q "app\build" 2>nul
)

if exist "app\build" (
    echo.
    echo ERROR: Could not delete app\build
    echo Another program is still locking the files.
    echo.
    pause
    exit /b 1
)

echo [4/4] Building Debug APK...
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ========================================
    echo        BUILD FAILED
    echo ========================================
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo        BUILD SUCCESSFUL
echo ========================================
echo.
echo APK files:
dir /s /b "app\build\outputs\apk\*.apk"

echo.
echo Opening APK folder...
start "" "%~dp0app\build\outputs\apk\debug"

exit /b 0