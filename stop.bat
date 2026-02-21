@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
REM =====================================================
REM 智能音乐助手 - 停止脚本
REM 用于停止运行在 8081 端口的 Kimi Agent 服务
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 停止程序
echo ============================================
echo.

echo 正在查找运行中的服务...
echo.

set FOUND=0
set PIDS=

REM 通过端口 8081 查找所有相关进程
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8081"') do (
    set CURRENT_PID=%%P
    REM 检查是否已添加过该 PID（去重）
    echo !PIDS! | findstr "!CURRENT_PID!" >nul
    if errorlevel 1 (
        if "!CURRENT_PID!" NEQ "0" (
            if !FOUND! EQU 0 (
                echo 找到服务进程，正在停止...
                set FOUND=1
            )
            set PIDS=!PIDS! !CURRENT_PID!
            echo 正在停止 PID: !CURRENT_PID!
            taskkill /F /PID !CURRENT_PID! >nul 2>&1
            if !errorlevel! EQU 0 (
                echo [成功] 进程 !CURRENT_PID! 已停止
            ) else (
                echo [警告] 停止进程 !CURRENT_PID! 失败，可能已停止
            )
        )
    )
)

echo.
if !FOUND! EQU 1 (
    echo [成功] 所有相关进程已停止
    echo 等待进程完全退出...
    timeout /t 2 /nobreak >nul
) else (
    echo [信息] 未找到运行中的服务
    echo.
    echo 提示：如果服务确实在运行但无法停止，请尝试：
    echo   1. 检查是否有其他程序占用 8081 端口
    echo   2. 使用任务管理器手动结束 java.exe 进程
)

echo.
echo 按任意键退出...
pause >nul
exit /b 0
