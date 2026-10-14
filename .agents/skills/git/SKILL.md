---
name: git
description: >-
  通过统一 Git MCP 工具只读检索项目代码仓库的提交历史、分支状态与代码变更 Diff。
  当用户要查 git 提交记录、日期区间 log、作者过滤、commit diff、代码改动审计时使用。
---

# Git MCP 只读审查与追溯技能

## 何时使用
- 审查特定历史提交改了哪些类、方法与配置文件；
- 按日期范围或作者排查某特性的合入历史；
- 查看工作区当前未提交的变更状态；
- 为日报、周报或代码重构审查提供客观的历史依据。

## 常用工具清单

| MCP 工具 | 核心参数 | 用途说明 |
|----------|----------|----------|
| `git_log` | `since`, `until`, `author`, `noMerges`, `maxCount` | 日期区间与作者维度的提交历史查询 |
| `git_commit_files` | `commit` (hash) | 列出某次提交修改的所有文件路径及变更状态 (A/M/D) |
| `git_show` | `commit`, `statOnly` (布尔) | 查看某次提交的详细 Diff 或统计信息 |
| `git_run` | `args`: string[] (如 `["status", "-sb"]`, `["branch", "-a"]`) | 受控执行基础只读 Git 命令 |

## 安全与使用准则
1. **只读安全**：本技能主要用于分析追溯与审计，严禁擅自执行破坏性强制重置（如 `reset --hard`）或清理操作；
2. **提交号自动解析**：查作者时优先通过 `git_run` 读取 `["config", "user.name"]`；
3. **关联技能**：
   - 提取成果用于日常汇报 -> 联动 [`daily-summary`](file:///d:/code/st/game-server/.agents/skills/daily-summary/SKILL.md) / [`weekly-report`](file:///d:/code/st/game-server/.agents/skills/weekly-report/SKILL.md)；
   - 代码安全与规范审查 -> 联动 [`code-review`](file:///d:/code/st/game-server/.agents/skills/code-review/SKILL.md)。
