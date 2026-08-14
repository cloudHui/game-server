# Web 前端迁移到 Vue 3 —— 可执行实施计划（收敛版）

> 本文是**唯一执行入口**，替代旧 `tovue3.md`。目标读者是"接手执行的另一个 AI 或开发者"：按本文逐节执行即可，无需再读散落的旧方案。
> 本文不重复代码导航细节，需要定位具体函数/消息号/调用链时，查 `webreadme/01~05*.md`（已核实存在，作为只读参考保留）。

---

## 0. 结论与总路线（TL;DR）

**能做。** 本质是把 `web/src/main/resources/static/` 下"静态 HTML + 原生 JS + 局部引入 Vue 3"的现状，迁移为标准 Vue 3 工程（SFC + Vite + TypeScript + Router + Pinia）。

总路线一句话：**先打地基（脚手架 + 统一 URL/HTTP/WS 层 + 构建打包打通），再按"低风险页面 → 高风险页面"逐页迁移，新旧并行运行、每页可回滚，最后切默认入口、清理旧代码。** 后端 Java API、WebSocket 协议、游戏规则、数据格式**零改动**。

按阶段分 10 步执行（见 §5），每步有独立验收和回滚点，**不承诺一次性大版本完成**。

---

## 1. 代码基线事实（执行前必读，均已核实）

### 1.1 目录与规模

| 项 | 值 |
|---|---|
| 前端根 | `web/src/main/resources/static/` |
| HTML | 32 个 |
| JS | 138 个 |
| CSS | 39 个 |
| 总行数 | 约 3.4 万行 |
| 后端 | Spring Boot 2.5.1，Java 8，Maven 多模块（父 `com.cloud:Server:1.0-SNAPSHOT`） |
| 发布物 | `build/web/Web.jar`，由 `scripts/ops.sh` 管理启停 |
| 部署 | Nginx 反代；外网走 `/访问唯一码/` 动态前缀（`server.servlet.context-path` 非固定 `/`） |

### 1.2 页面清单（32 个入口，迁移清单表见 §6）

```
static/index.html                                  # 首页/登录入口
static/pages/admin/admin.html                      # 管理后台
static/pages/admin/replays.html                    # 回放列表
static/pages/admin/terminal.html                   # 终端(Shell)
static/pages/games/index.html                      # 游戏总入口
static/pages/games/{doudizhu,paodekuai,tractor,mahjong}/rooms.html   # 4 个房间列表
static/pages/games/{doudizhu,paodekuai,tractor,mahjong}/index.html   # 4 个牌桌
static/pages/learning/index.html                   # 学习中心（已用 Vue3）
static/pages/learning/admin.html                   # 学习管理（已用 Vue3）
static/pages/lobby/index.html                      # 大厅
static/pages/lobby/entertainment.html              # 娱乐大厅
static/pages/mini/index.html                       # 小游戏入口
static/pages/mini/{2048,chess,escape-run,gcompris,gomoku,kids-match,letter-fire,math-quest-maze,number-fire,phonics,phonics/phonicssound,tangram,tank}/index.html  # 13 个小游戏
static/pages/photos/index.html                     # 相册
```

### 1.3 后端 API / WebSocket / 认证（执行时逐页对照）

**HTTP 端点**（`web/src/main/java/web/controller/`）：

| Controller | 前缀 | 主要方法 |
|---|---|---|
| `AuthController` | `/api/auth` | `login`、`register` |
| `UserController` | `/api`（兼容旧路径） | `login`、`validate`、`logout` |
| `RoomController` | `/api` | `GET /rooms`、`POST /rooms/join`、`POST /rooms/create` |
| `AdminController` | `/api/admin` | invites/users/tables/robot-matches/replays/records/replays·detail·code/shell |
| `ReplayController` | `/api/replays` | 列表、`/code` |
| `CapabilitiesController` | `/api/capabilities` | 能力探测 |
| `learning/controller/*` | `/api/learning/*`、`/api/learning/auth/*` | 学习中心全部接口 |

**WebSocket 端点**（`web/config/WebSocketConfig.java`）：

| 端点 | Handler |
|---|---|
| `/ws/game` | `GameWebSocketHandler`（牌桌） |
| `/ws/mini` | `MiniGameWebSocketHandler`（联网小游戏） |

**认证机制**（`web/config/AuthInterceptor.java` + `web/identity/SessionResolver.java`）：
- 会话解析优先级：请求头 `X-Session-Token` → query 参数 `sessionId` → cookie `sessionId`。
- 未登录访问 `/api/**` 返回 `{"code":401,"msg":"请先登录"}`；访问页面 302 跳 `/`。
- 白名单见 `web/config/AppConfig.java:42`（`/`、`/index.html`、`/api/auth/**`、`/api/login`、`/api/capabilities`、`/ws/**` 等）。

### 1.4 动态 context-path 机制（迁移最关键，务必吃透）

`static/shared/app-base.js:122-138` 运行时逻辑：

```js
var parts = w.location.pathname.split('/').filter(Boolean);
var base = '';
if (parts.length && parts[0].indexOf('.') < 0) {   // 第一段不含 "." 即视为前缀
    base = '/' + parts[0];
}
w.APP_BASE = base;
w.appUrl = function (path) { ... return base + path; }  // 统一拼前缀
```

同时 `app-base.js` 还提供：`console` 脱敏、`AppDialog`（alert/confirm/prompt/form 浮层）、`AppErrorPrivacy`。这些公共能力在迁移期**必须原样保留等价实现**。

### 1.5 共享 JS 清单与职责（迁移时的复用/替换对象）

`static/shared/`：`app-base.js`(base/对话框/脱敏)、`room-page.js`(房间页)、`game-table.js`(牌桌壳)、`poker-view.js`(25KB 扑克视图)、`mahjong-tile.js`、`table-seat-view.js`、`card-pager.js`、`replay-model.js`、`replay-table-view.js`、`room-config.js`、`capabilities-poll.js`(轮询)、`canvas-buffer.js`、`game-landscape.js`、`mini-base.js`、`mini-*`(小游戏公共)、`app-quality.css`、`theme.css`、`poker-card.css`、`poker-table.css`、`mahjong-tile.css`、`game-table.css`、`game-landscape.css`、`room-page.css`、`audio/`。

各游戏目录另有 `xxx.js`(状态/规则)、`xxx-op.js`(操作)、`xxx-view.js`(视图)、`tractor-hand.js`/`tractor-settle.js`/`mahjong-exposed.js` 等。

### 1.6 学习中心现状（已用 Vue3，但非工程化）

- `pages/learning/js/vue.global.prod.js` 浏览器直载 Vue 3。
- `pages/learning/js/app.js` 用 `const {createApp} = Vue; createApp(LearningMerge({ data(){...}, methods:{...} }))`。
- `pages/learning/js/parts/{merge,core,chinese,math,olympiad,library,resources}.js` 是**全局合并对象**，非模块。
- 属 Options API + 运行时模板，**不是** SFC/Vite/TS 工程。

### 1.7 Worker / Service Worker / 特殊浏览器能力

| 文件 | 用途 |
|---|---|
| `pages/mini/escape-run/sw.js` | Service Worker |
| `pages/mini/chess/chess-ai-worker.js` | 象棋 AI Worker |
| `pages/mini/gomoku/gomoku-ai-worker.js` | 五子棋 AI Worker |

另有 Canvas 渲染、DOM 尺寸测量、横屏布局、语音、AudioContext、ResizeObserver 等（详见 `webreadme/03-timers-connections-rendering.md`）。

---

## 2. 目标形态

### 2.1 目录结构

```text
web/
├── frontend/                  # 新建：标准 Vue3 工程
│   ├── package.json
│   ├── package-lock.json
│   ├── .nvmrc                 # 固定 Node 主版本
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── eslint.config.js
│   ├── index.html
│   ├── public/                # 原样复制的静态资源（图片/音频，不参与 Vite 处理）
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── api/               # HTTP 客户端 + 各域 API 封装
│       ├── ws/                # WebSocket 客户端
│       ├── utils/             # resolveAppUrl 等 URL/基础工具
│       ├── assets/            # 经 Vite 处理的资源
│       ├── components/        # 通用组件（对话框、加载、错误、牌面等）
│       ├── composables/
│       ├── layouts/
│       ├── router/
│       ├── stores/            # Pinia
│       ├── types/
│       ├── views/             # 管理页/大厅/学习等
│       └── games/             # 游戏引擎（可先为 TS 模块，不必全 Vue 组件）
├── src/main/java/             # 后端，零改动
├── src/main/resources/static/ # 旧页面，迁移期保留
└── pom.xml                    # 接入 frontend 构建
```

**关键约束：Vite 不得直接覆盖 `src/main/resources/static`。** 构建输出到 `web/frontend/dist`，再由 Maven 复制到 JAR 的 `static/vue/`，避免旧资源丢失、Git 污染、无法回滚。

### 2.2 技术栈与版本

- Vue 3、Vite（当前稳定版）、TypeScript、Vue Router（**迁移期 Hash 模式**）、Pinia、Vitest、ESLint + 格式化。
- Node 用当前 LTS（如 20.x），写 `.nvmrc`，Maven 侧用 `frontend-maven-plugin` 固定下载，避免依赖机器全局 Node。
- 生产运行**不需要** Node，Node 仅构建期用。

---

## 3. 已收敛约定（决策与理由，无特殊理由不得改）

| # | 决策 | 理由 |
|---|---|---|
| 1 | **等价迁移，不做视觉重做** | 风险最低，可逐页与旧页对照验收；UI 重做另立需求 |
| 2 | **迁移期用 Hash 路由**（`/{ctx}/vue/#/lobby`） | 动态前缀下刷新不 404，不用改 Spring/Nginx 回退；稳定后再考虑 History 模式 |
| 3 | **Vite `base` 用相对路径 `'./'`，运行时前缀由 `window.__APP_BASE__` 注入** | 前缀无法构建期写死；所有 API/WS/资源/跳转统一走 `resolveAppUrl` |
| 4 | **产物放 `static/vue/`，旧 `static` 原样保留** | 新旧并行、可回滚；同名文件冲突要显式报错不静默覆盖 |
| 5 | **后端零改动** | 只改前端渲染；接口/协议/规则若需改，单独立项评审，不混入迁移 |
| 6 | **每个页面组一个里程碑，独立上线/回滚** | 不一次性大版本 |
| 7 | **学习中心是"工程化"，不是"重写"** | 已用 Vue3，把 Options API + parts 全局对象逐步转 SFC/模块/TS |
| 8 | **Canvas/Worker/游戏引擎先保留为 TS 模块，不强制全 Vue 组件** | 高频渲染不做深层响应式 |

---

## 4. 统一基础设施设计（阶段一核心，可直接照此写代码）

这是全项目的地基，必须先做稳、先测透。**所有页面只允许通过这三层访问网络/路径，禁止页面自行拼前缀、自行 new WebSocket。**

### 4.1 运行时 base 注入（等价于旧 `app-base.js`）

在 `frontend/index.html` 顶部内联一段**与 `app-base.js:122-138` 完全等价**的逻辑，产出 `window.__APP_BASE__`（含或不含前导 `/` 的规范形式）。随后 `utils/app-url.ts` 提供：

```ts
// 契约（签名级，实现照此做）
export function resolveAppUrl(path: string): string;   // base + '/' + path，去重斜杠
export function resolveWsUrl(path: string): string;    // (https?'wss':'ws') + '//' + host + base + path
```

必须覆盖的用例：根路径部署、`/{前缀}` 部署、`path` 为空/含前导 `/`/多层、base 为空。**写单元测试**（§8）。

### 4.2 HTTP 客户端 `api/http.ts`

统一封装 `fetch`，契约：
- 自动拼 `resolveAppUrl`；`credentials: 'include'`。
- sessionId 来源与旧版一致（cookie 或 query，逐页迁移前先记录该页用的是哪种，见 §6 清单）。
- JSON 编解码、超时（默认值可配）、请求取消（AbortController）。
- 业务判定：**不以 HTTP 状态为准**，以响应体 `code` 字段为准（旧版错误码/提示文案保持）。
- `code === 401` 或 HTTP 401 → 清会话 + 跳登录入口。
- 统一错误提示（复用旧 `AppDialog` 的等价实现）。
- 网络异常与业务异常分离。

### 4.3 WebSocket 客户端 `ws/ws-client.ts`

统一封装，契约：
- `resolveWsUrl` 自动 `ws:`/`wss:`。
- 连接状态机：`connecting → open → closing → closed`。
- 心跳、断线指数退避重连、主动退出与意外断开区分。
- 消息解析 + 运行时校验（`/ws/game` 消息类型见 `webreadme/04-gate-message-boundary.md`）。
- 组件 `onBeforeUnmount` 必须释放连接、定时器、监听器。

### 4.4 Vite 开发代理

`vite.config.ts` 将 `/api`、`/ws` 代理到 `http://127.0.0.1:8081`；WS 代理开 `ws: true`。注意：后端若以动态前缀启动，代理必须带同一前缀（或开发期统一用根 context-path，但**不能因开发正常就跳过前缀部署测试**）。

### 4.5 旧公共能力等价层

把 `app-base.js` 里的 **AppDialog（alert/confirm/prompt/form）、console 脱敏、AppErrorPrivacy** 在 `src/components/` 与 `src/utils/` 里做成等价实现，迁移期所有页面复用，避免旧页面的交互手感退化。

---

## 5. 分阶段执行步骤

> 每阶段 = 目标 + 具体操作 + 命令 + 验收。**上一阶段验收通过才进下一阶段。**

### 阶段 0：冻结基线与建立清单

**操作**
1. `git tag vue3-base-$(date)` 打基线标签（用当时日期，不用占位符）。
2. 用 §1.2 页面清单 + §6 迁移表，逐页登记：入口 HTML、引用的 JS/CSS/图片/音频/Worker/SW、对应 API、对应 WS、sessionId 传参方式、localStorage/sessionStorage/cookie、跳转地址、移动/横屏/全屏行为、是否依赖 Canvas/Worker/语音。
3. 截图 + 录屏关键页面与操作，存 `webreadme/baseline/`（新建）。
4. 记录 `/ws/game`、`/ws/mini` 典型消息样本（连接/准备/开局/操作/结算/重连/异常）。
5. 跑一遍现有构建 `mvn clean install -DskipTests`，确认 `build/web/Web.jar` 可产出并记录校验值。

**验收**：所有入口进清单；每个联网页面能对应到 API/WS；能从基线提交重新构建旧 JAR。

### 阶段 1：建立标准 Vue3 工程 + 统一基础设施（**本阶段是地基，投入要足**）

**操作**
1. 在 `web/frontend/` 初始化 Vue3 + Vite + TS 工程，配 Router（Hash）+ Pinia + Vitest + ESLint。
2. 加 `.nvmrc`，提交 `package-lock.json`。
3. 落地 §4 全部内容：`index.html` 的 base 注入、`utils/app-url.ts`、`api/http.ts`、`ws/ws-client.ts`、AppDialog/脱敏等价层、Vite 代理。
4. 脚本命令齐备：`npm run dev / build / test / lint / type-check`。
5. 建统一错误页、加载态、全局异常捕获。
6. 空壳 Vue 应用挂载在 `/{ctx}/vue/#/` 下，**不替换旧首页**。
7. 改 `web/pom.xml`：接入 `frontend-maven-plugin`（固定 Node/npm + `npm ci` + `npm run build`），再用 `maven-resources-plugin` 把 `frontend/dist` 复制到 `${project.build.outputDirectory}/static/vue`；旧 `static` 与 `static/vue` 合并进 `target/classes/static`，同名文件显式报错。

**命令**

```bash
cd web/frontend && npm ci && npm run type-check && npm run test && npm run build
cd /home/ec2-user/repos/Server && mvn clean install -DskipTests
jar tf build/web/Web.jar | grep 'BOOT-INF/classes/static/vue/'
```

**验收**（逐条过，缺一不进下一阶段）
- Vite 开发服务器能起，空壳能挂载。
- 构建产物在**根路径**和**非根 context-path**下资源全部 200、无 404。
- 刷新 Vue 子路由（Hash）不 404。
- 旧页面完全不受影响。
- API 成功/业务失败/超时/401、WS 连接/关闭/断线/重连，各验一遍（含动态前缀）。

### 阶段 2：迁移登录、注册、站点入口

**操作**
1. 拆登录/注册/邀请码/自动登录为独立组件；会话集中到 `stores/auth`，明确 token/sessionId 来源与生命周期。
2. 接口保持 `/api/auth/login`、`/api/auth/register` 不变；错误码与提示文案与旧版一致。
3. 加重复提交保护、请求中态、输入校验。
4. 旧首页保留，新版走 `/{ctx}/vue/#/`，验收后再切默认入口。

**验收**：密码/token 登录、注册、退出正常；刷新后会话与旧版一致；登录失败不留错误会话；手机端输入/键盘正常；新旧页互跳会话不丢。

### 阶段 3：迁移大厅与房间页

**操作**
1. 导航、用户信息、能力检测、游戏入口拆组件；房间查询/创建/加入/退出封装进 Store/服务层（接口契约 `/api/rooms` 等不变）。
2. 迁移 `capabilities-poll.js` 轮询，页面隐藏/卸载时**暂停**（避免重复定时器）。
3. 房间创建/加入加幂等保护，防双击重复请求；牌桌参数显式类型定义+校验。

**验收**：房间刷新/创建/加入/退出正常；后端不可用时降级态与现状一致；无重复轮询；返回/刷新/前后台切换不重复进房。

### 阶段 4：迁移管理后台与回放页

**操作**
1. 先迁纯表格/表单页（用户、邀请码、牌桌、记录、回放），分页/筛选/加载/空态/错误统一封装。
2. 高危操作保留确认 + 服务端权限校验。
3. 终端页（`terminal.html`/shell）**单独上线**；输出必须 `textContent` 显示，**禁止**把不可信内容写入 `innerHTML`（防 XSS）。

**验收**：权限隔离不变；表格/详情与旧版一致；回放与终端无 XSS；删除/禁用/撤销不重复提交。

### 阶段 5：学习中心工程化（重点工程化，不重写）

**操作**
1. 把 `createApp(LearningMerge(...))` 的 Options API 代码逐个移入 `.vue` SFC。
2. 按语文/数学/奥数/资源库/管理拆分组件与路由。
3. 把 `parts/*.js` 全局合并对象转成显式模块依赖（ESM import/export）。
4. TS 逐步加类型，不要求一步最严格；CSS 先保视觉一致再清理。
5. `vue.global.prod.js` 引用**在该页完全切换后才删**。

**重点风险**：运行时模板 vs SFC 作用域差异；`this` 来源混杂漏方法/状态；深响应式大列表性能；Vite 后文件/音频/图片 URL 变化；`SecurityHeadersFilter` 对学习中心的特殊响应头不能无意改变。

### 阶段 6：迁移纯本地休闲小游戏

**方法**：Vue 管页面壳/菜单/弹窗/设置/生命周期，游戏引擎管高频计算与 Canvas 渲染；**不要**把每帧棋子坐标/动画对象做成深层响应式。

**操作**
1. 先迁不联网、无 SW 的：2048、tangram、letter-fire、number-fire、phonics、math-quest-maze、kids-match、gcompris 等。
2. 现有脚本整理成可导入模块，明确 `mount/start/pause/resize/destroy` 生命周期。
3. Vue `onMounted` 建引擎、`onBeforeUnmount` 清理：`requestAnimationFrame`、定时器、DOM 监听、ResizeObserver、Worker、AudioContext、WebSocket。
4. Worker 用 Vite 支持的 `new Worker(new URL(...), {type:'module'})`，不依赖旧相对路径。
5. SW（`escape-run/sw.js`）单独定 scope 与缓存版本；动态前缀下 scope 不可靠就延续禁用/降级。

**验收**：连续进出页面不增监听器/Worker/定时器；切后台暂停高耗循环；画布桌面/手机/横屏/高 DPI 尺寸正确；音频在手势后能恢复；游戏规则/AI 结果不变。

### 阶段 7：联网小游戏

**操作**：五子棋/象棋等接入 `/ws/mini`；先复用阶段 6 的引擎生命周期，再套 §4.3 的 WS 客户端与状态机。

### 阶段 8：迁移棋牌牌桌（斗地主/跑得快/拖拉机）

**推荐分层（强制，禁止 WS 回调直接改 DOM）**

```text
WebSocket 原始消息 → 协议适配 + 运行时校验 → 游戏状态机/Store → 计算属性/视图模型 → Vue 组件/牌面/Canvas
```

**每种游戏迁移步骤**
1. 冻结消息类型/字段/时序（对照 `webreadme/04` 与旧 `xxx-op.js`）。
2. 建 TS 类型 + 运行时保护。
3. 把规则/排序/牌型计算抽成纯函数并补单测。
4. 明确牌桌状态机：未连接/连接中/等待/准备/游戏中/结算/断线/重连中/失败。
5. 先用新状态层驱动旧视图或调试面板，核对消息状态一致。
6. 再迁玩家区/手牌区/公共牌区/操作区/倒计时/提示/结算弹窗。
7. 迁横屏/自适应/触摸选择/动画。
8. 加断线重连 + 快照恢复测试。
9. 与旧页同局/同回放对照。
10. 单个游戏完成即独立上线。

**扑克注意**：手牌排序/选牌状态/后端牌 ID 三者分离，视觉排序不得改变提交顺序或牌值；快速连点/倒计时/推送竞态；动画结束不作业务状态唯一触发；结算/下一局/准备消息由状态机处理。

### 阶段 9：迁移麻将（最复杂，放最后）

**注意**：手牌/摸牌/弃牌/副露/花牌用稳定唯一 ID 作 Vue `key`，**禁止数组下标**；吃碰杠胡保留服务端操作标识；补花/抢杠/海底等特殊时序用真实消息样本回归；断线恢复**以服务端快照为准**；动画队列与业务状态分离，不用异步动画阻塞消息。

### 阶段 10：切换默认入口 + 清理

**操作**
1. 核心链路全过验收后，`/` 与主导航指向 Vue 页。
2. 旧页改到明确兼容入口，**保留至少一个发布周期**。
3. 监控前端异常/API 失败/WS 断开/登录失败/资源 404。
4. 稳定后再删旧入口、旧 JS/CSS、`vue.global.prod.js`；删前先做静态资源引用检查，避免误删共享图片/音频/素材。

---

## 6. 页面迁移清单表（执行时逐行填状态）

| 页面 | 阶段 | 复杂度 | 关键依赖/风险 | 状态 |
|---|---|---|---|---|
| `index.html` 首页/登录 | 2 | 中 | `/api/auth/*`、会话生命周期 | ⬜ |
| `lobby/index.html`、`entertainment.html` | 3 | 中 | `/api/rooms`、`capabilities-poll` | ⬜ |
| `games/index.html` | 3 | 低 | 纯导航 | ⬜ |
| `admin/admin.html`、`replays.html` | 4 | 中 | `/api/admin/*`、`/api/replays` | ⬜ |
| `admin/terminal.html` | 4 | 中 | shell、**XSS 高危** | ⬜ |
| `learning/index.html`、`admin.html` | 5 | 中大 | 已用 Vue3，工程化改造 | ⬜ |
| `mini/*`（本地小游戏 13 个） | 6 | 小～中 | Canvas/Worker/音频/SW | ⬜ |
| `mini/chess`、`gomoku`（联网） | 7 | 中 | `/ws/mini`、AI Worker | ⬜ |
| `games/{doudizhu,paodekuai,tractor}/*` | 8 | 中大 | `/ws/game`、状态机、竞态 | ⬜ |
| `games/mahjong/*` | 9 | 大 | `/ws/game`、牌唯一 ID、特殊时序 | ⬜ |
| `photos/index.html` | 3 或 4 | 低 | `/api` 相册接口 | ⬜ |

---

## 7. 关键风险与规避（对照代码的具体点）

1. **动态 context-path 资源 404**（`app-base.js` 逻辑与 Vite 构建期 base 冲突）→ 统一 `resolveAppUrl`，`base:'./'`，随机前缀下完整浏览器测试，禁止写死 `/assets/...`。
2. **SPA 刷新 404** → 迁移期 Hash 路由；History 模式需精确回退并排除 `/api/**`、`/ws/**`、静态文件。
3. **API 前缀重复/漏前缀**（`/{p}/{p}/api` 或 `/api`）→ 只有 `api/http.ts` 一处拼前缀，禁止组件自行拼；URL 单测覆盖。
4. **HTTPS 下 WS 失败**（mixed content/握手失败）→ `wss:` 自动选择；查 context-path 与 Nginx Upgrade 头。
5. **实时消息竞态**（重复出牌/按钮残留/结算被覆盖）→ 状态机 + 消息号/局号保护；业务态与动画态分离。
6. **Vue 与旧脚本同控 DOM** → 按区域定所有权；旧引擎只操作专属容器；迁移后移除旧监听。
7. **内存泄漏/重复连接**（多次进出页面消息重复/CPU 高/声音叠）→ `onBeforeUnmount` 统一清理连接/Worker/RAF/观察器/事件/定时器；加反复进出测试。
8. **Vite 后资源路径变化**（开发正常、JAR 中 404）→ 资源按 Vite 规则导入；生产验证用构建产物。
9. **缓存新旧不匹配**（新 HTML 载旧 JS 白屏）→ JS/CSS 内容哈希；HTML 短缓存；保留旧哈希资源一个周期；谨慎管理 SW 缓存。
10. **TS 一次性过重** → 基础层严格类型；旧游戏模块分阶段；先给 API/WS/Store 边界建类型。
11. **性能下降**（出牌/动画卡顿，移动端更明显）→ 高频图形态用 `shallowRef` 或引擎内部对象；减大列表重渲染；真机测帧率/内存。
12. **XSS**（终端/回放用 `v-html` 或 `innerHTML`）→ 默认文本渲染；确需 HTML 先可信清洗；token 不落 URL/日志。
13. **npm 供应链/构建环境** → 提交锁文件、`npm ci`、固定 Node、审计依赖、CI 出 JAR；生产不在启动时下载依赖。

---

## 8. 测试方案

- **单元**：URL/context-path、API 响应转换、WS 消息解析、牌型/排序/操作判断、Store 状态迁移、重连退避与定时器清理。
- **组件**：登录/注册表单、房间列表与创建弹窗、操作按钮显隐、结算弹窗、后台分页与错误态。
- **E2E**：注册/登录→大厅→房间→准备→开局→操作→结算→下一局；刷新/返回/断网/恢复/重复进入；管理员关键查询；学习中心登录/加载/记录保存；**根路径与动态前缀各跑一遍**。
- **人工兼容**：Chrome/Edge/Safari + 目标移动浏览器；桌面/竖屏/横屏；触摸/鼠标/键盘；弱网/高延迟/断网；HTTP 本地 + HTTPS 生产。

---

## 9. 完成标准与回滚

**某页迁移完成 = 同时满足**：功能与旧版一致（有意变化已记录）；API/WS 契约未意外改；根路径+动态前缀都过；刷新/前进后退/直达正常；无控制台错误与 404；无重复监听/连接/泄漏；自动化测试过；关键移动场景过；Maven 能产出含该页的 `build/web/Web.jar`；回滚入口或旧 JAR 已说明；发布后核验了 Java 进程/端口/日志/静态资源/API/WS。

**回滚**：页面级 = 导航指回旧 HTML；发布级 = 恢复上个已验证 `Web.jar`。**禁止**上线后临时改 JAR 内静态文件。

---

## 10. 执行检查清单（给执行 AI 的 Do / Don't）

**Do**
- 每页迁移前先登记 §6 表（含 sessionId 传参方式、跳转地址、缓存键）。
- 只改前端，后端接口/协议/规则保持零改动；发现接口不适合前端 → 单独立项，不混入。
- 网络/路径只走 `resolveAppUrl`/`http`/`ws-client` 三层。
- 每个阶段单独构建、测试、上线、回滚。

**Don't**
- 不要一次性迁移全部页面；不要 Vite 直接覆盖旧 `static`；不要写死线上域名/前缀/token；不要 `v-html` 渲染不可信内容；不要在生产启动阶段下载 npm 依赖。

---

## 11. 工作量参照

等价迁移（不重做 UI）：工程/构建/URL/API/WS 基础层 = 中；登录/大厅/后台 = 中；学习中心工程化 = 中偏大；纯本地小游戏 = 每个小～中；联网棋牌 = 每个中～大；麻将 = 大；全量回归/灰度/部署/清理 = 中偏大。建议按"1 个基础设施里程碑 + 每个页面组 1 个里程碑"排期。
