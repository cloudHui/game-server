# Claude Code + DeepSeek

用于 Linux 服务器的一键安装和配置脚本。它会安装 Claude Code、配置 DeepSeek Anthropic 兼容接口，并为 Agent、Codex、Claude 安装共享工程技能。真实 Key 不写入仓库。

## 一条命令安装

```bash
curl -fsSL https://raw.githubusercontent.com/cloudHui/game-server/main/scripts/claude-deepseek/install.sh | bash
```

安装中会等待输入 DeepSeek API Key。直接回车可跳过，之后补充：

```bash
claude-deepseek setup-key
```

入口只负责下载；Claude、DeepSeek、技能分别由仓库内模块处理。

## Clone 后安装

在仓库根目录运行：

```bash
./scripts/claude-deepseek/install.sh
source ~/.bashrc
claude
```

如果脚本还没有执行权限：

```bash
chmod +x scripts/claude-deepseek/claude-deepseek.sh
```

安装过程会：

1. 检查 `curl` 和 `jq`，缺少时询问是否安装。
2. 从 Anthropic 官方地址安装 Claude Code。
3. 已存在全局 npm 版本时，询问是否迁移到用户级原生版本。
4. 安装 `/grill-with-docs`、`/caveman`、`/diagnose`、`/tdd`。
5. 将技能统一存到 `~/.agents/skills`，并链接到 Codex 和 Claude 目录。
6. 向 `~/AGENTS.md` 和 `~/CLAUDE.md` 幂等追加必用规则。
7. 隐藏输入 DeepSeek API Key，或回车跳过。
8. 通过 `https://api.deepseek.com/models` 验证 Key并选择模型。
9. 将私有配置保存到 `~/.config/claude-deepseek/config.env`。
10. 在 `~/.bashrc` 添加幂等的配置加载入口。

私有配置权限为 `600`，更新前的备份保存在：

```text
~/.config/claude-deepseek/backups/
```

## 检查和替换模型

```bash
./scripts/claude-deepseek/claude-deepseek.sh model
```

脚本会显示当前模型和 DeepSeek 官方模型列表。默认不替换；选择新模型后还会要求再次确认。

## 只安装或更新共享技能

不安装 Claude、不配置 DeepSeek 时，可单独运行：

```bash
./scripts/claude-deepseek/install-matt-skills.sh
```

上游当前已经将 `diagnose` 政名为 `diagnosing-bugs`，并删除了 `caveman`。本脚本把最新版 `diagnosing-bugs` 适配为 `/diagnose`，同时将 `caveman` 固定到上游删除前最后版本；另外两个技能跟随上游 `main`。

技能触发规则：

- 想需求、对齐思路：必须用 `/grill-with-docs`
- 准备动手：必须用 `/caveman`
- 复杂 Bug：必须用 `/diagnose`
- 核心纯函数或底层业务逻辑：必须用 `/tdd`

## 手动更新 Claude Code

配置默认关闭自动更新，需要时运行：

```bash
./scripts/claude-deepseek/claude-deepseek.sh update-cli
```

## 诊断

```bash
./scripts/claude-deepseek/claude-deepseek.sh doctor
```

诊断会检查 CLI 版本、配置、DeepSeek 鉴权和模型列表，不会显示 API Key。

## 非交互安装

```bash
DEEPSEEK_API_KEY=your-key \
DEEPSEEK_MODEL=deepseek-v4-pro \
./scripts/claude-deepseek/claude-deepseek.sh setup
```

不要把包含真实 Key 的命令保存到仓库、Shell 历史或部署日志。
