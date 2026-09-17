---
name: mcp-service-ops
description: >-
  统一 MCP 本地服务（运行在 18765 端口）的健康探活、启停与运维排查技能。
  当需要启动统一 MCP 服务、排查 MCP 无法连接、检测 /health 探活状态或重启服务时调用。
---

# MCP-Service-Ops 统一 MCP 服务运维技能

## 架构与调用原则
`servertool` 模块提供统一的单端口 Streamable HTTP/SSE MCP 服务：
- **监听端口**：`127.0.0.1:18765`
- **协议入口**：`http://127.0.0.1:18765/mcp`
- **心跳健康检查**：`http://127.0.0.1:18765/health`
- **核心禁忌**：**严禁 AI 在后台自行用脚本拉起或杀死 MCP 服务进程**。MCP 服务由开发者在外部常驻保持，AI 直接作为 MCP 客户端发起工具调用。若连接异常，仅汇报探活状态并提示开发者。

## 运维管理命令

### 1. Windows 环境 (`scripts/mcp.bat`)
```cmd
# 启动 MCP 服务
scripts\mcp.bat start

# 查看服务状态与端口监听
scripts\mcp.bat status

# 停止服务
scripts\mcp.bat stop

# 重启服务
scripts\mcp.bat restart
```

### 2. Linux 环境 (`scripts/mcp.sh`)
```bash
# 启动
./scripts/mcp.sh start

# 检查状态
./scripts/mcp.sh status

# 停止
./scripts/mcp.sh stop
```

## 故障排查（Troubleshooting）
1. **端口被占用**：
   - 使用 `netstat -ano | findstr 18765` 查找占用该端口的 PID；
   - 若为旧残留进程，通过 `scripts/mcp.bat stop` 或 `taskkill /F /PID <pid>` 清理后重新启动。
2. **探活失败**：
   - 浏览器或终端执行 `curl -i http://127.0.0.1:18765/health`；
   - 正常响应应返回 HTTP 200 及内容 `OK`。
3. **日志位置**：
   - 查看 `servertool/logs/mcp/` 目录下的当日日志文件，检查连接接入与请求解析情况。
