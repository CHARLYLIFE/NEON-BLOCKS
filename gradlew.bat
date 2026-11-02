@echo off
setlocal
set GRADLE_VERSION=8.13
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set BASE=%USERPROFILE%\.gradle\neon-bootstrap
set INSTALL=%BASE%\gradle-%GRADLE_VERSION%
if not exist "%INSTALL%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%BASE%\gradle.zip'; Expand-Archive -Force '%BASE%\gradle.zip' '%BASE%'"
  if errorlevel 1 exit /b 1
)
call "%INSTALL%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
