# 接口契约

## 一、前端 ↔ Java 后端

统一返回结构：

```json
{ "code": 200, "message": "success", "data": ... }
```

失败时 `code != 200`，`message` 为可读错误（全局异常处理，不漏堆栈）。
除标注外，均需在请求头带 `Authorization: Bearer <token>`。

### 账号

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册，返回 `{token,userId,nickname}` |
| POST | `/api/auth/login` | 登录，返回同上 |

### 岗位

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/positions` | 岗位列表 |
| GET | `/api/positions/{id}` | 岗位详情 |
| POST | `/api/positions` | 新建（category/title/description/companyName/companyIntro/city） |
| PUT | `/api/positions/{id}` | 编辑 |
| DELETE | `/api/positions/{id}` | 删除 |

### 简历

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/resumes?status=draft\|optimized` | 列表 |
| POST | `/api/resumes/upload` | 上传文件（multipart: file/name/tag/positionId） |
| POST | `/api/resumes/paste` | 粘贴文本（params: content/name/tag/positionId） |
| POST | `/api/resumes/import-ai` | 从 Agent 产物导入（params: fileName/tag/positionId） |
| GET | `/api/resumes/{id}/download` | 下载 |
| DELETE | `/api/resumes/{id}` | 删除 |

### 面试归档与复盘

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/interviews/archive` | 归档，**幂等**。body：`{agentSessionId, positionId}` |
| GET | `/api/interviews` | 会话列表（时间轴用） |
| GET | `/api/interviews/{id}` | 详情：`{session, messages[], review}` |

### 找职位

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/job-sites?keyword=&city=` | 招聘站点（含拼好的外链），**无需登录** |
| GET | `/api/job-searches` | AI 职位清单记录（含 `disclaimer`） |
| POST | `/api/job-searches` | 保存记录；`source=agent` 时服务端强制写入 disclaimer |
| DELETE | `/api/job-searches/{id}` | 删除 |

### 知识库文档

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/knowledge-docs` | 文档列表 |
| POST | `/api/knowledge-docs` | 记录上传（multipart: agentKbName/file） |
| DELETE | `/api/knowledge-docs/{id}` | 删除 |

### 薄网关（透传给 Agent）

| 方法 | 路径 | 转发到 Agent |
|---|---|---|
| POST | `/agent/chat` | `POST /chat` |
| POST | `/agent/chat/stream` | `POST /chat/stream`（**SSE**） |
| POST | `/agent/chat/finish` | `POST /chat/finish` |
| POST | `/agent/chat/confirm` | `POST /chat/confirm` |
| GET | `/agent/health` | `GET /health` |
| GET | `/agent/knowledge` | `GET /knowledge` |
| POST | `/agent/knowledge/{kb}/ingest` | `POST /knowledge/{kb}/ingest`（multipart） |
| DELETE | `/agent/knowledge/{kb}` | `DELETE /knowledge/{kb}` |

## 二、Java 后端 ↔ Python Agent

> **Agent 接口没有 `/api` 前缀**（旧文档写 `/api/chat` 是错的，已在 Agent 工程 README 修正）。

`chat` / `knowledge` / `sessions` 三组需请求头 `X-API-Key`（Agent 侧 `API_KEY` 为空则关闭鉴权）。

### SSE 事件格式（`/chat/stream`）

```
data: {"type":"session","session_id":"...","mode":"mock_interview"}

data: {"type":"delta","text":"你"}

data: {"type":"done","citations":[],"artifact":null,"finished":false,
       "requires_confirmation":false,"awaiting_confirmation":false,"draft_artifact":null}
```

前端需从首个 `session` 事件取 `session_id`，供后续「归档到复盘」使用。

### 会话详情（归档数据源）

`GET /sessions/{id}`：

```json
{
  "id": "...",
  "mode": "mock_interview",
  "kb_name": "",
  "finished": true,
  "artifact": "路径字符串",
  "messages": [{ "role": "user", "content": "..." }]
}
```

> `artifact` 只是**路径字符串**，Agent 没有文件下载接口。
> 因此简历导入走「共享卷只读读取」或手动上传。
