<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  agentStream,
  interviewApi,
  positionApi,
  resumeApi,
  type Position,
  type ResumeItem,
} from '@/api'
import { Archive, Link2, Send, Sparkles } from 'lucide-vue-next'
import AiDisclaimer from '@/components/AiDisclaimer.vue'

interface Msg {
  role: 'user' | 'assistant'
  content: string
  citations?: string[]
}

/** 模式选项：value 为 Agent 的英文 mode，'' 代表自动路由（不下发 mode） */
const MODES = [
  { value: '', label: '自动路由' },
  { value: 'mock_interview', label: '模拟面试' },
  { value: 'interview_questions', label: '面试真题' },
  { value: 'resume', label: '简历制作' },
  { value: 'knowledge', label: '知识库问答' },
  { value: 'job_search', label: '职位搜索' },
  { value: 'qa', label: '答疑问答' },
  { value: 'tutorial', label: '教程生成' },
]

const messages = ref<Msg[]>([])
const input = ref('')
const streaming = ref(false)
const sessionId = ref('')
const mode = ref('') // 用户选择的模式，'' = 自动路由
const resolvedMode = ref('') // 服务端回传的实际模式
const positionId = ref<number | undefined>(undefined)
const resumeId = ref<number | undefined>(undefined)
const positions = ref<Position[]>([])
const resumes = ref<ResumeItem[]>([])
const archived = ref(false)
const errorMsg = ref('')

const currentPosition = computed(() => positions.value.find((p) => p.id === positionId.value))
const currentResume = computed(() => resumes.value.find((r) => r.id === resumeId.value))
const linked = computed(() => !!currentPosition.value || !!currentResume.value)
const resolvedModeLabel = computed(
  () => MODES.find((m) => m.value === resolvedMode.value)?.label || resolvedMode.value,
)

onMounted(async () => {
  try {
    const [ps, rs] = await Promise.all([positionApi.list(), resumeApi.list()])
    positions.value = ps
    resumes.value = rs
    if (ps.length) positionId.value = ps[0].id
  } catch (e) {
    console.error('加载岗位/简历失败', e)
  }
})

// 切换岗位：自动带出该岗位关联的简历，并重置会话让新上下文当轮生效
watch(positionId, (pid) => {
  const hit = resumes.value.find((r) => r.positionId === pid)
  resumeId.value = hit ? hit.id : undefined
  resetSession()
})

// 切换简历或模式同样重置会话，保证上下文与模式一致
watch(resumeId, () => resetSession())
watch(mode, () => resetSession())

async function scrollBottom() {
  await nextTick()
  const el = document.getElementById('chat-scroll')
  if (el) el.scrollTop = el.scrollHeight
}

/** 重置会话（保留模式与关联选择） */
function resetSession() {
  messages.value = []
  sessionId.value = ''
  resolvedMode.value = ''
  archived.value = false
  errorMsg.value = ''
}

function selectMode(value: string) {
  if (mode.value !== value) mode.value = value
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return

  input.value = ''
  errorMsg.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '' })
  streaming.value = true
  const idx = messages.value.length - 1

  try {
    await agentStream(
      {
        query: text,
        session_id: sessionId.value || undefined,
        mode: mode.value || undefined,
        position_id: positionId.value,
        resume_id: resumeId.value,
      },
      {
        onSession: (info) => {
          sessionId.value = info.session_id
          resolvedMode.value = info.mode
        },
        onDelta: (t) => {
          messages.value[idx].content += t
          scrollBottom()
        },
        onDone: (payload) => {
          messages.value[idx].citations = payload?.citations || []
        },
      },
    )
  } catch (e: any) {
    errorMsg.value = e?.message || '生成失败'
    messages.value[idx].content += '\n\n（生成中断：' + (e?.message || '未知错误') + '）'
  } finally {
    streaming.value = false
    scrollBottom()
  }
}

async function archive() {
  if (!sessionId.value) return
  try {
    await interviewApi.archive(sessionId.value, positionId.value)
    archived.value = true
  } catch (e: any) {
    errorMsg.value = e?.message || '归档失败'
  }
}

/** 完全清空：连模式一起回到默认 */
function resetAll() {
  resetSession()
  mode.value = ''
}
</script>

<template>
  <div class="mx-auto flex h-[calc(100vh-60px)] max-w-5xl flex-col px-6 py-6">
    <!-- 会话头部 -->
    <div class="card mb-4 px-5 py-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 text-sm">
          <Sparkles class="h-4 w-4 text-primary" />
          <span class="font-medium text-ink">AI 助手</span>
          <span v-if="resolvedMode" class="rounded-md bg-primary/10 px-2 py-0.5 text-xs text-primary">{{
            resolvedModeLabel
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <select
            v-model="positionId"
            class="rounded-lg border border-slate-200 px-2 py-1.5 text-sm outline-none focus:border-primary"
          >
            <option :value="undefined">不关联岗位</option>
            <option v-for="p in positions" :key="p.id" :value="p.id">{{ p.title }}</option>
          </select>
          <select
            v-model="resumeId"
            class="rounded-lg border border-slate-200 px-2 py-1.5 text-sm outline-none focus:border-primary"
          >
            <option :value="undefined">不关联简历</option>
            <option v-for="r in resumes" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
          <button
            v-if="sessionId && !archived"
            class="flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-sm text-white transition-colors hover:bg-primaryDark"
            @click="archive"
          >
            <Archive class="h-3.5 w-3.5" />归档到复盘
          </button>
          <span v-else-if="archived" class="text-sm text-success">已归档</span>
          <button
            class="rounded-lg px-3 py-1.5 text-sm text-muted transition-colors hover:bg-slate-100"
            @click="resetAll"
          >
            清空
          </button>
        </div>
      </div>

      <!-- 关联状态：让上下文可见，消除「AI 到底知不知道」的困惑 -->
      <div class="mt-2 flex items-center gap-1.5 border-t border-slate-100 pt-2 text-xs">
        <Link2 class="h-3.5 w-3.5" :class="linked ? 'text-primary' : 'text-muted'" />
        <span v-if="linked" class="text-muted">
          已关联：<span class="text-ink">{{ currentPosition?.title || '未选岗位' }}</span>
          <template v-if="currentResume"> · 简历《<span class="text-ink">{{ currentResume.name }}</span>》</template>
          <span class="ml-1">（将作为上下文发给 AI）</span>
        </span>
        <span v-else class="text-muted">未关联岗位/简历 · AI 将按通用方式回答</span>
      </div>
    </div>

    <!-- 对话区 -->
    <div id="chat-scroll" class="card flex-1 space-y-4 overflow-y-auto p-5">
      <p v-if="!messages.length" class="py-16 text-center text-sm text-muted">
        选择下方模式后直接提问，例如「帮我模拟一场后端工程师面试」或「整理 20 道 Java 面试题」<br />
        手动选择模式可跳过自动路由，响应更快、意图更准。
      </p>

      <div v-for="(m, i) in messages" :key="i" class="flex" :class="m.role === 'user' ? 'justify-end' : 'justify-start'">
        <div
          class="max-w-[78%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed"
          :class="
            m.role === 'user'
              ? 'bg-primary text-white'
              : 'bg-slate-100 text-ink' + (streaming && i === messages.length - 1 ? ' stream-cursor' : '')
          "
        >
          <div class="whitespace-pre-wrap">{{ m.content }}</div>
          <div v-if="m.citations?.length" class="mt-2 border-t border-slate-200 pt-2 text-xs text-muted">
            📎 引用来源
            <div v-for="(c, ci) in m.citations" :key="ci">• {{ c }}</div>
          </div>
        </div>
      </div>
    </div>

    <p v-if="errorMsg" class="mt-2 text-sm text-danger">{{ errorMsg }}</p>

    <!-- 模式选择条 -->
    <div class="mt-4 flex flex-wrap items-center gap-2">
      <button
        v-for="m in MODES"
        :key="m.value"
        type="button"
        class="rounded-full px-3 py-1.5 text-xs transition-all duration-150"
        :class="
          mode === m.value
            ? 'bg-primary text-white shadow-sm'
            : 'bg-slate-100 text-muted hover:bg-primary/10 hover:text-primary'
        "
        @click="selectMode(m.value)"
      >
        {{ m.label }}
      </button>
      <span class="ml-1 text-xs text-muted">手动选择模式可跳过自动路由，响应更快</span>
    </div>

    <!-- 输入区 -->
    <div class="card mt-3 flex items-center gap-3 px-4 py-3">
      <input
        v-model="input"
        class="flex-1 bg-transparent text-sm outline-none"
        placeholder="输入你的问题…"
        @keyup.enter="send"
      />
      <button
        :disabled="streaming || !input.trim()"
        class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark disabled:opacity-50"
        @click="send"
      >
        <Send class="h-4 w-4" />{{ streaming ? '生成中' : '发送' }}
      </button>
    </div>

    <AiDisclaimer />
  </div>
</template>
