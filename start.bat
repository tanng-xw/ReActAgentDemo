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

REM 编译项目
echo [4/4] 编译并启动项目...
echo.

REM 检查是否已有实例在运行（通过端口检测）
netstat -ano | findstr ":8081" >nul 2>&1
if not errorlevel 1 (
    echo [警告] 检测到端口 8081 已被占用，可能有实例在运行
    choice /C YN /M "是否停止已有实例并重新启动"
    if errorlevel 2 exit /b 1
    call stop.bat
    timeout /t 3 /nobreak >nul
)

REM 创建日志目录
if not exist logs mkdir logs

REM 编译并启动
echo 正在编译项目...
call mvn clean package -DskipTests -q

if errorlevel 1 (
    echo [错误] 编译失败，请检查错误信息
    pause
    exit /b 1
)

echo.
echo 编译成功！
echo.
echo 正在启动服务...
echo 日志文件: logs\app.log
echo.

REM 检查 JAR 文件是否存在
if not exist target\kimi-agent-1.0.0.jar (
    echo [错误] 未找到 JAR 文件: target\kimi-agent-1.0.0.jar
    pause
    exit /b 1
)

REM 启动应用（使用 start 命令创建新窗口）
start "KimiAgent" java -jar target\kimi-agent-1.0.0.jar

echo 等待服务启动...
timeout /t 8 /nobreak >nul

REM 检查服务是否启动成功
powershell -Command "try { Invoke-RestMethod -Uri 'http://localhost:8081/api/chat/health' -TimeoutSec 5 >$null; exit 0 } catch { exit 1 }" >nul 2>&1

if errorlevel 1 (
    echo [错误] 服务启动失败，请检查日志: logs\app.log
    echo.
    echo 常见原因：
    echo   1. API Key 配置错误
    echo   2. 端口 8081 被其他程序占用
    echo   3. Java 版本不兼容
    pause
    exit /b 1
)

echo.
echo ============================================
echo      服务启动成功！
echo ============================================
echo  访问地址: http://localhost:8081
echo  API地址: http://localhost:8081/api/chat/health
echo  日志文件: logs\app.log
echo.
echo  按任意键打开浏览器...
pause >nul
start http://localhost:8081
