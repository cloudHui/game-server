---
name: find-skills
description: >-
  通过 skills.sh 生态检索并推荐/安装 Agent 技能。
  当用户询问“有没有某种技能”、“如何做某类专门任务”、“查找扩展技能”时使用。
---

# Find Skills 技能发现与生态检索

帮助用户从开源 Agent 技能生态（`skills.sh`）中发现、验证并安装经过社区检验的高质量技能。

## 何时使用
- 用户询问“有没有做 X 的技能”、“能不能找个代码检查/前端/测试技能”；
- 用户需要引入社区现成的最佳实践（如 React 优化、Docker 部署、Git 自动化）。

## 核心机制与命令
基于 Skills CLI (`npx skills`)：
- **搜索技能**：`npx skills find [query]`
- **安装技能**：`npx skills add <package>` (加 `-g` 为全局安装，`-y` 自动确认)
- **检查更新**：`npx skills check`
- **更新全部**：`npx skills update`

## 推荐与安装原则
1. **优先高安装量**：优先选择经过广泛验证的包（>1K 安装量，如 `anthropics/skills`、`vercel-labs/agent-skills`）；
2. **审查必要性**：向用户说明技能用途与安装命令后，征得确认再执行；
3. **生态官网**：可直接查阅 [skills.sh](https://skills.sh/) 寻找热门排行。
