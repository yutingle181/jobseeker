<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronRight,
  Loader2,
  Wrench,
} from 'lucide-vue-next'
import type { ToolCallEvent } from '../api'

/**
 * 工具调用时间线：把 Agent 的 Function Calling 过程渲染成「可追溯的一行一步」。
 *
 * 默认折叠成一行摘要（调用了几个工具、一共多久），点击展开看每一步。
 * 折叠状态只存在组件本地（ref），不写回后端，避免污染会话数据。
 */
const props = withDefaults(
  defineProps<{
    events: ToolCallEvent[]
    /** 流式进行中：头部显示转圈，表示过程仍在推进 */
    running?: boolean
  }>(),
  { running: false },
)

const open = ref(false)

const totalMs = computed(() =>
  props.events.reduce((sum, e) => sum + (e.elapsed_ms ?? 0), 0),
)

const summary = computed(() => {
  if (!props.events.length) return props.running ? '正在判断是否需要工具…' : '未调用工具，直接作答'
  return `调用了 ${props.events.length} 个工具 · ${formatMs(totalMs.value)}`
})

const failed = computed(() => props.events.some((e) => e.ok === false))

function formatMs(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}

/** 摘要统一压成一行并截断，避免长返回把时间线撑爆 */
function short(text?: string, max = 90) {
  const t = (text ?? '').replace(/\s+/g, ' ').trim()
  if (!t) return '—'
  return t.length > max ? `${t.slice(0, max)}…` : t
}

function dotClass(e: ToolCallEvent) {
  if (e.ok === false) return 'bg-amber-500'
  return 'bg-emerald-500'
}
</script>

<template>
  <div
    class="animate-fade-in-up overflow-hidden rounded-xl border border-slate-200 bg-slate-50/70 transition-all duration-200"
  >
    <button
      type="button"
      class="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-slate-100/80 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
      :aria-expanded="open"
      @click="open = !open"
    >
      <component :is="open ? ChevronDown : ChevronRight" class="h-3.5 w-3.5 shrink-0 text-muted" />
      <Wrench class="h-3.5 w-3.5 shrink-0 text-primary" />
      <span class="text-xs font-medium text-ink">工具调用</span>
      <span class="truncate text-xs text-muted">{{ summary }}</span>
      <AlertTriangle v-if="failed" class="ml-auto h-3.5 w-3.5 shrink-0 text-amber-500" />
      <Loader2
        v-else-if="running"
        class="ml-auto h-3.5 w-3.5 shrink-0 animate-spin text-primary"
      />
    </button>

    <div v-show="open" class="border-t border-slate-200/80 px-3 pb-2 pt-1">
      <p v-if="!events.length" class="py-1 text-xs text-muted">{{ summary }}</p>
      <div v-else class="ml-1 border-l-2 border-primary/50 pl-3">
        <div v-for="(e, i) in events" :key="e.index ?? i" class="flex gap-2 py-1.5">
          <span
            class="mt-1.5 h-2 w-2 shrink-0 rounded-full"
            :class="dotClass(e)"
            aria-hidden="true"
          />
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-x-2">
              <span class="font-mono text-xs text-ink">{{ e.name }}</span>
              <span class="text-[11px] text-muted">
                #{{ (e.index ?? i) + 1 }} · {{ formatMs(e.elapsed_ms ?? 0) }}
              </span>
              <Check v-if="e.ok !== false" class="h-3 w-3 text-emerald-600" />
              <AlertTriangle v-else class="h-3 w-3 text-amber-500" />
            </div>
            <p class="mt-0.5 text-[11px] text-muted">入参：{{ short(e.args_summary) }}</p>
            <p class="text-[11px] text-slate-600">返回：{{ short(e.result_summary) }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
