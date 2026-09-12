<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { positionApi, type Position } from '@/api'
import { Plus, Trash2, Edit2 } from 'lucide-vue-next'

const router = useRouter()
const list = ref<Position[]>([])
const current = ref<Position | null>(null)
const keyword = ref('')
const errorMsg = ref('')

async function load() {
  try {
    list.value = await positionApi.list()
    if (!current.value && list.value.length) current.value = list.value[0]
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
}

onMounted(load)

const filtered = () => {
  if (!keyword.value.trim()) return list.value
  const k = keyword.value.trim().toLowerCase()
  return list.value.filter(
    (p) => p.title.toLowerCase().includes(k) || (p.companyName || '').toLowerCase().includes(k),
  )
}

async function remove(id: string) {
  try {
    await positionApi.remove(id)
    if (current.value?.id === id) current.value = null
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '删除失败'
  }
}
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-xl font-semibold text-ink">岗位管理</h1>
      <button class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark" @click="router.push({ name: 'position-wizard' })">
        <Plus class="h-4 w-4" />新建岗位
      </button>
    </div>

    <p v-if="errorMsg" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>

    <div class="grid grid-cols-1 gap-6 md:grid-cols-[280px_1fr]">
      <!-- 左侧列表 -->
      <div class="card p-3">
        <input v-model="keyword" class="mb-3 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="搜索岗位" />
        <div v-if="filtered().length" class="space-y-1">
          <button
            v-for="p in filtered()"
            :key="p.id"
            class="flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-left text-sm transition-colors"
            :class="current?.id === p.id ? 'bg-primary/10 text-primary' : 'text-ink hover:bg-slate-50'"
            @click="current = p"
          >
            <span class="truncate">{{ p.title }}</span>
          </button>
        </div>
        <p v-else class="px-3 py-8 text-center text-sm text-muted">暂无岗位</p>
      </div>

      <!-- 右侧详情 -->
      <div class="card p-6">
        <template v-if="current">
          <div class="flex items-start justify-between">
            <div>
              <h2 class="text-lg font-semibold text-ink">{{ current.title }}</h2>
              <p class="mt-1 text-sm text-muted">
                {{ current.category }} · {{ current.companyName || '未填写公司' }}{{ current.city ? ' · ' + current.city : '' }}
              </p>
            </div>
            <div class="flex gap-2">
              <button class="rounded-lg p-2 text-muted transition-colors hover:bg-slate-100 hover:text-primary" title="编辑">
                <Edit2 class="h-4 w-4" @click="router.push({ name: 'position-wizard', params: { id: current.id } })" />
              </button>
              <button class="rounded-lg p-2 text-muted transition-colors hover:bg-slate-100 hover:text-danger" title="删除" @click="remove(current.id)">
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="mt-5 space-y-4">
            <div>
              <p class="mb-1 text-xs text-muted">岗位要求 / JD</p>
              <div class="whitespace-pre-wrap rounded-xl bg-bgSoft p-4 text-sm leading-relaxed text-ink">
                {{ current.description || '未填写' }}
              </div>
            </div>
            <div>
              <p class="mb-1 text-xs text-muted">公司简介</p>
              <div class="whitespace-pre-wrap rounded-xl bg-bgSoft p-4 text-sm leading-relaxed text-ink">
                {{ current.companyIntro || '未填写' }}
              </div>
            </div>
          </div>

          <div class="mt-6 flex gap-3">
            <button class="rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark" @click="router.push({ name: 'assistant' })">
              用这个岗位开始模拟面试
            </button>
            <button class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-slate-50" @click="router.push({ name: 'jobs' })">
              去找同岗位职位
            </button>
          </div>
        </template>
        <p v-else class="py-16 text-center text-sm text-muted">选择左侧岗位查看详情，或新建一个岗位。</p>
      </div>
    </div>
  </div>
</template>
