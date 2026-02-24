@echo off
chcp 65001 >nul
REM 推送到 GitHub 和 Gitee 双平台

echo ==========================================
echo   推送到 GitHub 和 Gitee
echo ==========================================
echo.

echo [1/2] 推送到 GitHub (origin)...
git push origin br_stream
if %errorlevel% neq 0 (
    echo [错误] GitHub 推送失败！
    pause
    exit /b 1
)
echo [完成] GitHub 推送成功
echo.

echo [2/2] 推送到 Gitee...
git push gitee br_stream
if %errorlevel% neq 0 (
    echo [错误] Gitee 推送失败！
    pause
    exit /b 1
)
echo [完成] Gitee 推送成功
echo.

echo ==========================================
echo   双平台推送完成！
echo ==========================================
echo.
echo GitHub: https://github.com/tanng-xw/ReActAgentDemo
echo Gitee:  https://gitee.com/tanngcloud9/react-agent-demo
pause
