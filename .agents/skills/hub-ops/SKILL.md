---
name: hub-ops
description: >-
  一体化 Hub 核心服务的编译、打包、部署与启停运维技能。
  当用户提出“编译项目”、“打包”、“启动服务”、“重启”、“检查 Hub 运行状态”时调用。
---

# Hub-Ops 服务构建与运维技能

## 何时使用
- 业务代码修改完毕，需要编译并启动 Hub 服务进行自测；
- 服务器发布上线，需要打包构建最终 jar；
- 排查 8081 端口占用、查看 Spring Boot 启动报错与运行日志。

## 构建与运维命令

### 1. Maven 多模块构建
在工程根目录下执行：
```bash
# 仅编译，跳过测试
mvn clean compile -DskipTests

# 全量打包 (产物位于 build/hub/hub.jar)
mvn clean package -DskipTests
```

### 2. 本地/测试服启停 (Windows)
- **一键打包并启动**：直接运行根目录下的 `deploy.bat`
- **手动启动已打好的 jar**：
  ```cmd
  java -Dfile.encoding=UTF-8 -jar build\hub\hub.jar
  ```
- **配置覆盖**：可通过 JVM 参数或环境变量覆盖配置，例如指定端口：
  ```cmd
  java -Dserver.port=18081 -jar build\hub\hub.jar
  ```

### 3. Linux 服务器启停 (`scripts/hub.sh`)
```bash
# 打包并启动
./scripts/hub.sh deploy

# 启动 (已有 hub.jar 时)
./scripts/hub.sh start

# 检查运行状态 (PID, 端口, 资源占用)
./scripts/hub.sh status

# 停止服务
./scripts/hub.sh stop

# 重启服务
./scripts/hub.sh restart
```

## 运行状态与健康确认
1. **进程检查**：确认 Java 进程是否监听默认的 `8081` 端口；
2. **Web 访问验证**：
   - 访问地址：`http://127.0.0.1:8081/<scripts/web-path.txt 里的访问唯一码>/`；
   - 默认管理员账号：`admin` / `admin123`；
3. **日志排查**：
   - 检查控制台或 `hub/logs/` 中的日志，确认 Netty Gateway 与 WebSocket 端口成功绑定，无 `Address already in use` 异常。
