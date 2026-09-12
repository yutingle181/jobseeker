@echo off
rem 构建后把前端产物同步到后端 static 目录（供 spring-boot:run 返回 SPA）。
rem 使用 robocopy：1) 不依赖删除/重命名，规避 D: 盘 safe-delete 限制；
rem 2) 只复制新增/变更文件，旧哈希文件自然被覆盖。
rem robocopy 退出码：0=无变动 1=已复制 均为成功；>=2 才是真正错误。
setlocal
robocopy dist ..\server\src\main\resources\static /E /R:1 /W:1
if %errorlevel% leq 1 (exit /b 0)
exit /b %errorlevel%
