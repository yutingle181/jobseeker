<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { interviewApi, positionApi, resumeApi, type Position } from '@/api'
import { Briefcase, FileText, MessageSquare, ArrowRight } from 'lucide-vue-next'

const router = useRouter()
const positions = ref<Position[]>([])
const resumeCount = ref(0)
const interviewCount = ref(0)
const current = ref<Position | null>(null)

onMounted(async () => {
  try {
    positions.value = await positionApi.list()
    current.value = positions.value[0] ?? null
  } catch (e) {
    console.error('加载岗位失败', e)
  }
  try {
    resumeCount.value = (await resumeApi.list()).length
  } catch (e) {
    console.error('加载简历失败', e)
  }
  try {
    interviewCount.value = (await interviewApi.list()).length
  } catch (e) {
    console.error('加载面试记录失败', e)
  }
})

function selectPosition(p: Position) {
  current.value = p
}
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-8">
    <!-- 主行动区 -->
    <section class="card relative overflow-hidden p-8">
      <div class="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-primary/10 blur-2xl" />
      <h1 class="text-2xl font-semibold text-ink">你的专属 AI 面试教练</h1>
      <p class="mt-2 max-w-xl text-sm leading-relaxed text-muted">
        先建好目标岗位，再让 AI 助手陪你模拟面试、整理真题、优化简历；
        面试结束可一键归档，随时回看复盘。
      </p>
      <div class="mt-6 flex flex-wrap gap-3">
        <button
          class="flex items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-medium text-white transition-all hover:bg-primaryDark"
          @click="router.push({ name: 'assistant' })"
        >
          <MessageSquare class="h-4 w-4" />开始 AI 助手
        </button>
        <button
          class="flex items-center gap-2 rounded-xl border border-primary/30 bg-white px-5 py-2.5 text-sm font-medium text-primary transition-colors hover:bg-primary/5"
          @click="router.push({ name: 'position-wizard' })"
        >
          <Briefcase class="h-4 w-4" />去建岗位
        </button>
      </div>
    </section>

    <!-- 真实统计 -->
    <section class="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
      <div class="card card-hover p-5">
        <p class="text-xs text-muted">累计面试归档</p>
        <p class="mt-2 text-2xl font-semibold text-ink">{{ interviewCount }}</p>
      </div>
      <div class="card card-hover p-5">
        <p class="text-xs text-muted">简历数量</p>
        <p class="mt-2 text-2xl font-semibold text-ink">{{ resumeCount }}</p>
      </div>
      <div class="card card-hover p-5">
        <p class="text-xs text-muted">关联岗位</p>
        <p class="mt-2 text-2xl font-semibold text-ink">{{ positions.length }}</p>
      </div>
    </section>

    <!-- 当前岗位 -->
    <section class="card mt-6 p-6">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-base font-semibold text-ink">当前岗位</h2>
        <button
          class="flex items-center gap-1 text-sm text-primary transition-colors hover:text-primaryDark"
          @click="router.push({ name: 'positions' })"
        >
          切换岗位<ArrowRight class="h-3.5 w-3.5" />
        </button>
      </div>

      <div v-if="current" class="rounded-xl border border-slate-100 bg-bgSoft p-4">
        <p class="font-medium text-ink">{{ current.title }}</p>
        <p class="mt-1 text-sm text-muted">
          {{ current.companyName || '未填写公司' }}{{ current.city ? ' · ' + current.city : '' }}
        </p>
      </div>
      <p v-else class="rounded-xl border border-dashed border-slate-200 p-6 text-center text-sm text-muted">
        还没有岗位，先去新建一个目标岗位吧。
      </p>

      <div v-if="positions.length > 1" class="mt-4 flex flex-wrap gap-2">
        <button
          v-for="p in positions"
          :key="p.id"
          class="rounded-lg px-3 py-1.5 text-sm transition-colors"
          :class="current?.id === p.id ? 'bg-primary text-white' : 'bg-slate-100 text-muted hover:bg-slate-200'"
          @click="selectPosition(p)"
        >
          {{ p.title }}
        </button>
      </div>
    </section>

    <!-- 快捷入口 -->
    <section class="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
      <button class="card card-hover flex items-center gap-3 p-5 text-left" @click="router.push({ name: 'resumes' })">
        <FileText class="h-5 w-5 text-primary" />
        <div>
          <p class="text-sm font-medium text-ink">简历管理</p>
          <p class="text-xs text-muted">上传、优化与版本管理</p>
        </div>
      </button>
      <button class="card card-hover flex items-center gap-3 p-5 text-left" @click="router.push({ name: 'reviews' })">
        <MessageSquare class="h-5 w-5 text-primary" />
        <div>
          <p class="text-sm font-medium text-ink">面试复盘</p>
          <p class="text-xs text-muted">时间轴回看建议与弱点</p>
        </div>
      </button>
      <button class="card card-hover flex items-center gap-3 p-5 text-left" @click="router.push({ name: 'jobs' })">
        <Briefcase class="h-5 w-5 text-primary" />
        <div>
          <p class="text-sm font-medium text-ink">找职位</p>
          <p class="text-xs text-muted">直达招聘网站</p>
        </div>
      </button>
    </section>
  </div>
</template>
