<script setup lang="ts">
import { computed } from 'vue'
import { AlertCircle, CheckCircle2, Target } from 'lucide-vue-next'

/**
 * JD 匹配评分卡：总分环形 + 分项进度条 + 命中/缺口双栏 + 面试准备重点。
 *
 * 数据来自 SSE `done` 事件里的 `structured` 字段（Agent 侧结构化产出），
 * 不走文本解析——所以数值与 Agent 结论严格一致，不会出现「卡片和文字两张皮」。
 */
interface JdResult {
  total_score?: number
  dimension_scores?: Record<string, number>
  matched?: string[]
  gaps?: string[]
  interview_focus?: string[]
  summary?: string
}

const props = defineProps<{ result: JdResult }>()

/** 维度键 -> 中文名；Agent 偶尔返回本地化键名，这里兜住 */
const DIM_LABELS: Record<string, string> = {
  skills: '技能匹配',
  skill: '技能匹配',
  技能: '技能匹配',
  技能匹配: '技能匹配',
  experience: '经验匹配',
  经验: '经验匹配',
  经验匹配: '经验匹配',
  education: '学历匹配',
  学历: '学历匹配',
  学历匹配: '学历匹配',
  projects: '项目匹配',
  project: '项目匹配',
  项目: '项目匹配',
  项目匹配: '项目匹配',
}

function clamp(value?: number) {
  return Math.max(0, Math.min(100, Math.round(value ?? 0)))
}

const total = computed(() => clamp(props.result.total_score))

/** 环形进度：conic-gradient 按分数切分主色弧 */
const ringStyle = computed(() => ({
  background: `conic-gradient(#2563eb 0% ${total.value}%, #e2e8f0 ${total.value}% 100%)`,
}))

const dims = computed(() =>
  Object.entries(props.result.dimension_scores ?? {}).map(([key, score]) => ({
    label: DIM_LABELS[key] ?? key,
    score: clamp(score),
  })),
)

const matched = computed(() => props.result.matched ?? [])
const gaps = computed(() => props.result.gaps ?? [])
const focus = computed(() => props.result.interview_focus ?? [])
</script>

<template>
  <div
    class="card card-hover animate-fade-in-up border border-slate-200 p-4"
    aria-label="JD 匹配评分卡"
  >
    <div class="flex flex-wrap items-center gap-5">
      <!-- 总分环形 -->
      <div
        class="flex h-24 w-24 shrink-0 items-center justify-center rounded-full"
        :style="ringStyle"
      >
        <div class="flex h-[76px] w-[76px] flex-col items-center justify-center rounded-full bg-white">
          <span class="text-2xl font-semibold leading-none text-ink">{{ total }}</span>
          <span class="mt-1 text-[10px] text-muted">总分 / 100</span>
        </div>
      </div>

      <!-- 分项进度条 -->
      <div class="min-w-[220px] flex-1">
        <p v-if="result.summary" class="mb-2 text-xs leading-relaxed text-muted">
          {{ result.summary }}
        </p>
        <p v-if="!dims.length" class="text-xs text-muted">暂无分项得分</p>
        <div v-for="d in dims" :key="d.label" class="mb-2 last:mb-0">
          <div class="flex items-center justify-between text-xs text-slate-600">
            <span>{{ d.label }}</span>
            <span class="font-medium text-ink">{{ d.score }}</span>
          </div>
          <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div
              class="h-full rounded-full bg-gradient-to-r from-primary to-sky-400 transition-all duration-500"
              :style="{ width: `${d.score}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 命中 / 缺口双栏：窄屏自动堆叠 -->
    <div class="mt-3 flex flex-wrap gap-3">
      <div class="min-w-[200px] flex-1 rounded-xl border border-l-[3px] border-slate-200 border-l-emerald-500 bg-slate-50/60 p-3">
        <p class="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-ink">
          <CheckCircle2 class="h-3.5 w-3.5 text-emerald-600" />
          命中项
        </p>
        <p v-if="!matched.length" class="text-xs text-muted">暂无命中项</p>
        <p v-for="m in matched" :key="m" class="text-xs leading-relaxed text-slate-600">
          • {{ m }}
        </p>
      </div>

      <div class="min-w-[200px] flex-1 rounded-xl border border-l-[3px] border-slate-200 border-l-amber-500 bg-slate-50/60 p-3">
        <p class="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-ink">
          <AlertCircle class="h-3.5 w-3.5 text-amber-600" />
          缺口项
        </p>
        <p v-if="!gaps.length" class="text-xs text-muted">暂无明显缺口</p>
        <p v-for="g in gaps" :key="g" class="text-xs leading-relaxed text-slate-600">
          • {{ g }}
        </p>
      </div>
    </div>

    <!-- 面试准备重点 -->
    <div v-if="focus.length" class="mt-3 rounded-xl border border-primary/25 bg-primary/5 p-3">
      <p class="mb-1 flex items-center gap-1.5 text-xs font-medium text-ink">
        <Target class="h-3.5 w-3.5 text-primary" />
        面试准备重点
      </p>
      <p v-for="f in focus" :key="f" class="text-xs leading-relaxed text-slate-600">• {{ f }}</p>
    </div>
  </div>
</template>
