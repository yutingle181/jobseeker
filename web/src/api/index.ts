import http, { unwrap } from './http'

export interface Position {
  id: number
  category: string
  title: string
  description: string
  companyName: string
  companyIntro: string
  city: string
}

export interface ResumeItem {
  id: number
  name: string
  sourceType: string
  tag: string
  status: string
  positionId: number
  createdAt: string
}

export interface ReviewItem {
  session: { id: number; mode: string; createdAt: string; positionId: number }
  review: { suggestions: string; weaknesses: string; detail: string } | null
}

export const authApi = {
  login: (data: { username: string; password: string }) =>
    unwrap<{ token: string; userId: number; nickname: string }>(http.post('/auth/login', data)),
  register: (data: { username: string; password: string; nickname?: string }) =>
    unwrap<{ token: string; userId: number; nickname: string }>(http.post('/auth/register', data)),
}

export const positionApi = {
  list: () => unwrap<Position[]>(http.get('/positions')),
  create: (data: Partial<Position>) => unwrap<number>(http.post('/positions', data)),
  update: (id: number, data: Partial<Position>) => unwrap(http.put(`/positions/${id}`, data)),
  remove: (id: number) => unwrap(http.delete(`/positions/${id}`)),
}

export const resumeApi = {
  list: (status?: string) => unwrap<ResumeItem[]>(http.get('/resumes', { params: { status } })),
  upload: (file: File, name: string, tag: string, positionId?: number) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('name', name)
    fd.append('tag', tag)
    if (positionId) fd.append('positionId', String(positionId))
    return unwrap<number>(http.post('/resumes/upload', fd))
  },
  paste: (content: string, name: string, tag: string, positionId?: number) =>
    unwrap<number>(
      http.post('/resumes/paste', null, { params: { content, name, tag, positionId } }),
    ),
  importAi: (fileName: string, tag: string, positionId?: number) =>
    unwrap<number>(
      http.post('/resumes/import-ai', null, { params: { fileName, tag, positionId } }),
    ),
  remove: (id: number) => unwrap(http.delete(`/resumes/${id}`)),
  downloadUrl: (id: number) => `/api/resumes/${id}/download`,
}

export const interviewApi = {
  archive: (agentSessionId: string, positionId?: number) =>
    unwrap<number>(http.post('/interviews/archive', { agentSessionId, positionId })),
  list: () => unwrap<{ id: number; mode: string; createdAt: string; positionId: number }[]>(
    http.get('/interviews'),
  ),
  detail: (id: number) => unwrap<ReviewItem & { messages: { role: string; content: string }[] }>(
    http.get(`/interviews/${id}`),
  ),
}

export const jobSiteApi = {
  // 显式 8s 超时（覆盖全局 30s），避免后端偶发卡死时前端干等整段超时
  list: (keyword?: string, city?: string) =>
    unwrap<{ id: number; name: string; icon: string; url: string }[]>(
      http.get('/job-sites', { params: { keyword, city }, timeout: 8000 }),
    ),
}

export const jobSearchApi = {
  list: () =>
    unwrap<{ id: number; keyword: string; result: string; source: string; disclaimer: string }[]>(
      http.get('/job-searches'),
    ),
  save: (data: { keyword: string; result: string; positionId?: number; source?: string }) =>
    unwrap<number>(http.post('/job-searches', data)),
  remove: (id: number) => unwrap(http.delete(`/job-searches/${id}`)),
}

export const knowledgeApi = {
  list: () => unwrap<{ id: number; agentKbName: string; fileName: string }[]>(
    http.get('/knowledge-docs'),
  ),
  record: (agentKbName: string, file: File) => {
    const fd = new FormData()
    fd.append('agentKbName', agentKbName)
    fd.append('file', file)
    return unwrap<number>(http.post('/knowledge-docs', fd))
  },
  remove: (id: number) => unwrap(http.delete(`/knowledge-docs/${id}`)),
}

export interface AgentStreamHandlers {
  onSession?: (info: { session_id: string; mode: string }) => void
  onDelta?: (text: string) => void
  onDone?: (payload: any) => void
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
  /** 关联岗位 ID，由后端网关注入为 user_context */
  position_id?: number
  /** 关联简历 ID，由后端网关注入为 user_context */
  resume_id?: number
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
        else if (evt.type === 'done') handlers.onDone?.(evt)
      } catch {
        /* 忽略非 JSON 行 */
      }
    }
  }
}
