@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
REM =====================================================
REM 智能音乐助手 - 停止脚本
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 停止程序
echo ============================================
echo.

echo 正在查找运行中的服务...
echo.

set FOUND=0

REM 通过端口 8081 查找进程
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8081"') do (
    if "%%P" NEQ "0" (
        if !FOUND! EQU 0 (
            echo 找到服务进程，正在停止...
            set FOUND=1
        )
        echo 正在停止 PID: %%P
        taskkill /F /PID %%P >nul 2>&1
        if !errorlevel! EQU 0 (
            echo [成功] 进程 %%P 已停止
        ) else (
            echo [警告] 停止进程 %%P 失败
        )
    )
)

echo.
if !FOUND! EQU 1 (
    echo [成功] 服务已停止
    timeout /t 2 /nobreak >nul
) else (
    echo [信息] 未找到运行中的服务
)

echo.
pause
exit /b 0
