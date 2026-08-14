# Claude Code + DeepSeek

用于 Linux 服务器的一键安装和配置脚本。它会安装 Claude Code、提示输入 DeepSeek API Key、读取官方模型列表并配置 Anthropic 兼容接口。真实 Key 不写入仓库。

## 一键安装

在仓库根目录运行：

```bash
./scripts/claude-deepseek/claude-deepseek.sh setup
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
4. 明文提示输入 DeepSeek API Key。
5. 通过 `https://api.deepseek.com/models` 验证 Key。
6. 显示官方可用模型并让用户选择。
7. 将配置保存到 `~/.config/claude-deepseek/config.env`。
8. 在 `~/.bashrc` 添加幂等的配置加载入口。

私有配置权限为 `600`，更新前的备份保存在：

```text
~/.config/claude-deepseek/backups/
```

## 检查和替换模型

```bash
./scripts/claude-deepseek/claude-deepseek.sh model
```

脚本会显示当前模型和 DeepSeek 官方模型列表。默认不替换；选择新模型后还会要求再次确认。

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
