# Web 页面迁移到标准 Vue 3 的完整计划

## 1. 文档目标

本文用于指导 `web` 模块从当前“静态 HTML + 原生 JavaScript + 局部直接引入 Vue 3”的形式，逐步迁移为标准的 Vue 3 工程，并说明迁移后的开发、测试、Maven 打包、部署、启动、验证和回滚方式。

本次迁移只调整 Web 前端实现方式，原则上不修改现有 Java API、WebSocket 地址、消息协议、游戏规则和数据存储格式。若迁移过程中发现接口本身不适合前端使用，应单独评审，不能把接口变更混在页面迁移中直接上线。

## 2. 当前项目情况

### 2.1 后端和部署方式

- 项目是 Maven 多模块工程，Web 模块位于 `web/`。
- Web 后端使用 Spring Boot 2.5.1，当前 Java 编译目标为 Java 8。
- `web/src/main/resources/static/` 中的文件随 Spring Boot 一起打入 JAR。
- Maven 构建后的 Web 产物为 `build/web/Web.jar`。
- Web 默认监听 `8081`，实际部署通过 `scripts/ops.sh` 启动。
- 线上可能使用动态访问前缀，即 Spring Boot 的 `server.servlet.context-path` 不是固定的 `/`。
- Nginx 将包含访问前缀的 HTTP 和 WebSocket 请求反向代理到 Web 服务。

### 2.2 当前前端情况

当前静态目录大约包含：

- 30 个 HTML 页面；
- 107 个 JavaScript 文件；
- 19 个 CSS 文件；
- 约 3 万行 HTML、JavaScript 和 CSS；
- 登录、大厅、后台、学习中心、棋牌游戏、休闲小游戏等多个相对独立的页面组。

其中学习中心已经通过 `vue.global.prod.js` 使用 Vue 3，但它仍是浏览器直接加载 Vue 和普通 JavaScript 文件，不是基于 npm、Vite、单文件组件和模块化构建的标准 Vue 工程。

现有页面还包含以下重要依赖：

- `/api/**` HTTP 接口；
- `/ws/game` 和 `/ws/mini` WebSocket；
- `shared/app-base.js` 中的动态 context-path 适配；
- `shared/game-table.js`、`room-page.js`、牌面组件和公共 CSS；
- Canvas、DOM 尺寸测量、横屏布局、语音、Worker、Service Worker 等浏览器能力；
- 游戏页面中的大量命令式 DOM 更新与实时状态处理。

因此，普通管理页面迁移难度较低，游戏牌桌和部分小游戏迁移难度较高。

## 3. 迁移后的目标形态

建议在 `web/frontend/` 建立独立的标准 Vue 3 工程，采用：

- Vue 3；
- Vite；
- TypeScript；
- Vue Router；
- Pinia；
- Vitest；
- ESLint 和格式化工具；
- npm 锁定依赖版本；
- Maven 构建时调用 npm，并把 `dist` 内容复制到 JAR 的 `static` 目录。

建议的目录结构如下：

```text
web/
├── frontend/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── public/
│   ├── src/
│   │   ├── main.ts
│   │   ├── App.vue
│   │   ├── api/
│   │   ├── assets/
│   │   ├── components/
│   │   ├── composables/
│   │   ├── layouts/
│   │   ├── router/
│   │   ├── stores/
│   │   ├── types/
│   │   ├── views/
│   │   └── games/
│   └── tests/
├── src/main/java/
├── src/main/resources/
└── pom.xml
```

不要让 Vite 直接覆盖 `src/main/resources/static`。该目录在迁移期还保存旧页面，直接覆盖会导致旧资源丢失、Git 工作区出现大量构建文件，也不利于回滚。推荐先输出到 `web/frontend/dist`，再由 Maven 复制到 `target/classes/static`。

## 4. 必须遵守的迁移原则

1. 新旧页面必须可以并行运行，按页面逐批切换。
2. 一次迁移只改变前端渲染方式，不同时改变后端协议和游戏规则。
3. 每个页面迁移前先记录当前接口、WebSocket 消息、跳转地址、缓存键和关键交互。
4. Vue 状态是页面状态的唯一来源，迁移后不得继续让多套代码同时修改同一个 DOM。
5. Canvas、Worker 或复杂游戏引擎可以先保留为普通 TypeScript 模块，不要求第一阶段全部改成 Vue 组件。
6. 所有 URL 必须在根路径和动态 context-path 下都能工作。
7. 每一批上线都要有明确开关或旧页面入口，确保能够快速回滚。
8. 服务是否正常必须用主机 Java 进程、监听端口和同一时间段日志交叉验证；仅页面返回 200 不代表前端、API、WebSocket 和后端链路都正常。

## 5. 总体迁移顺序

推荐按以下顺序迁移：

1. 工程脚手架和公共基础设施；
2. 登录、注册和入口页；
3. 大厅、导航、会话和房间列表；
4. 管理后台及回放查询；
5. 学习中心；
6. 纯本地休闲小游戏；
7. 联网小游戏；
8. 斗地主、跑得快、拖拉机等扑克游戏；
9. 麻将；
10. 清理旧页面和旧依赖。

这个顺序先处理低风险页面，并尽早验证认证、context-path、API、路由和构建部署链路。麻将放在最后，是因为其状态、操作类型、牌面展示和断线恢复通常最复杂。

## 6. 分阶段实施步骤

### 阶段 0：冻结基线和建立清单

#### 操作

1. 为当前可正常运行的版本建立 Git 标签或明确提交号。
2. 列出全部 HTML 入口，以及每个入口引用的 JS、CSS、图片、音频、Worker 和 Service Worker。
3. 建立页面迁移表，至少记录：
   - 页面地址；
   - 页面功能；
   - 对应 API；
   - 对应 WebSocket；
   - localStorage/sessionStorage/cookie 使用；
   - 页面间传参方式；
   - 移动端、横屏和全屏行为；
   - 是否依赖 Canvas、Worker、语音或音频；
   - 负责人、状态和验收结果。
4. 保存关键页面截图和操作录像，作为视觉及交互基线。
5. 为 WebSocket 记录典型消息样本：连接、准备、开局、操作、结算、重连和异常。
6. 执行现有自动化测试和静态链接检查，记录基线结果。

#### 验收

- 所有现有入口均进入清单；
- 每个联网页面都能对应到 API 或 WebSocket；
- 可以从基线提交重新构建旧版 `Web.jar`。

### 阶段 1：建立标准 Vue 3 工程

#### 操作

1. 在 `web/frontend/` 初始化 Vue 3 + Vite + TypeScript 工程。
2. 固定 Node.js 主版本，建议使用当前受支持的 LTS 版本，并在项目中增加 `.nvmrc` 或等价说明。
3. 提交 `package-lock.json`，CI 和 Maven 使用 `npm ci`，不要在生产构建中使用不锁版本的 `npm install`。
4. 增加开发、构建、测试和代码检查命令：
   - `npm run dev`；
   - `npm run build`；
   - `npm run test`；
   - `npm run lint`；
   - `npm run type-check`。
5. 配置 Vue Router 和 Pinia。
6. 建立统一的错误页、加载状态、提示组件和全局异常捕获。
7. 在 Vue 页面暂时使用独立前缀，例如 `/vue/`，不要立刻替换旧首页。

#### 路由选择

迁移期推荐优先使用 Hash 路由，例如：

```text
/{context-path}/vue/#/lobby
```

它不会要求 Spring Boot 为每一个前端路由配置回退规则，也能避免刷新子路由时返回 404。全部迁移稳定后，如果确实需要无 `#` 地址，再切换 HTML5 History 路由，并在 Spring Boot 和 Nginx 中增加仅针对前端路由的 `index.html` 回退。回退规则不得吞掉 `/api/**`、`/ws/**` 和真实静态文件请求。

#### 验收

- Vue 空壳能通过 Vite 开发服务器运行；
- 构建后静态资源可在 Spring Boot 根路径和非根 context-path 下加载；
- 刷新 Vue 子路由不出现 404；
- 旧页面不受影响。

### 阶段 2：统一动态 context-path、HTTP 和 WebSocket

这是整个迁移最关键的基础步骤。

#### 操作

1. 在 HTML 启动时明确注入或计算应用 context-path。
2. 建立唯一的 URL 工具，例如 `resolveAppUrl(path)`，所有 API、图片、Worker 和跳转都经过它处理。
3. 建立统一 HTTP 客户端，负责：
   - context-path；
   - `credentials`；
   - JSON 编解码；
   - 超时；
   - 业务错误码；
   - 401/登录失效处理；
   - 网络错误提示；
   - 请求取消。
4. 建立统一 WebSocket 客户端，负责：
   - `ws:`/`wss:` 自动选择；
   - context-path；
   - 连接状态；
   - 心跳；
   - 断线和指数退避重连；
   - 主动退出和意外断开的区分；
   - 消息解析和运行时校验；
   - 页面卸载时释放连接和定时器。
5. Vite 开发服务器代理 `/api`、`/ws` 到 `http://127.0.0.1:8081`，WebSocket 代理必须开启 `ws`。
6. 不把线上域名、随机访问前缀、sessionId 或 token 写死进构建产物。

#### 容易出现的问题

- Vite 的 `base: '/'` 会让资源指向域名根目录，部署在随机前缀下时产生 404。
- 简单使用相对路径时，深层页面和路由刷新后的资源位置可能不同。
- WebSocket 漏掉 context-path 或反向代理升级头时，静态页正常但连接失败。
- 开发环境跨端口会产生 cookie、CORS 或 SameSite 差异。
- 多个页面同时创建连接，可能导致重复登录、重复消息或旧连接未释放。

#### 验收

必须分别验证：

- 根路径部署；
- 动态 context-path 部署；
- HTTP 和 HTTPS；
- API 成功、业务失败、超时和登录失效；
- WebSocket 连接、正常关闭、网络中断、恢复和重复连接。

### 阶段 3：迁移登录、注册和站点入口

#### 操作

1. 把登录、注册、邀请码和自动登录拆成独立组件。
2. 把会话信息集中到认证 Store，定义清楚 token/sessionId 的来源和生命周期。
3. 保持 `/api/auth/login`、`/api/auth/register` 等当前接口不变。
4. 保持原有错误码和提示含义，不仅根据 HTTP 状态判断成功。
5. 加入重复提交保护、请求中状态和输入校验。
6. 保留旧首页地址，通过临时路径访问新版，验收后再切换默认入口。

#### 验收

- 用户名密码登录、token 登录、注册、退出全部正常；
- 刷新页面后会话行为与旧版一致；
- 登录失败不会留下错误会话；
- 手机端输入框和键盘交互正常；
- 从旧页面与新页面相互跳转时会话不丢失。

### 阶段 4：迁移大厅和房间页面

#### 操作

1. 将导航、用户信息、能力检测、游戏入口拆成组件。
2. 将房间查询、创建、加入和退出封装到 Store/服务层。
3. 保留 `/api/rooms` 等现有接口契约。
4. 迁移 `capabilities-poll.js` 的轮询逻辑，并确保页面隐藏或卸载时暂停。
5. 对房间创建和加入操作增加幂等保护，避免双击重复请求。
6. 对进入牌桌的参数进行显式类型定义和校验。

#### 验收

- 房间列表刷新、创建、加入和退出正常；
- 后端部分服务不可用时，“敬请期待”或降级状态与现状一致；
- 轮询没有重复定时器；
- 返回、刷新、前后台切换不会造成重复进入房间。

### 阶段 5：迁移管理后台和回放页面

#### 操作

1. 先迁移纯表格和表单页面：用户、邀请码、牌桌、记录、回放。
2. 将分页、筛选、加载、空状态和错误提示统一封装。
3. 高风险管理操作保留确认步骤和服务端权限校验。
4. 终端页面单独迁移，不与普通管理页面同时上线。
5. 终端输出必须作为文本显示，禁止使用不可信内容直接写入 `innerHTML`。

#### 验收

- 管理员与普通用户权限隔离不变；
- 表格分页和详情数据与旧版一致；
- 重放代码和终端输出不会产生 XSS；
- 删除、禁用、撤销等操作不会被重复提交。

### 阶段 6：整理并迁移学习中心

学习中心已经使用 Vue 3，重点不是从零重写，而是工程化。

#### 操作

1. 把 `createApp` Options API 代码移入 `.vue` 单文件组件。
2. 按语文、数学、奥数、资源库和管理功能拆分组件及路由。
3. 将当前全局变量、合并对象和 `parts/*.js` 转为显式模块依赖。
4. 逐步增加 TypeScript 类型，不要求一次性开启最严格规则。
5. 迁移 CSS 时先保持视觉一致，再进行样式清理。
6. 删除对本地 `vue.global.prod.js` 的引用必须放在该页面完全切换之后。

#### 重点风险

- 原模板可能依赖运行时编译，迁移到单文件组件后作用域不同；
- Options API 中 `this` 的来源混杂，拆模块时容易漏方法或状态；
- 大型列表或内容渲染可能因响应式对象过深造成性能下降；
- 文件、音频和图片 URL 在 Vite 构建后路径变化；
- 现有安全响应头对学习中心有特殊处理，不能无意改变。

### 阶段 7：迁移休闲小游戏

#### 方法

Vue 负责页面壳、菜单、弹窗、设置和生命周期；游戏引擎负责高频计算与 Canvas 渲染。不要把每一帧的棋子坐标或动画对象全部做成深层响应式数据。

#### 操作

1. 先迁移不联网、无 Service Worker 的小游戏。
2. 将现有游戏脚本整理为可导入模块，并明确 `mount`、`start`、`pause`、`resize`、`destroy` 生命周期。
3. Vue 组件在 `onMounted` 创建引擎，在 `onBeforeUnmount` 清理：
   - `requestAnimationFrame`；
   - 定时器；
   - DOM 监听器；
   - ResizeObserver；
   - Worker；
   - AudioContext；
   - WebSocket。
4. Worker 文件使用 Vite 支持的 URL 构造方式，不能依赖旧目录相对路径。
5. Service Worker 单独制定 scope 和缓存版本策略。动态 context-path 下若不能保证 scope 正确，应延续当前禁用或降级策略。
6. 联网小游戏最后接入 `/ws/mini`。

#### 验收

- 连续进入退出页面不会增加监听器、Worker 或定时器；
- 页面切到后台能够暂停高消耗循环；
- 画布在手机、桌面、横屏和高 DPI 下尺寸正确；
- 音频在浏览器要求用户手势时能够正常恢复；
- 游戏规则和 AI 测试结果不变。

### 阶段 8：迁移棋牌游戏牌桌

#### 推荐分层

```text
WebSocket 原始消息
        ↓
协议适配与运行时校验
        ↓
游戏状态机 / Store
        ↓
计算属性和视图模型
        ↓
Vue 组件、牌面组件、Canvas
```

禁止让 WebSocket 回调直接查找并修改 DOM。所有消息先进入协议层，再由状态机决定状态变更，Vue 只根据状态渲染。

#### 每种游戏的迁移步骤

1. 冻结并整理消息类型、字段含义和时序。
2. 为消息建立 TypeScript 类型和运行时保护。
3. 把当前脚本中的纯规则、排序和牌型计算提取为纯函数，并补单元测试。
4. 建立明确的牌桌状态：未连接、连接中、等待、准备、游戏中、结算、断线、重连中和失败。
5. 先用新版状态层驱动旧视图或调试面板，确认消息状态一致。
6. 再迁移玩家区、手牌区、公共牌区、操作区、倒计时、提示和结算弹窗。
7. 迁移横屏、自适应、触摸选择和动画。
8. 增加断线重连和快照恢复测试。
9. 与旧页面进行同局或同回放对照。
10. 单个游戏完成后独立上线，不等待所有游戏一起完成。

#### 扑克页面注意事项

- 手牌排序、选牌状态和后端牌 ID 必须分开，不能因视觉排序改变提交顺序或牌值。
- 快速连续点击、倒计时结束和服务端推送可能形成竞态。
- 动画结束不能作为业务状态改变的唯一触发条件。
- 结算、下一局和准备消息可能紧邻到达，应由状态机处理。

#### 麻将页面注意事项

- 手牌、摸牌、弃牌、副露和花牌应使用稳定唯一标识，不能仅用数组下标作为 Vue `key`。
- 吃、碰、杠、胡等多个可选操作要保持服务端给出的操作标识。
- 补花、抢杠、海底等特殊时序需使用真实消息样本回归。
- 断线恢复必须以服务端快照为准，不能继续使用断线前的本地临时状态。
- 不要用异步动画阻塞消息处理，应将动画队列和业务状态分离。

### 阶段 9：切换默认入口

#### 操作

1. 所有核心链路通过验收后，将 `/` 和主要导航指向 Vue 页面。
2. 旧页面改到明确的兼容入口，保留至少一个发布周期。
3. 监控前端异常、API 失败率、WebSocket 断开率、登录失败和页面资源 404。
4. 确认稳定后再删除旧入口、旧 JS、旧 CSS 和 `vue.global.prod.js`。
5. 删除前先使用静态资源引用检查，避免误删共享图片、音频或游戏素材。

#### 回滚

- 页面级回滚：导航重新指向旧 HTML；
- 发布级回滚：恢复上一个经过验证的 `Web.jar`；
- 不建议上线后临时修改 JAR 内静态文件，这会造成版本不可追踪。

## 7. Maven 与 Vite 的标准打包方案

### 7.1 推荐方案

生产构建由 Maven 统一触发：

1. 在 `web/frontend` 执行 `npm ci`；
2. 执行 `npm run build`，生成 `web/frontend/dist`；
3. Maven 将 `dist` 复制到 `${project.build.outputDirectory}/static`；
4. Spring Boot Maven 插件把 Java class、配置和静态文件共同打入 `Web.jar`；
5. 最终仍输出 `build/web/Web.jar`，现有 `ops.sh` 的启动方式保持不变。

可使用 `frontend-maven-plugin` 固定并下载 Node/npm，再使用 `maven-resources-plugin` 复制 `dist`。这样 CI 和开发机无需依赖各自不同的全局 Node 版本。若服务器不能联网下载 Node 和 npm 包，应在 CI 构建产物后部署 JAR，或者使用内部 npm 镜像及 Maven缓存；不要在每次线上启动时现场下载依赖。

### 7.2 迁移期资源合并

迁移期需要同时打包旧静态目录和 Vue 构建结果：

```text
src/main/resources/static  ─┐
                            ├─> target/classes/static ─> Web.jar
frontend/dist             ──┘
```

建议先把 Vue 产物放入 `static/vue/`，避免与旧 `index.html`、`assets/` 重名。切换默认入口后，再调整输出位置。复制时必须定义覆盖顺序，同名文件应让构建直接失败或由明确规则处理，不能静默覆盖。

### 7.3 Vite 的 base 配置

由于生产环境 context-path 可变，不能在构建时写死随机前缀。建议采用以下两种方式之一：

1. 迁移期使用相对资源基址，并通过 Hash 路由运行；
2. 由后端在入口 HTML 注入运行时 public base，再由统一 URL 工具生成 API、WebSocket 和业务资源地址。

无论选择哪种方式，都必须实测 CSS 中的图片、动态 import、Worker、字体、音频和 Service Worker。它们的 URL 处理方式并不完全相同。

### 7.4 建议的构建命令

开发阶段只构建前端：

```bash
cd /home/ec2-user/repos/Server/web/frontend
npm ci
npm run type-check
npm run test
npm run build
```

构建完整项目：

```bash
cd /home/ec2-user/repos/Server
mvn clean install -DskipTests
```

如果已经在 `pom.xml` 中把 Vue 构建绑定到 Maven 生命周期，上述 Maven 命令会自动执行 `npm ci` 和 `npm run build`。日常增量构建可根据最终 Maven 配置决定是否增加跳过前端安装或跳过前端测试的参数，但正式发布不得跳过 `npm run build`。

构建完成后检查：

```bash
test -f build/web/Web.jar
jar tf build/web/Web.jar | grep 'BOOT-INF/classes/static/'
```

还应检查 Vue 的入口 HTML、带哈希的 JS/CSS、图片和 Worker 是否确实进入 JAR。

## 8. 开发环境如何运行

### 8.1 推荐的前后端分离开发方式

终端一运行 Java Web 后端：

```bash
cd /home/ec2-user/repos/Server
./scripts/ops.sh start web
```

终端二运行 Vite：

```bash
cd /home/ec2-user/repos/Server/web/frontend
npm run dev
```

浏览器访问 Vite 显示的开发地址。Vite 将 `/api` 和 `/ws` 代理到本机 `8081`。该方式支持热更新，适合页面开发。

注意：如果后端是以动态 context-path 启动，Vite 代理也必须包含同一前缀，或者开发环境明确使用根 context-path。不要因为 Vite 页面显示正常，就跳过动态前缀部署测试。

### 8.2 接近生产的本地验证

执行完整 Maven 构建后，通过 `Web.jar` 内的静态资源访问，不再启动 Vite：

```bash
cd /home/ec2-user/repos/Server
./scripts/ops.sh start web
./scripts/ops.sh status web
```

这种方式用于验证最终资源路径、缓存、JAR 内容和 Spring Boot 行为。若服务已经运行，不得未经确认重复启动或重启；先检查实际 Java 进程、8081 监听和对应时间日志。

## 9. 生产部署与运行

### 9.1 发布前

1. 使用干净工作区或 CI 执行前端检查、测试和完整 Maven 构建。
2. 保存新旧 JAR 的版本号、提交号、校验值和构建时间。
3. 在测试环境用与生产相同的 context-path 和 Nginx 规则验证。
4. 重点检查登录、大厅、API、WebSocket、至少一局游戏、学习中心和后台。
5. 确认旧 JAR 可以立即恢复。

### 9.2 部署

迁移完成后的交付物仍是：

```text
/home/ec2-user/repos/Server/build/web/Web.jar
```

使用项目现有运维脚本管理：

```bash
cd /home/ec2-user/repos/Server
./scripts/ops.sh status web
./scripts/ops.sh restart web
```

`restart` 会影响在线用户和 WebSocket 对局，只能在明确的发布窗口执行，并应提前确认是否存在在线用户或进行中的牌桌。Vue 迁移不改变这一点。

如果需要配置 Nginx，继续使用项目已有的 Nginx 配置生成和应用流程。Vue 静态资源、API 和 WebSocket 必须位于同一 context-path 规则下；WebSocket location 仍需正确传递 Upgrade/Connection 头。

### 9.3 发布后验证

发布后必须在同一时间段交叉检查：

1. 主机实际 Java `Web.jar` 进程；
2. `8081` 监听端口；
3. Web 服务启动日志及访问日志；
4. 通过本机地址请求入口 HTML；
5. 通过公网域名和动态 context-path 请求入口 HTML；
6. 浏览器中 JS/CSS/图片是否均为 200；
7. 登录 API 的实际请求和响应；
8. WebSocket 是否返回 `101 Switching Protocols` 并正常收发消息；
9. 后端依赖服务链路是否正常；
10. 浏览器控制台是否有运行时错误。

静态 HTML 返回 200 只说明入口文件可访问，不能证明 Vue 已挂载、API 正常、WebSocket 正常或完整服务链路正常。

## 10. 主要风险与处理办法

### 10.1 动态 context-path 导致资源 404

**表现：** HTML 能打开，但 JS、CSS、图片或 Worker 指向域名根目录。

**处理：** 禁止写死 `/assets/...`；统一处理运行时 base；在随机前缀下执行完整浏览器测试。

### 10.2 SPA 刷新出现 404

**表现：** 页面内跳转正常，刷新子路由失败。

**处理：** 迁移期使用 Hash 路由；使用 History 路由时添加精确回退，并排除 API、WebSocket 和静态文件。

### 10.3 API 地址重复或漏前缀

**表现：** 请求出现 `/{prefix}/{prefix}/api`，或直接请求 `/api`。

**处理：** URL 只允许一个模块拼接；禁止组件自行拼 context-path；增加 URL 单元测试。

### 10.4 WebSocket 在 HTTPS 下失败

**表现：** 页面正常，控制台提示 mixed content 或握手失败。

**处理：** HTTPS 使用 `wss:`；检查 context-path 和 Nginx Upgrade；用当次握手和服务端日志判断，不能只看静态页面。

### 10.5 响应式更新与实时消息竞态

**表现：** 重复出牌、操作按钮残留、结算被下一局覆盖。

**处理：** 建立状态机和消息序号/局号保护；业务状态与动画状态分离；对重复、乱序和延迟消息测试。

### 10.6 Vue 与旧脚本同时控制 DOM

**表现：** DOM 被覆盖、事件重复、显示状态反复变化。

**处理：** 按区域明确所有权；旧引擎只能操作其专属容器；完成迁移后移除旧监听器。

### 10.7 内存泄漏和重复连接

**表现：** 多次进出页面后消息重复、CPU 升高、声音叠加。

**处理：** 组件卸载时统一清理连接、Worker、动画帧、观察器、事件和定时器；增加反复进出页面的测试。

### 10.8 Vite 构建后的资源路径变化

**表现：** 开发环境正常，JAR 中图片、音频或 Worker 404。

**处理：** 静态资源按 Vite 规则导入；生产验证必须使用构建产物；不要依赖源码目录相对位置。

### 10.9 缓存导致新旧文件不匹配

**表现：** 新 HTML 加载旧 JS，出现白屏或接口字段错误。

**处理：** JS/CSS 使用内容哈希；HTML 设置合适的短缓存或不缓存；发布时保持旧哈希资源一段时间；谨慎管理 Service Worker 缓存。

### 10.10 TypeScript 一次性改造过重

**表现：** 大量 `any` 或类型错误阻塞业务迁移。

**处理：** 新基础层严格类型化；旧游戏模块允许分阶段迁移；优先为 API、WebSocket 和 Store 边界建类型。

### 10.11 性能下降

**表现：** 出牌、拖动或动画卡顿，移动设备更明显。

**处理：** 高频图形状态避免深响应式；使用 `shallowRef` 或引擎内部对象；减少大列表重渲染；用真实低端设备检测帧率和内存。

### 10.12 安全问题

**表现：** 迁移时为方便渲染使用 `v-html`，引入 XSS；token 被错误持久化或输出到日志。

**处理：** 默认文本渲染；确需 HTML 时先进行可信清洗；不在 URL、构建配置和日志暴露凭据；服务端权限校验继续保留。

### 10.13 npm 供应链和构建环境不稳定

**表现：** 同一提交构建结果不同，服务器无网络时构建失败。

**处理：** 提交锁文件、使用 `npm ci`、固定 Node 版本、审计依赖、缓存制品；推荐 CI 构建 JAR 后部署，不在生产启动阶段安装依赖。

## 11. 测试方案

### 11.1 单元测试

- URL/context-path 处理；
- API 响应转换；
- WebSocket 消息解析；
- 游戏牌型、排序、操作判断；
- Store 状态迁移；
- 重连退避和定时器清理。

### 11.2 组件测试

- 登录和注册表单；
- 房间列表及创建弹窗；
- 操作按钮显示条件；
- 结算弹窗；
- 管理后台分页和错误状态。

### 11.3 端到端测试

- 注册或登录 → 大厅 → 房间 → 准备 → 开局 → 操作 → 结算 → 下一局；
- 刷新、返回、断网、恢复和重复进入；
- 管理员登录及关键管理查询；
- 学习中心登录、内容加载和记录保存；
- 根路径和动态 context-path 各执行一次。

### 11.4 人工兼容性测试

- Chrome、Edge、Safari，以及实际需要支持的移动浏览器；
- 桌面、手机竖屏、手机横屏；
- 触摸、鼠标和键盘；
- 弱网、高延迟和短暂断网；
- HTTP 本地环境和 HTTPS 生产环境。

## 12. 每批迁移的完成标准

某页面只有同时满足以下条件，才算迁移完成：

- 功能与旧版一致，已明确记录有意变化；
- API 和 WebSocket 契约未被意外改变；
- 根路径与动态 context-path 均通过；
- 页面刷新、前进、后退和直接访问正常；
- 无明显控制台错误和资源 404；
- 无重复监听、重复连接和明显内存泄漏；
- 自动化测试通过；
- 关键移动端场景通过；
- Maven 能生成包含该页面的 `build/web/Web.jar`；
- 已说明回滚入口或旧 JAR；
- 发布后验证了 Java 进程、端口、日志、静态资源、API 和 WebSocket。

## 13. 工作量和排期建议

具体时间取决于人员数量、现有测试覆盖和是否要求视觉重做。若只做等价迁移，不同时重新设计 UI，可按以下相对工作量理解：

- 工程、构建、URL、API、WebSocket 基础层：中等；
- 登录、大厅、后台：中等；
- 学习中心工程化：中等偏大；
- 纯本地小游戏：每个小到中等；
- 联网棋牌游戏：每个中等到大；
- 麻将：大；
- 全量回归、灰度、部署和清理：中等偏大。

建议以“一个基础设施里程碑 + 每个页面组一个里程碑”管理，不建议承诺一次性大版本完成。每个里程碑都应独立构建、测试、上线和回滚。

## 14. 最终结果

全部迁移完成后：

- 开发人员在 `web/frontend` 使用 Vite 热更新开发；
- 前端使用 Vue 3 单文件组件、TypeScript、Router 和 Pinia；
- API 和 WebSocket 继续由 Spring Boot 提供；
- Maven 在构建过程中自动构建 Vue 并将产物放入 Spring Boot JAR；
- 发布物仍为 `build/web/Web.jar`；
- 部署和运行仍通过现有 `scripts/ops.sh` 管理；
- Nginx 和动态 context-path 机制继续保留；
- 生产运行时不需要 Node.js，Node.js 只用于构建阶段；
- 浏览器最终只下载构建后的 HTML、带哈希的 JS/CSS 和静态资源。

这套方案的核心是先把构建、URL、认证、API 和 WebSocket 基础设施做稳，再逐页迁移，最后才删除旧页面。这样可以把一次高风险的整体重写，拆成多次可验证、可上线、可回滚的改造。
