@echo off
chcp 65001
REM =====================================================
REM 智能音乐助手 - 启动脚本
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 启动程序
echo ============================================
echo.

REM 检查 Java
echo [*] 检查 Java 环境...
java -version
if %errorlevel% neq 0 (
    echo [错误] Java 未安装，请确保 Java 21 已正确安装
    pause
    exit /b 1
)
echo [*] Java 环境正常

REM 停止已有实例
echo [*] 停止已有服务...
call stop.bat
timeout /t 2 /nobreak
echo [*] 准备就绪

REM 创建日志目录
if not exist logs mkdir logs

echo.
echo ============================================
echo      正在启动服务...
echo ============================================
echo 访问地址: http://localhost:8081
echo.

REM 启动服务（新窗口）
start "KimiAgent Server" cmd /k "chcp 65001 && set SERVER_PORT=8081 && mvn spring-boot:run -q"

echo [*] 等待服务启动...
timeout /t 6 /nobreak

echo [*] 正在打开浏览器...
start http://localhost:8081
echo.
echo 提示: 关闭名为 'KimiAgent Server' 的窗口即可停止服务
