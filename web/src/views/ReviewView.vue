<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { interviewApi } from '@/api'
import { MessageSquare, ChevronDown, ChevronUp } from 'lucide-vue-next'
import AiDisclaimer from '@/components/AiDisclaimer.vue'

interface Session {
  id: number
  mode: string
  createdAt: string
  positionId: number
}

const sessions = ref<Session[]>([])
const expanded = ref<number | null>(null)
const detail = ref<any>(null)
const errorMsg = ref('')

onMounted(async () => {
  try {
    sessions.value = await interviewApi.list()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
})

async function toggle(id: number) {
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
