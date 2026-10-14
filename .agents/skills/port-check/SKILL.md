---
name: port-check
description: >-
  Windows 查端口被哪个进程占用，并展示关闭命令。
  当用户问端口被谁占了、怎么关占用进程、8081 或 18765 端口冲突时使用。
---

# Windows 端口占用排查技能

## 何时使用
- 启动 Hub 服务提示 8081 端口被占用；
- 启动 MCP 服务提示 18765 端口冲突；
- 用户明确询问某个端口（如 3306、6379）被哪个程序占用了、怎么杀掉进程。

## 排查步骤（Windows 环境）

### 步骤 1：查询占用端口的 PID
```cmd
netstat -ano | findstr :<PORT>
```
示例：
```cmd
netstat -ano | findstr :8081
netstat -ano | findstr :18765
```
寻找处于 `LISTENING` 状态行的最后一列数字，即为目标 **PID**。

### 步骤 2：由 PID 获取进程名与详细信息
```cmd
tasklist /FI "PID eq <PID>"
```
或通过 PowerShell 获取：
```powershell
Get-Process -Id <PID> | Select-Object Id, ProcessName, Path
```

### 步骤 3：出具处理建议与关闭命令
向用户如实汇报进程名与 PID。若用户需要释放端口，提供关闭命令：

**按 PID 强制终止：**
```cmd
taskkill /PID <PID> /F
```

**按进程名强制终止：**
```cmd
taskkill /IM <ProcessName.exe> /F
```

## 注意事项
1. **绝不擅自强杀**：未向用户汇报并取得确认前，不要擅自执行 `taskkill /F`；
2. **IDE 进程优先**：若占用进程来自 IDE（如 IDEA、VS Code）拉起的 Java 进程，优先提示在 IDE 内优雅停止；
3. **查无结果处理**：若未发现 `LISTENING`，检查是否绑定到了 IPv6 (`[::]:PORT`) 或被系统保留端口段占用。
