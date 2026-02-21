# Git 仓库配置说明

## 仓库信息

- **仓库路径**: `C:/Code/KimiMusicAgent`
- **当前分支**: `master`
- **提交历史**: 使用 `git log` 查看

## 自动提交机制

每次文件修改后，系统会自动执行：
```bash
git add -A
git commit -m "提交信息"
```

## 常用命令

```bash
# 查看提交历史
git log --oneline

# 查看当前状态
git status

# 手动提交
git add -A
git commit -m "提交信息"

# 查看变更
git diff
```

## 提交记录

- `721f7e8` - Initial commit: 项目初始化
- `0dbaa08` - Add auto-commit script: 添加自动提交脚本
- `2b98e0c` - Update README.md: 修复版本号
