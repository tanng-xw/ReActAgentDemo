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
echo [步骤 1/4] 正在停止项目...
call stop.bat
if errorlevel 1 (
    echo [警告] 停止项目时出现问题，继续执行...
)
timeout /t 2 /nobreak >nul
echo.

REM 步骤2：清理项目
echo [步骤 2/4] 正在清理项目...
call mvn clean -q
if errorlevel 1 (
    echo [错误] 清理项目失败
    pause
    exit /b 1
)
echo [步骤 2/4] 项目清理完成
echo.

REM 步骤3：重新编译
echo [步骤 3/4] 正在重新编译项目...
call mvn package -DskipTests -q
if errorlevel 1 (
    echo [错误] 编译项目失败
    pause
    exit /b 1
)
echo [步骤 3/4] 项目编译成功
echo.

REM 检查 JAR 文件是否存在
if not exist target\kimi-agent-1.0.0.jar (
    echo [错误] 未找到 JAR 文件
    pause
    exit /b 1
)

REM 步骤4：启动项目
echo [步骤 4/4] 正在启动项目...
echo 日志文件: logs\app.log
echo.

REM 创建日志目录
if not exist logs mkdir logs

REM 启动应用
start "KimiAgent" cmd /c "chcp 65001 >nul && java -jar target\kimi-agent-1.0.0.jar"

echo 等待服务启动...
timeout /t 8 /nobreak >nul

REM 检查服务是否启动成功
powershell -Command "Invoke-RestMethod -Uri 'http://localhost:8081/api/chat/health' -TimeoutSec 5" >nul 2>&1

if errorlevel 1 (
    echo [错误] 服务启动失败，请检查日志: logs\app.log
    pause
    exit /b 1
)

echo.
echo ============================================
echo      重启成功！
echo ============================================
echo  访问地址: http://localhost:8081
echo  API地址: http://localhost:8081/api/chat/health
echo.
@REM pause
@REM start http://localhost:8081
