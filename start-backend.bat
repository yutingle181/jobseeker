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
ping -n 5 127.0.0.1 >nul

echo [3/3] Starting backend ...
echo     Build output : D:/project/jobseeker/server/target  (类/编译产物，与源码同盘避免跨盘 relativize 错)
echo     Resources    : C:/jobseeker-target/classes  (D: 盘 rename 被禁，资源单独输出到 C: 再经 classpath 加回)
echo     JAVA_HOME    : C:\Program Files\Java\jdk-17  (必须用 JDK17，JDK24 会让 Lombok 报 TypeTag.UNKNOWN)
echo     Wait until you see:  Started JobseekerApplication
echo     To stop it: press Ctrl+C in this window, or just close the window.
echo.

cd /d D:\project\jobseeker\server
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
call "D:\softw\apache-maven-3.5.4\bin\mvn.cmd" -s .mvn\local-settings.xml spring-boot:run

echo.
echo Backend has stopped.
pause
