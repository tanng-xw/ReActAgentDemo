@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
REM =====================================================
REM 智能音乐助手 - 启动脚本
REM 用于编译和启动 Kimi Agent 服务
REM 服务将运行在 http://localhost:8081
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 启动程序
echo ============================================
echo.

REM 检查 Java 环境
echo [1/4] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java 环境，请确保 Java 21 已正确安装
    pause
    exit /b 1
)
echo [1/4] Java 环境检查通过
echo.

REM 检查 Maven 环境
echo [2/4] 检查 Maven 环境...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Maven 环境，请确保 Maven 已正确安装
    pause
    exit /b 1
)
echo [2/4] Maven 环境检查通过
echo.

REM 检查 API Key
echo [3/4] 检查 API 配置...
if "%OPENAI_API_KEY%"=="" (
    echo [信息] 使用配置文件中设置的 API Key
) else (
    echo [信息] 使用环境变量中的 API Key
)
echo [3/4] API 配置检查完成
echo.

REM 检查是否已有实例在运行（通过端口检测）
echo [4/4] 检查端口占用...
netstat -ano | findstr ":8081" >nul 2>&1
if not errorlevel 1 (
    echo [警告] 检测到端口 8081 已被占用，可能有实例在运行
    choice /C YN /M "是否停止已有实例并重新启动"
    if errorlevel 2 exit /b 1
    call stop.bat
    timeout /t 3 /nobreak >nul
)
echo [4/4] 端口检查完成
echo.

REM 创建日志目录
if not exist logs mkdir logs

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

REM 使用 spring-boot:run 启动（更可靠，无需打包）
set SERVER_PORT=8081
call mvn spring-boot:run -q

REM 如果启动失败，暂停显示错误
if errorlevel 1 (
    echo.
    echo [错误] 服务启动失败
    pause
    exit /b 1
)
