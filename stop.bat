@echo off
chcp 65001
REM =====================================================
REM 智能音乐助手 - 停止脚本
REM =====================================================

echo.
echo ============================================
echo      智能音乐助手 - 停止程序
echo ============================================
echo.

set FOUND=0

REM 通过端口 8081 查找并停止进程
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8081"') do (
    if "%%P" NEQ "0" (
        if !FOUND! EQU 0 (
            echo [*] 找到服务进程，正在停止...
            set FOUND=1
        )
        echo [*] 停止进程 PID: %%P
        taskkill /F /PID %%P
        if !errorlevel! EQU 0 (
            echo [*] 进程 %%P 已停止
        ) else (
            echo [!] 停止进程 %%P 失败
        )
    )
)

echo.
if !FOUND! EQU 1 (
    echo [*] 服务已停止
    timeout /t 2 /nobreak
) else (
    echo [*] 未找到运行中的服务
)

echo.
