# Nginx / Xray SNI 共存说明

普通部署：Java Web 只监听 `127.0.0.1:8081`，公网经 Nginx 80/443，路径为访问唯一码：

```text
https://你的域名/访问唯一码/
```

使用 `./scripts/ops.sh nginx-apply <域名>` 写入反代（见 `game-web.snippet.conf.in`）。

## 443 已被 Xray Reality 占用

一键安装检测到 443 被非 Nginx 进程占用时**不会自动改 Xray**，请人工配置 SNI 分流：

1. 备份 x-ui 数据库与现有 Nginx 配置
2. Xray 改为仅监听 `127.0.0.1:8443`
3. 网站 HTTPS 监听 `127.0.0.1:8444`（或你现有的本机 HTTPS 端口）
4. Nginx `stream` 在公网 443 按 SNI 分流：网站域名 → Nginx，其它 → Xray
5. 分别执行 `xray -test`（或面板检测）与 `nginx -t` 后再切换

示例文件：

- `family-site.conf.example`：HTTP/HTTPS 与反代示意（端口请改为 8081）
- `xray-sni-stream.conf.example`：stream SNI 分流示意

本仓库**不包含**自动修改 x-ui/Xray 的脚本，以免误伤面板与现网代理。
