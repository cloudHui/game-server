# 部署脚本

## 目录职责

- `ops.sh` / `ops.bat`：Linux / Windows 构建与服务管理入口。
- `nginx/`：`ops.sh nginx-apply` 使用的模板、安装器和部署说明，不可单独清理。
- `learning/`：离线学习数据导入工具，不参与服务启动。
- `validate-*.js`：各小游戏及静态资源的独立回归检查。
- `install-git-hooks.sh`：可选的本地 Git hook 安装器。
- `web-path.txt`：Web 服务启动和 Nginx 配置共同读取的访问路径配置。

Windows 推荐从仓库根目录执行 `deploy.bat`，或直接执行 `scripts\ops.bat`。

- `deploy.bat`：唯一的 Windows 部署入口，交互式更新、打包、启动、停止和状态查看
- `deploy.bat deploy`：拉取代码、打包、启动，并询问是否配置 Nginx
- `deploy.bat build`：只打包，不更新代码
- `deploy.bat start`：直接启动现有 `build` 产物
- `deploy.bat stop` / `status`：停止或查看服务
- 本地模式直接访问 `http://127.0.0.1:8081/`，不使用随机路径
- 域名模式会生成 Nginx 反代配置；DNS 解析仍需在域名服务商处完成

Linux 继续使用 `scripts/ops.sh`，两端都只认 `build/<服务>/` 作为运行目录。

Linux 可以单独打包、启停某个服务，例如：

- `./scripts/ops.sh build web`：只打包 web 及其必要依赖
- `./scripts/ops.sh start web`：只启动 web
- `./scripts/ops.sh build-restart web`：只打包并重启 web
- 服务参数支持 `center`、`gate`、`lobby`、`game`、`web` 和 `all`；省略时默认为 `all`
