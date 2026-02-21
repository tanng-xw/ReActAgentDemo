@echo off
chcp 65001 >nul
REM =====================================================
REM 自动提交脚本
REM 检查变更并自动提交到 GIT
REM =====================================================

echo.
echo [GIT] 检查文件变更...

REM 检查是否有变更
git diff --quiet --exit-code
if %errorlevel% equ 0 (
    git diff --cached --quiet --exit-code
    if %errorlevel% equ 0 (
        echo [GIT] 没有检测到变更，跳过提交
        exit /b 0
    )
)

REM 添加所有变更
echo [GIT] 添加变更文件...
git add -A

REM 生成提交信息（带时间戳）
set TIMESTAMP=%date:~0,4%-%date:~5,2%-%date:~8,2% %time:~0,2%:%time:~3,2%:%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

echo [GIT] 创建提交...
git commit -m "Auto commit: %TIMESTAMP%

- 自动提交文件变更
- 详见 git diff"