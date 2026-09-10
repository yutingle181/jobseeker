# 架构设计

## 一句话定位

**Agent 负责"生成"，软件负责"存、管、看"。**

求职辅助软件不重新实现任何 AI 能力，而是把现有 Python Agent 作为独立的「AI 助手」内嵌，
自己只承接 Agent 没有的业务能力：账号、岗位 JD 库、简历文件、面试归档与复盘、知识库文档、招聘站点入口。

## 目录关系（平行工程）

```
d:/project/
├── Agent实习项目/      # 现有 Python Agent + Streamlit —— 完全不动
└── jobseeker/          # 本软件（英文命名，避免 Maven/npm 中文路径问题）
    ├── server/         # Java 后端 :8080
    ├── web/            # Vue3 前端 :5173(dev) / :80(docker)
    └── docs/
```

## 分层架构（后端）

```
Controller  路由、参数校验（JSR-303）
    ↓
Service     业务编排、数据隔离（按 user_id）、归档同步
    ↓
Mapper      MyBatis-Plus 数据访问
    ↓
Entity/DO   与表一一对应
```

旁路两个组件：
- `client/AgentClient`：调 Python Agent（同步 / SSE / multipart / 拉会话）
- `gateway/AgentGatewayController`：`/agent/**` **纯透传**，不做任何业务加工

## 关键设计决策

### 1. 薄网关，而不是重新实现

Java 侧**不包装** Agent 的 7 个能力。网关只做三件事：

1. 转发协议（解决跨域）
2. 注入 `X-API-Key`（解决前端暴露密钥）
3. SSE 边收边写（解决流式被缓冲）

禁止在 Java 侧重写模式判断、提示词、会话状态机。

### 2. 单向只读归档

Agent 有自己的 SQLite，本软件有自己的 MySQL。两者通过归档打通：

```
SSE 首事件拿 session_id → 用户点「归档到复盘」
  → POST /api/interviews/archive
  → Java 调 Agent GET /sessions/{id}
  → 落 interview_session + interview_message + interview_review
```

- **单向**：只从 Agent 拉，绝不反向写 Agent。
- **幂等**：`agent_session_id` 唯一索引，重复归档不产生重复消息。
- 复盘页只读 MySQL，Agent 挂了也能回看历史。

### 3. 找职位 = 真实外链 + AI 参考（A + B）

| | A 外链入口 | B AI 清单 |
|---|---|---|
| 数据 | 真实招聘网站 | Agent `job_search` |
| 真实性 | 真实、实时 | **非实时**，模型推断 |
| 展示 | 卡片 + 新标签打开 | 表格 + **强制免责标注** + **不渲染链接** |

> Agent 的职位搜索为何不真实：`.env` 中 `ENABLE_WEB_SEARCH=false`，且实测 DuckDuckGo
> 10s 超时返回空，模型拿不到任何外部数据，只能凭训练知识生成。
> 因此 B 的免责标注是硬性要求，不是可选装饰。

### 4. 数据隔离

所有业务表带 `user_id`，Service 层强制用 `UserContext.require()` 取当前用户，
查询一律带 `user_id` 条件，删除/查看详情前校验归属，避免越权。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + MySQL 8 |
| 鉴权 | JWT + 拦截器，密码 BCrypt |
| HTTP 客户端 | OkHttp 4（同步 / SSE / multipart） |
| 前端 | Vue 3 + Vite 5 + TypeScript + Pinia + tdesign-vue-next + Tailwind |
| Agent | 现有 Python FastAPI（不改） |
