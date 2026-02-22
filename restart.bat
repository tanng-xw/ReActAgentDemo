@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
REM =====================================================
REM 智能音乐助手 - 重启脚本
REM 执行流程：停止 - 清理 - 编译 - 启动
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 重启程序
echo ============================================
echo.

REM 步骤1：停止项目
echo [步骤 1/3] 正在停止项目...
call stop.bat
if errorlevel 1 (
    echo [警告] 停止项目时出现问题，继续执行...
)
timeout /t 2 /nobreak >nul
echo.

REM 步骤2：清理并编译项目
echo [步骤 2/3] 正在清理并编译项目...
call mvn clean compile -q
if errorlevel 1 (
    echo [错误] 编译项目失败
    pause
    exit /b 1
)
echo [步骤 2/3] 项目编译成功
echo.

REM 步骤3：启动项目
echo [步骤 3/3] 正在启动项目...
echo 日志文件: logs\app.log
echo.

REM 创建日志目录
if not exist logs mkdir logs

REM 使用 spring-boot:run 启动
echo.
echo ============================================
echo      正在启动服务...
echo ============================================
echo  访问地址: http://localhost:8081
echo  API地址: http://localhost:8081/api/chat/health
echo  日志文件: logs\app.log
echo.
echo  按 Ctrl+C 停止服务
echo.

set SERVER_PORT=8081
call mvn spring-boot:run -q

REM 如果启动失败，暂停显示错误
if errorlevel 1 (
    echo.
    echo [错误] 服务启动失败
    pause
    exit /b 1
)
