@echo off
chcp 65001
REM =====================================================
REM 智能音乐助手 - 重启脚本
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 重启程序
echo ============================================
echo.

REM 步骤1：停止项目
echo [*] 正在停止项目...
call stop.bat
timeout /t 2 /nobreak
echo.

REM 步骤2：清理并编译项目
echo [*] 正在清理并编译项目...
call mvn clean compile -q
echo [*] 项目编译完成
echo.

REM 步骤3：启动项目
echo [*] 正在启动项目...

REM 创建日志目录
if not exist logs mkdir logs

echo.
echo ============================================
echo      正在启动服务...
echo ============================================
echo 访问地址: http://localhost:8081
echo.

REM 在新窗口中启动服务
start "KimiAgent Server" cmd /k "chcp 65001 && set SERVER_PORT=8081 && mvn spring-boot:run -q"

echo [*] 等待服务启动...
timeout /t 6 /nobreak

echo [*] 重启完成！正在打开浏览器...
start http://localhost:8081
echo.
