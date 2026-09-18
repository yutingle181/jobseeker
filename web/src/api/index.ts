import http, { unwrap } from './http'

/**
 * 后端主键为雪花 ID（19 位），已统一按字符串下发：
 * JS 的 Number 装不下 2^53 以上的整数，按数字接收会被四舍五入，回传即错。
 * 因此这里所有 ID 类字段都是 string，只做透传与比较，不参与数值运算。
 */
export type EntityId = string

export interface Position {
  id: EntityId
  category: string
  title: string
  description: string
  companyName: string
  companyIntro: string
  city: string
}

export interface ResumeItem {
  id: EntityId
  name: string
  sourceType: string
  tag: string
  status: string
  positionId?: EntityId | null
  createdAt: string
}

export interface ReviewItem {
  session: { id: EntityId; mode: string; createdAt: string; positionId?: EntityId | null }
  review: { suggestions: string; weaknesses: string; detail: string } | null
}

export const authApi = {
  login: (data: { username: string; password: string }) =>
    unwrap<{ token: string; userId: EntityId; nickname: string }>(http.post('/auth/login', data)),
  register: (data: { username: string; password: string; nickname?: string }) =>
    unwrap<{ token: string; userId: EntityId; nickname: string }>(http.post('/auth/register', data)),
}

export const positionApi = {
  list: () => unwrap<Position[]>(http.get('/positions')),
  get: (id: EntityId) => unwrap<Position>(http.get(`/positions/${id}`)),
  create: (data: Partial<Position>) => unwrap<EntityId>(http.post('/positions', data)),
  update: (id: EntityId, data: Partial<Position>) => unwrap(http.put(`/positions/${id}`, data)),
  remove: (id: EntityId) => unwrap(http.delete(`/positions/${id}`)),
}

export const resumeApi = {
  list: (status?: string) => unwrap<ResumeItem[]>(http.get('/resumes', { params: { status } })),
  upload: (file: File, name: string, tag: string, positionId?: EntityId) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('name', name)
    fd.append('tag', tag)
    if (positionId) fd.append('positionId', String(positionId))
    return unwrap<EntityId>(http.post('/resumes/upload', fd))
  },
  paste: (content: string, name: string, tag: string, positionId?: EntityId) =>
    unwrap<EntityId>(
      http.post('/resumes/paste', {
        content,
        name,
        tag,
        positionId: positionId ?? null,
      }),
    ),
  importAi: (fileName: string, tag: string, positionId?: EntityId) =>
    unwrap<EntityId>(
      http.post('/resumes/import-ai', null, { params: { fileName, tag, positionId } }),
    ),
  /** 关联简历到岗位（岗位向导第二步选择已有简历时使用）。 */
  link: (id: EntityId, positionId: EntityId) =>
    unwrap(http.put(`/resumes/${id}/position`, null, { params: { positionId } })),
  /** 落库 Agent 生成的优化版简历（前端 SSE 流式收集后回传）。内容较长，走 JSON 请求体。 */
  optimizeResult: (content: string, name: string, tag: string, positionId?: EntityId) =>
    unwrap<EntityId>(
      http.post('/resumes/optimize', {
        content,
        name,
        tag,
        positionId: positionId ?? null,
      }),
    ),
  remove: (id: EntityId) => unwrap(http.delete(`/resumes/${id}`)),
  downloadUrl: (id: EntityId) => `/api/resumes/${id}/download`,
}

export const interviewApi = {
  archive: (agentSessionId: string, positionId?: EntityId) =>
    unwrap<EntityId>(http.post('/interviews/archive', { agentSessionId, positionId })),
  list: () => unwrap<{ id: EntityId; mode: string; createdAt: string; positionId?: EntityId | null }[]>(
    http.get('/interviews'),
  ),
  detail: (id: EntityId) => unwrap<ReviewItem & { messages: { role: string; content: string }[] }>(
    http.get(`/interviews/${id}`),
  ),
}

export const jobSiteApi = {
  // 显式 8s 超时（覆盖全局 30s），避免后端偶发卡死时前端干等整段超时
  list: (keyword?: string, city?: string) =>
    unwrap<{ id: EntityId; name: string; icon: string; url: string }[]>(
      http.get('/job-sites', { params: { keyword, city }, timeout: 8000 }),
    ),
}

export const jobSearchApi = {
  list: () =>
    unwrap<{ id: EntityId; keyword: string; result: string; source: string; disclaimer: string }[]>(
      http.get('/job-searches'),
    ),
  save: (data: { keyword: string; result: string; positionId?: EntityId; source?: string }) =>
    unwrap<EntityId>(http.post('/job-searches', data)),
  remove: (id: EntityId) => unwrap(http.delete(`/job-searches/${id}`)),
}

export const knowledgeApi = {
  list: () => unwrap<{ id: EntityId; agentKbName: string; fileName: string }[]>(
    http.get('/knowledge-docs'),
  ),
  record: (agentKbName: string, file: File) => {
    const fd = new FormData()
    fd.append('agentKbName', agentKbName)
    fd.append('file', file)
    return unwrap<number>(http.post('/knowledge-docs', fd))
  },
  remove: (id: EntityId) => unwrap(http.delete(`/knowledge-docs/${id}`)),
}

export interface Delivery {
  id: EntityId
  companyName: string
  jobTitle: string
  city: string
  channel: string
  deliverDate: string
  status: string
  interviewRound: string
  examInfo: string
  lastInterviewTime: string
  result: string
  salary: string
  positionId?: EntityId | null
  resumeId?: EntityId | null
  remark: string
  createdAt: string
}

export interface DeliveryStats {
  total: number
  offerCount: number
  interviewingCount: number
  rejectedCount: number
  byStatus: Record<string, number>
  byCity: Record<string, number>
  byChannel: Record<string, number>
}

export const deliveryApi = {
  list: () => unwrap<Delivery[]>(http.get('/deliveries')),
  get: (id: EntityId) => unwrap<Delivery>(http.get(`/deliveries/${id}`)),
  create: (data: Partial<Delivery>) => unwrap<EntityId>(http.post('/deliveries', data)),
  update: (id: EntityId, data: Partial<Delivery>) => unwrap(http.put(`/deliveries/${id}`, data)),
  remove: (id: EntityId) => unwrap(http.delete(`/deliveries/${id}`)),
  stats: () => unwrap<DeliveryStats>(http.get('/deliveries/stats')),
}

/**
 * 一次工具调用事件（Agent 侧 Function Calling 的可视化素材）。
 *
 * 只承载「工具名 / 耗时 / 参数与结果摘要」，不含工具返回全文——
 * 与后端「事件不带原文」的约定一致，避免把检索原文灌进浏览器。
 */
export interface ToolCallEvent {
  /** 工具名，如 web_search / knowledge_search */
  name: string
  /** 本次调用是否成功 */
  ok?: boolean
  /** 耗时（毫秒） */
  elapsed_ms?: number
  /** 入参摘要（已截断） */
  args_summary?: string
  /** 返回摘要（已截断） */
  result_summary?: string
  /** 事件序号，用于增量补全顺序 */
  index?: number
}

export interface AgentStreamHandlers {
  onSession?: (info: { session_id: string; mode: string }) => void
  onDelta?: (text: string) => void
  onDone?: (payload: any) => void
  /** 新增可选回调：老调用方不实现也不会报错 */
  onToolCall?: (evt: ToolCallEvent) => void
}

/**
 * 调用薄网关的 SSE 流式接口。
 * 用 fetch + ReadableStream 逐段读取（EventSource 不支持 POST）。
 */
export interface AgentStreamBody {
  query: string
  session_id?: string
  /** Agent 的英文 mode；不传则由 Agent 自动路由 */
  mode?: string
  kb_name?: string
  /** 关联岗位 ID（雪花 ID 字符串），由后端网关注入为 user_context */
  position_id?: EntityId
  /** 关联简历 ID（雪花 ID 字符串），由后端网关注入为 user_context */
  resume_id?: EntityId
}

export async function agentStream(
  body: AgentStreamBody,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal,
) {
  const token = localStorage.getItem('token')
  const resp = await fetch('/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!resp.ok || !resp.body) {
    throw new Error(`网关返回 ${resp.status}`)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n')
    buffer = parts.pop() ?? ''
    for (const line of parts) {
      if (!line.startsWith('data:')) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      try {
        const evt = JSON.parse(raw)
        if (evt.type === 'session') handlers.onSession?.(evt)
        else if (evt.type === 'delta') handlers.onDelta?.(evt.text ?? '')
        else if (evt.type === 'tool_call') handlers.onToolCall?.(evt as ToolCallEvent)
        else if (evt.type === 'done') handlers.onDone?.(evt)
      } catch {
        /* 忽略非 JSON 行 */
      }
    }
  }
}
