<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { interviewApi } from '@/api'
import {
  CheckSquare,
  ChevronDown,
  ChevronUp,
  ListOrdered,
  MessageSquare,
  TrendingUp,
} from 'lucide-vue-next'
import AiDisclaimer from '@/components/AiDisclaimer.vue'
import { hasParsedContent, parseReviewReport } from '@/utils/reviewParse'

interface Session {
  id: string
  mode: string
  createdAt: string
  positionId?: string | null
}

const sessions = ref<Session[]>([])
const expanded = ref<string | null>(null)
const detail = ref<any>(null)
const errorMsg = ref('')

onMounted(async () => {
  try {
    sessions.value = await interviewApi.list()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
})

async function toggle(id: string) {
  if (expanded.value === id) {
    expanded.value = null
    detail.value = null
    return
  }
  try {
    detail.value = await interviewApi.detail(id)
    expanded.value = id
  } catch (e: any) {
    errorMsg.value = e?.message || '加载详情失败'
  }
}

const splitLines = (s?: string) => (s ? s.split('\n').filter(Boolean) : [])

/** 把 Agent 的复盘报告还原成四段式结构；解析不到内容时回退到后端抽取的文本 */
const parsed = computed(() => parseReviewReport(detail.value?.review?.detail))
const structured = computed(() => hasParsedContent(parsed.value))

/** 环形指标：conic-gradient 按分数切弧 */
function ringStyle(score: number) {
  const v = Math.max(0, Math.min(100, Math.round(score || 0)))
  return { background: `conic-gradient(#2563eb 0% ${v}%, #e2e8f0 ${v}% 100%)` }
}
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <h1 class="mb-6 text-xl font-semibold text-ink">面试复盘</h1>
    <p v-if="errorMsg" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>
    <AiDisclaimer dense />

    <div v-if="sessions.length" class="relative pl-8">
      <!-- 时间轴竖线 -->
      <div class="absolute left-3 top-2 bottom-2 w-px bg-slate-200" />

      <div v-for="s in sessions" :key="s.id" class="relative mb-5">
        <div class="absolute -left-5 top-4 h-3 w-3 rounded-full bg-primary ring-4 ring-primary/15" />

        <div class="card card-hover p-5">
          <div class="flex items-start justify-between">
            <div>
              <p class="font-medium text-ink">{{ s.mode || '面试' }}</p>
              <p class="mt-1 text-xs text-muted">{{ s.createdAt }}</p>
            </div>
            <button class="flex items-center gap-1 rounded-lg px-2 py-1 text-sm text-primary transition-colors hover:bg-primary/5" @click="toggle(s.id)">
              {{ expanded === s.id ? '收起' : '查看详情' }}
              <ChevronUp v-if="expanded === s.id" class="h-3.5 w-3.5" />
              <ChevronDown v-else class="h-3.5 w-3.5" />
            </button>
          </div>

          <div v-if="expanded === s.id && detail" class="mt-4 space-y-4 border-t border-slate-100 pt-4">
            <!-- 结构化复盘（Agent 的 interview_review 产出）：评分 / 追问链 / 薄弱点 / 改进动作 -->
            <template v-if="structured">
              <div class="animate-fade-in-up rounded-xl border border-slate-200 p-4">
                <p class="mb-3 flex items-center gap-1.5 text-xs font-medium text-ink">
                  <TrendingUp class="h-3.5 w-3.5 text-primary" />表现评分
                </p>
                <div class="flex flex-wrap items-center gap-5">
                  <div
                    v-if="parsed.overall !== null"
                    class="flex h-20 w-20 items-center justify-center rounded-full"
                    :style="ringStyle(parsed.overall)"
                  >
                    <div class="flex h-[62px] w-[62px] flex-col items-center justify-center rounded-full bg-white">
                      <span class="text-lg font-semibold leading-none text-ink">{{ parsed.overall }}</span>
                      <span class="mt-0.5 text-[10px] text-muted">总分</span>
                    </div>
                  </div>
                  <div
                    v-for="d in parsed.dimensions.slice(0, 3)"
                    :key="d.label"
                    class="flex flex-col items-center"
                  >
                    <div
                      class="flex h-16 w-16 items-center justify-center rounded-full"
                      :style="ringStyle(d.score)"
                    >
                      <div class="flex h-[50px] w-[50px] items-center justify-center rounded-full bg-white">
                        <span class="text-sm font-semibold text-ink">{{ d.score }}</span>
                      </div>
                    </div>
                    <span class="mt-1 max-w-[80px] truncate text-[11px] text-muted">{{ d.label }}</span>
                  </div>
                </div>
              </div>

              <div
                v-if="parsed.questionChain.length"
                class="animate-fade-in-up rounded-xl border border-slate-200 bg-slate-50/60 p-4"
              >
                <p class="mb-2 flex items-center gap-1.5 text-xs font-medium text-ink">
                  <ListOrdered class="h-3.5 w-3.5 text-primary" />追问链还原
                </p>
                <div class="ml-1 border-l-2 border-primary/40 pl-3">
                  <div v-for="(q, qi) in parsed.questionChain" :key="qi" class="flex gap-2 py-1">
                    <span class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary/70" />
                    <p class="text-sm leading-relaxed text-slate-600">
                      <span class="mr-1.5 text-xs text-muted">Q{{ qi + 1 }}</span>{{ q }}
                    </p>
                  </div>
                </div>
              </div>

              <div
                v-if="parsed.weaknesses.length"
                class="animate-fade-in-up rounded-xl border border-l-[3px] border-slate-200 border-l-red-500 bg-red-50/60 p-4"
              >
                <p class="mb-2 text-xs font-medium text-danger">薄弱点</p>
                <p
                  v-for="(l, i) in parsed.weaknesses"
                  :key="i"
                  class="text-sm leading-relaxed text-slate-600"
                >
                  • {{ l }}
                </p>
              </div>

              <div
                v-if="parsed.actions.length"
                class="animate-fade-in-up rounded-xl border border-l-[3px] border-slate-200 border-l-emerald-500 bg-emerald-50/60 p-4"
              >
                <p class="mb-2 text-xs font-medium text-success">改进动作</p>
                <p
                  v-for="(l, i) in parsed.actions"
                  :key="i"
                  class="flex items-start gap-1.5 text-sm leading-relaxed text-slate-600"
                >
                  <CheckSquare class="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />{{ l }}
                </p>
              </div>
            </template>

            <!-- 回退：老数据没有小标题，按后端抽取的「建议 / 弱点」文本展示 -->
            <template v-else>
              <div v-if="splitLines(detail.review?.suggestions).length" class="rounded-xl bg-success/5 p-4">
                <p class="mb-2 text-xs font-medium text-success">建议</p>
                <ul class="space-y-1 text-sm text-ink">
                  <li v-for="(l, i) in splitLines(detail.review.suggestions)" :key="i">• {{ l }}</li>
                </ul>
              </div>
              <div v-if="splitLines(detail.review?.weaknesses).length" class="rounded-xl bg-danger/5 p-4">
                <p class="mb-2 text-xs font-medium text-danger">弱点</p>
                <ul class="space-y-1 text-sm text-ink">
                  <li v-for="(l, i) in splitLines(detail.review.weaknesses)" :key="i">• {{ l }}</li>
                </ul>
              </div>
            </template>

            <div class="max-h-72 space-y-3 overflow-y-auto rounded-xl bg-bgSoft p-4">
              <div v-for="(m, i) in detail.messages || []" :key="i" class="text-sm">
                <p class="mb-1 text-xs text-muted">{{ m.role === 'user' ? '候选人' : '面试官' }}</p>
                <p class="whitespace-pre-wrap leading-relaxed text-ink">{{ m.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="card flex flex-col items-center py-16">
      <MessageSquare class="mb-3 h-8 w-8 text-slate-300" />
      <p class="text-sm text-muted">还没有面试记录。在 AI 助手里完成一次模拟面试后，点「归档到复盘」就会出现在这里。</p>
    </div>
  </div>
</template>
