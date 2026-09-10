@echo off
title jobseeker-backend :8080

echo ============================================
echo   jobseeker backend restarter
echo ============================================
echo.

echo [1/3] Stopping old backend on port 8080 ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
  echo     killing PID %%p
  taskkill /F /PID %%p >nul 2>&1
)

echo [2/3] Waiting for port 8080 to be released ...
timeout /t 4 /nobreak >nul

echo [3/3] Starting backend ...
echo     Build output: C:/jobseeker-target  (D: drive cannot rename/delete files)
echo     Wait until you see:  Started JobseekerApplication
echo     To stop it: press Ctrl+C in this window, or just close the window.
echo.

cd /d D:\project\jobseeker\server
set JAVA_HOME=D:\dev\java
call "D:\softw\apache-maven-3.5.4\bin\mvn.cmd" -s .mvn\local-settings.xml spring-boot:run

echo.
echo Backend has stopped.
pause
