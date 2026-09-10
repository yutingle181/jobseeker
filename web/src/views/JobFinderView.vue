<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { jobSiteApi, jobSearchApi, positionApi, type Position } from '@/api'
import { ExternalLink, AlertTriangle, Sparkles, Trash2 } from 'lucide-vue-next'

const router = useRouter()
const sites = ref<{ id: number; name: string; icon: string; url: string }[]>([])
const positions = ref<Position[]>([])
const keyword = ref('')
const city = ref('')
const records = ref<{ id: number; keyword: string; result: string; source: string; disclaimer: string }[]>([])
const errorMsg = ref('')
const loading = ref(false)
const loadError = ref(false)

onMounted(async () => {
  try {
    positions.value = await positionApi.list()
    if (positions.value.length) {
      keyword.value = positions.value[0].title
      city.value = positions.value[0].city || ''
    }
    await reload()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
})

async function reload() {
  loading.value = true
  loadError.value = false
  try {
    sites.value = await jobSiteApi.list(keyword.value, city.value)
  } catch (e: any) {
    loadError.value = true
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  records.value = await jobSearchApi.list()
}

onMounted(loadRecords)

async function removeRecord(id: number) {
  try {
    await jobSearchApi.remove(id)
    await loadRecords()
  } catch (e: any) {
    errorMsg.value = e?.message || '删除失败'
  }
}

function openSite(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

const currentPositionTitle = computed(() => keyword.value || '未选择岗位')
</script>

<template>
  <div class="mx-auto max-w-5xl px-6 py-8">
    <h1 class="mb-6 text-xl font-semibold text-ink">找职位</h1>
    <p v-if="errorMsg && !loadError" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>

    <!-- A：真实外链入口 -->
    <section class="card p-6">
      <h2 class="text-base font-semibold text-ink">前往招聘网站，寻找职位</h2>
      <p class="mt-1 text-sm text-muted">以下为真实招聘站点，职位数据以站点为准。</p>

      <div class="mt-4 flex flex-wrap items-end gap-3">
        <div>
          <label class="mb-1 block text-xs text-muted">岗位关键词</label>
          <input v-model="keyword" class="w-56 rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" @change="reload" />
        </div>
        <div>
          <label class="mb-1 block text-xs text-muted">城市</label>
          <input v-model="city" class="w-40 rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" @change="reload" />
        </div>
      </div>

      <div class="mt-5 grid grid-cols-2 gap-4 md:grid-cols-4">
        <button
          v-for="s in sites"
          :key="s.id"
          class="card card-hover flex flex-col items-center gap-2 p-5"
          @click="openSite(s.url)"
        >
          <span class="text-sm font-medium text-ink">{{ s.name }}</span>
          <span class="flex items-center gap-1 text-xs text-primary">
            前往搜索<ExternalLink class="h-3 w-3" />
          </span>
        </button>
      </div>
      <p v-if="loading" class="mt-4 text-sm text-muted">加载中…</p>
      <p v-else-if="loadError" class="mt-4 flex items-center gap-2 text-sm text-danger">
        加载失败（{{ errorMsg }}），<button class="underline text-primary" @click="reload">点此重试</button>
      </p>
      <p v-else-if="!sites.length" class="mt-4 text-sm text-muted">暂无可用站点（后端 job_site 表未配置）。</p>
    </section>

    <!-- B：AI 参考 -->
    <section class="card mt-6 p-6">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-base font-semibold text-ink">没有头绪？让 AI 给你方向</h2>
          <p class="mt-1 text-sm text-muted">
            让 AI 助手基于「{{ city || '城市' }} + {{ currentPositionTitle }}」生成职位方向参考。
          </p>
        </div>
        <button
          class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark"
          @click="router.push({ name: 'assistant' })"
        >
          <Sparkles class="h-4 w-4" />用 AI 助手生成职位方向
        </button>
      </div>

      <!-- AI 清单：强制免责标注、不渲染链接 -->
      <div v-if="records.length" class="mt-6">
        <div class="mb-3 flex items-start gap-2 rounded-xl bg-warn/10 p-3 text-sm text-warn">
          <AlertTriangle class="mt-0.5 h-4 w-4 shrink-0" />
          <span>以下为 AI 生成的参考方向，非实时在招职位，请勿当作真实职位投递。</span>
        </div>

        <div v-for="r in records" :key="r.id" class="mb-3 rounded-xl border border-slate-100 p-4">
          <div class="mb-2 flex items-center justify-between">
            <p class="text-sm font-medium text-ink">{{ r.keyword || '未命名检索' }}</p>
            <button class="rounded-lg p-1.5 text-muted transition-colors hover:bg-slate-100 hover:text-danger" @click="removeRecord(r.id)">
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
          <!-- 只展示文本，绝不把内容渲染成可点击链接 -->
          <div class="max-h-60 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed text-ink">{{ r.result }}</div>
        </div>
      </div>
    </section>
  </div>
</template>
