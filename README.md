# AI 求职助手（jobseeker）

> 当前版本 **v0.3.0**

参照 OfferGoose 的求职辅助软件，**内嵌现有 Python Agent 作为独立的「AI 助手」**。

核心分工：**Agent 负责"生成"，本软件负责"存、管、看"**。

## 设计原则

- **Agent 的 9 个能力一律不重新实现**（教程 / 答疑 / 简历 / 面试真题 / 模拟面试 / 职位搜索 / 知识库问答 / JD 匹配诊断 / 面试复盘），
  全部收归「AI 助手」页，Java 只做**薄网关透传**。
- **薄网关的唯一加工点：装配用户画像**。调用 Agent 前，按登录用户把「所选岗位 + 关联简历」拼装为 `user_context` 注入 `/agent/chat` 与 `/agent/chat/stream`，让 Agent 直接"认识"用户岗位与简历。
- **Agent 代码零改动**，只通过 HTTP / SSE 调用。
- **不含会员功能**（无套餐、无次数扣减、无支付）。
- 与 Agent 工程**平行存放**：`../Agent实习项目`（Python）与 `jobseeker`（Java + Vue）互不污染。

## 本软件做什么（Agent 没有的能力）

| 模块 | 说明 |
|---|---|
| 账号 | 注册 / 登录 / JWT 鉴权 |
| 岗位 JD 库 | 3 步向导新建、列表、详情、编辑、删除 |
| 简历文件管理 | 上传 / 粘贴 / 版本 / 下载 / 删除 / 从 AI 产物导入 |
| 面试归档与复盘 | 调 Agent `/sessions/{id}` 单向只读归档；**复盘升级为四段式结构化卡片**（表现评分环形指标 / 追问链步骤条 / 薄弱点 / 改进动作），解析失败自动回退旧版「建议 / 弱点」 |
| **JD 匹配评分卡** | 关联岗位 + 简历后，把 Agent 的结构化产物渲染为 环形总分 + 分项进度条 + 命中/缺口双栏 + 面试准备重点 |
| **工具调用时间线** | 把 SSE `tool_call` 事件渲染为可折叠时间线（工具名 / 耗时 / 入参摘要 / 返回摘要），过程可追溯 |
| 职位搜索记录 | 保存检索与结果（`source=agent` 强制带免责说明） |
| 知识库文档 | 上传文档归属与列表（建库问答在 Agent 侧） |
| 招聘网站入口 | 站点配置 + 安全外链拼接 |
| 投递进度管理 | 秋招投递台账：列表增删改、12 状态枚举 + 面试轮次、关联岗位/简历、后端聚合统计看板（总投递 / Offer / 面试中 / 拒信 与 状态 / 城市 / 渠道分布） |
| 网关可观测 | TTFT / P95 / 缓存命中率 + **工具调用次数与耗时分布**（`GET /agent/gateway-metrics`） |

## AI 助手上下文注入（user_context）

「AI 助手」不是裸 Agent，而是网关在调用前为用户装配好背景：

1. 用户在「AI 助手」页选择**岗位**与**关联简历**（前端 `assistant` 视图的模式选择器与简历下拉）。
2. 后端 `AgentContextService` 校验二者归属当前用户后，查库取出岗位 JD 与简历正文（截断）拼装为 `user_context`。
3. 网关把 `user_context` 随请求注入 `/agent/chat`、`/agent/chat/stream`；Agent 侧 `SessionManager` 在每轮以 `SystemMessage` 前置注入会话（历史裁剪后画像不丢）。

效果：模拟面试 / 简历制作 / 职位搜索等能力能直接结合用户岗位与简历作答；`user_context` 为空时行为与旧版一致。

## 目录

```
jobseeker/
├── server/                  # Java 后端 Spring Boot 3.2.5（分层 Controller/Service/Mapper）
│   └── src/main/resources/db/schema.sql   # 建表脚本（含招聘站点初始数据）
├── web/                     # Vue 3 + Vite + tdesign + Tailwind
├── .mvn/local-settings.xml.example   # 本地 Maven 配置模板（复制为 server/.mvn/local-settings.xml 后使用，不进库）
├── docker-compose.yml       # 编排 mysql + server + web
├── start-all.ps1            # 先起 Agent 再起本软件
├── start-backend.bat        # Windows 一键重启后端（按端口杀旧进程再 mvn spring-boot:run）
├── scripts/                 # 运维脚本（如网关压测 bench-gateway.ps1）
└── docs/
    ├── ARCHITECTURE.md      # 架构与关键决策
    ├── API_CONTRACT.md      # 前后端 + 后端↔Agent 接口契约
    └── DEPLOY.md            # 部署步骤与已知坑
```

## 快速开始

```powershell
# 1) 先起 Python Agent（在平行目录）
cd ..\Agent实习项目
.\run_api.ps1

# 2) 建库
mysql -u root -p < server/src/main/resources/db/schema.sql

# 3) 起后端（JDK 17） —— 新开一个终端、回到 jobseeker 目录后进入 server
cd jobseeker\server
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
# 首次需准备本地 Maven 配置：把仓库根模板复制为 server/.mvn/local-settings.xml，并按本机改 localRepository 路径（不进版本库）
copy ..\.mvn\local-settings.xml.example .mvn\local-settings.xml
mvn -s .mvn/local-settings.xml spring-boot:run

# 4) 起前端
cd ..\web
npm install --registry=https://registry.npmmirror.com
npm run dev
```

> **Windows 本机构建坑（D: 盘重命名被拦）**：部分环境 D: 盘禁止文件重命名，会导致 Maven 资源拷贝
> `AccessDeniedException`（以及 Safe-Delete 拒绝删除 `target`）。已由 `server/pom.xml` 的
> `windows-safe-delete` profile 把**资源输出**改到 `C:/jobseeker-target/classes`（编译产物仍留在项目内 `target`）；
> 也可用仓库根目录的 `start-backend.bat` 一键重启（按端口杀旧进程再 `mvn spring-boot:run`）。

详见 [`docs/DEPLOY.md`](docs/DEPLOY.md)。

## 重要提醒

- **AI 职位清单不是真实职位**：Agent 的联网检索当前不可用（`ENABLE_WEB_SEARCH=false`
  且 DuckDuckGo 超时），职位搜索结果本质是模型推断，界面**强制展示免责标注且不渲染链接**。
- **Agent 接口没有 `/api` 前缀**：真实路径是 `/chat`、`/knowledge`、`/sessions`（旧文档有误，已修正）。
- **AI 生成内容免责**：助手对话、面试复盘（建议 / 弱点）、简历产物、AI 职位方向等均为 AI 生成，
  界面强制展示"内容由 AI 生成，仅供参考"，请勿直接当作权威结论。
- **`/agent/**` 需登录态**：AI 助手网关已纳入登录拦截（`AuthInterceptor` 覆盖 `/agent/**`），
  未登录会被拦截——这也是网关注入 `user_context` 的前提（需先拿到当前用户）。
- **主键（雪花 ID）统一按字符串下发**：19 位 Long 超出 JS `Number` 安全整数范围，按数字下发会被
  四舍五入（`...587778` → `...587800`），回传时 ID 已经错了、`user_context` 会**静默**注入失败。
  已由 `config/JacksonConfig.java` 全局把 `Long` 序列化为字符串（`int` 不受影响，`Result.code` 仍是数字）；
  前端所有 ID 一律当字符串透传，不做数值运算。
- **SSE 事件类型**：`session` / `delta` / `tool_call`（工具调用过程）/ `done`（含 `tool_events` 与结构化产物 `structured`）；
  长结构化调用期间每 15s 发一条 `: keep-alive` 注释，避免被网关 OkHttp 的 120s 读超时掐断。
- **前端构建产物不入库**：`npm run build` 的 postbuild 会把 `web/dist` 复制到 `server/src/main/resources/static/`
  （仅供 `:8080` 直接服务 UI），其中 `assets/` 与 `index.html` 已在 `.gitignore` 中忽略；`static/icons/` 是站点图标源码，需保留。
