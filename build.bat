@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo Build failed. See the errors above.
  exit /b 1
)
echo.
echo Build completed: build\libs\isoforge-forge-1.20.1-0.2.0.jar
exit /b 0
